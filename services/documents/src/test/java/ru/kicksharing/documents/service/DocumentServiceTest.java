package ru.kicksharing.documents.service;

import org.junit.jupiter.api.*;
import ru.kicksharing.documents.client.*;
import ru.kicksharing.documents.repository.DocumentRepository;
import ru.kicksharing.documents.dto.request.GenerateDocumentRequest;
import ru.kicksharing.documents.dto.external.*;
import ru.kicksharing.documents.exception.ApiException;
import ru.kicksharing.documents.util.JwtUtilTest;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    DocumentRepository repository; UserClient user; RideClient ride; ModerationClient mod; PdfService pdf; DocumentService service;
    GenerateDocumentRequest request;
    @BeforeEach void setUp() {
        repository=mock(DocumentRepository.class);user=mock(UserClient.class);ride=mock(RideClient.class);
        mod=mock(ModerationClient.class);pdf=mock(PdfService.class);
        service=new DocumentService(repository,user,ride,mod,pdf);
        request=new GenerateDocumentRequest();request.setUserId(JwtUtilTest.USER);request.setDocType("USER_BLOCKED");
    }
    @Test void foreignIdentityNeverWritesMetadata() {
        request.setUserId(UUID.randomUUID());
        assertEquals(403,assertThrows(ApiException.class,()->service.generate(request,JwtUtilTest.authorization())).getStatusCode());
        verifyNoInteractions(repository,user,ride,mod,pdf);
    }
    @Test void revokedTokenNeverWritesMetadata() {
        doThrow(new ApiException(401,"revoked")).when(user).validate(anyString());
        assertEquals(401,assertThrows(ApiException.class,()->service.generate(request,JwtUtilTest.authorization())).getStatusCode());
        verifyNoInteractions(repository,ride,mod,pdf);
    }
    @Test void metadataFailureCannotHideOriginalDownstreamError() {
        when(mod.getStatus(anyString(),anyString())).thenThrow(new ApiException(503,"moderation unavailable"));
        doThrow(new RuntimeException("DB lost")).when(repository).updateStatus(any(),eq("FAILED"),isNull());
        var failure=assertThrows(ApiException.class,()->service.generate(request,JwtUtilTest.authorization()));
        assertEquals(503,failure.getStatusCode());assertEquals(1,failure.getSuppressed().length);
    }
    @Test void unblockedNoticeIsConflictAndFailedMetadata() {
        when(mod.getStatus(anyString(),anyString())).thenReturn(new ModerationResponse());
        assertEquals(409,assertThrows(ApiException.class,()->service.generate(request,JwtUtilTest.authorization())).getStatusCode());
        verify(repository).updateStatus(any(),eq("FAILED"),isNull());verifyNoInteractions(pdf);
    }
    @Test void foreignReceiptDeniedBeforePaymentStateIsDisclosed() {
        request.setDocType("RIDE_PAYMENT");request.setSourceId(UUID.randomUUID());
        var result=new RideResponse();result.setUserId(UUID.randomUUID().toString());result.setStatus("FINISHED");
        when(ride.getRide(anyString(),anyString())).thenReturn(result);
        assertEquals(403,assertThrows(ApiException.class,()->service.generate(request,JwtUtilTest.authorization())).getStatusCode());
        verifyNoInteractions(pdf);
    }
    @Test void unpaidReceiptIsConflict() {
        request.setDocType("RIDE_PAYMENT");request.setSourceId(UUID.randomUUID());
        var result=new RideResponse();result.setUserId(JwtUtilTest.USER.toString());result.setStatus("FINISHED");
        when(ride.getRide(anyString(),anyString())).thenReturn(result);
        assertEquals(409,assertThrows(ApiException.class,()->service.generate(request,JwtUtilTest.authorization())).getStatusCode());
    }
}
