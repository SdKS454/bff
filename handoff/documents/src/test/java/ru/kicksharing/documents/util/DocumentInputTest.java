package ru.kicksharing.documents.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DocumentInputTest {
    @ParameterizedTest @ValueSource(strings={"[]", "null", "{} {}", "{\"docType\":1}",
        "{\"from\":[2026,10,1]}", "{\"from\":\"2026-02-30\"}", "{\"from\":\"2026-1-1\"}",
        "{\"userId\":\"1-1-1-1-1\"}", "{\"docType\":\"USER_HISTORY\",\"docType\":\"USER_BLOCKED\"}"})
    void rejectsNonContractInput(String json) { assertThrows(IllegalArgumentException.class, () -> DocumentInput.parse(json)); }
    @Test void acceptsBffPayloadIncludingNullOptionals() {
        var request=DocumentInput.parse("{\"userId\":\"550e8400-e29b-41d4-a716-446655440000\",\"docType\":\"USER_HISTORY\",\"sourceId\":null,\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}");
        assertEquals("2026-10-01",request.getFrom().toString());assertNull(request.getSourceId());
    }
}
