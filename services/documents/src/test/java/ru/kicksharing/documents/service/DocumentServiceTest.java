package ru.kicksharing.documents.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.kicksharing.documents.client.ModerationClient;
import ru.kicksharing.documents.client.RideClient;
import ru.kicksharing.documents.client.UserClient;
import ru.kicksharing.documents.dto.external.ModerationResponse;
import ru.kicksharing.documents.dto.external.RideResponse;
import ru.kicksharing.documents.dto.request.GenerateDocumentRequest;
import ru.kicksharing.documents.exception.ApiException;
import ru.kicksharing.documents.repository.DocumentRepository;
import ru.kicksharing.documents.util.JwtUtilTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    DocumentRepository repository;
    UserClient user;
    RideClient ride;
    ModerationClient mod;
    PdfService pdf;
    DocumentService service;
    GenerateDocumentRequest request;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        user = mock(UserClient.class);
        ride = mock(RideClient.class);
        mod = mock(ModerationClient.class);
        pdf = mock(PdfService.class);
        service = new DocumentService(repository, user, ride, mod, pdf);
        request = new GenerateDocumentRequest();
        request.setUserId(JwtUtilTest.USER);
        request.setDocType("USER_BLOCKED");
    }

    @Test
    void foreignIdentityNeverWritesMetadata() {
        request.setUserId(UUID.randomUUID());
        assertEquals(403, assertThrows(ApiException.class, () -> service.generate(request, JwtUtilTest.authorization())).getStatusCode());
        verifyNoInteractions(repository, user, ride, mod, pdf);
    }

    @Test
    void revokedTokenNeverWritesMetadata() {
        doThrow(new ApiException(401, "revoked")).when(user).validate(anyString());
        assertEquals(401, assertThrows(ApiException.class, () -> service.generate(request, JwtUtilTest.authorization())).getStatusCode());
        verifyNoInteractions(repository, ride, mod, pdf);
    }

    @Test
    void metadataFailureCannotHideOriginalDownstreamError() {
        when(mod.getStatus(anyString(), anyString())).thenThrow(new ApiException(503, "moderation unavailable"));
        doThrow(new RuntimeException("DB lost")).when(repository).updateStatus(any(), eq("FAILED"), isNull());
        var failure = assertThrows(ApiException.class, () -> service.generate(request, JwtUtilTest.authorization()));
        assertEquals(503, failure.getStatusCode());
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void unblockedNoticeIsConflictAndFailedMetadata() {
        when(mod.getStatus(anyString(), anyString())).thenReturn(new ModerationResponse());
        assertEquals(409, assertThrows(ApiException.class, () -> service.generate(request, JwtUtilTest.authorization())).getStatusCode());
        verify(repository).updateStatus(any(), eq("FAILED"), isNull());
        verifyNoInteractions(pdf);
    }

    @Test
    void foreignReceiptDeniedBeforePaymentStateIsDisclosed() {
        request.setDocType("RIDE_PAYMENT");
        request.setSourceId(UUID.randomUUID());
        var result = new RideResponse();
        result.setUserId(UUID.randomUUID().toString());
        result.setStatus("FINISHED");
        when(ride.getRide(anyString(), anyString())).thenReturn(result);
        assertEquals(403, assertThrows(ApiException.class, () -> service.generate(request, JwtUtilTest.authorization())).getStatusCode());
        verifyNoInteractions(pdf);
    }

    @Test
    void unpaidReceiptIsConflict() {
        request.setDocType("RIDE_PAYMENT");
        request.setSourceId(UUID.randomUUID());
        var result = new RideResponse();
        result.setUserId(JwtUtilTest.USER.toString());
        result.setStatus("FINISHED");
        when(ride.getRide(anyString(), anyString())).thenReturn(result);
        assertEquals(409, assertThrows(ApiException.class, () -> service.generate(request, JwtUtilTest.authorization())).getStatusCode());
    }

    @Test
    void metadataAndDownloadRejectForeignDocuments() {
        var d = new ru.kicksharing.documents.entity.Document(UUID.randomUUID(), "USER_HISTORY", null, null, null);
        when(repository.findById(d.getId(), false)).thenReturn(java.util.Optional.of(d));
        assertEquals(403, assertThrows(ApiException.class, () -> service.metadata(JwtUtilTest.authorization(), d.getId())).getStatusCode());
        assertEquals(403, assertThrows(ApiException.class, () -> service.download(JwtUtilTest.authorization(), d.getId())).getStatusCode());
        verifyNoInteractions(pdf);
    }

    @Test
    void deleteIsIdempotentButNeverDeletesOthers() {
        var d = new ru.kicksharing.documents.entity.Document(JwtUtilTest.USER, "USER_BLOCKED", null, null, null);
        d.setDeleted(true);
        when(repository.findById(d.getId(), true)).thenReturn(java.util.Optional.of(d));
        service.delete(JwtUtilTest.authorization(), d.getId());
        verify(repository, never()).softDelete(any(), any());
        d.setUserId(UUID.randomUUID());
        assertEquals(403, assertThrows(ApiException.class, () -> service.delete(JwtUtilTest.authorization(), d.getId())).getStatusCode());
    }

    @Test
    void historyUsesCurrentOwnerAndBoundedPagination() {
        when(repository.findByUserId(JwtUtilTest.USER, 1, 2)).thenReturn(java.util.List.of());
        assertTrue(service.history(JwtUtilTest.authorization(), 1, 2).isEmpty());
        verify(repository).findByUserId(JwtUtilTest.USER, 1, 2);
        assertEquals(400, assertThrows(ApiException.class, () -> service.history(JwtUtilTest.authorization(), 101, 0)).getStatusCode());
    }

    @Test
    void pendingDocumentCannotPretendToHaveDownloadablePdf() {
        var d = new ru.kicksharing.documents.entity.Document(JwtUtilTest.USER, "USER_BLOCKED", null, null, null);
        when(repository.findById(d.getId(), false)).thenReturn(java.util.Optional.of(d));
        assertEquals(409, assertThrows(ApiException.class, () -> service.download(JwtUtilTest.authorization(), d.getId())).getStatusCode());
        verify(repository, never()).save(any());
        verifyNoInteractions(pdf);
    }

    @Test
    void metadataDatesAreIsoStrings() {
        var d = new ru.kicksharing.documents.entity.Document(JwtUtilTest.USER, "USER_HISTORY", null, java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 27));
        String json = ru.kicksharing.documents.util.JsonUtil.toJson(ru.kicksharing.documents.dto.DocumentMetadata.of(d));
        assertTrue(json.contains("2026-10-01"));
        assertTrue(json.contains("\"createdAt\":\""));
    }

    @Test
    void historicalDownloadUsesSnapshotWithoutQueryingCurrentBan() {
        var d = new ru.kicksharing.documents.entity.Document(JwtUtilTest.USER, "USER_BLOCKED", null, null, null);
        d.setStatus("GENERATED");
        var data = new ru.kicksharing.documents.dto.internal.UserBlockedDto();
        data.setUserFullName("Иван Петров");
        data.setReason("Опасное вождение");
        d.setPayloadJson(ru.kicksharing.documents.util.JsonUtil.toJson(data));
        when(repository.findById(d.getId(), false)).thenReturn(java.util.Optional.of(d));
        when(pdf.generatePdf(eq("user-blocked"), any())).thenReturn(new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, service.download(JwtUtilTest.authorization(), d.getId()));
        verifyNoInteractions(mod, ride);
        verify(user, never()).getUser(anyString(), anyString());
        verify(repository, never()).save(any());
    }

}
