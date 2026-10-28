package bff;

import bff.service.impl.JsonText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JsonTextTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "[]", "null", "{", "{\"x\":1,}", "{} garbage", "{} {}", "{\"x\":NaN}"})
    void rejectsMalformedOrNonObjectJson(String json) {
        assertThrows(IllegalArgumentException.class, () -> JsonText.object(json));
    }

    @Test
    void uuidDoesNotBecomeFloatingPointOrAcceptShortForm() {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        assertEquals(id, JsonText.uuidOrNull("{\"id\":\"" + id + "\"}", "id"));
        assertThrows(IllegalArgumentException.class, () -> JsonText.uuidOrNull("{\"id\":12}", "id"));
        assertThrows(IllegalArgumentException.class, () -> JsonText.uuid("1-1-1-1-1"));
        assertEquals("\"" + id + "\"", JsonText.quote(id));
    }

    @Test
    void coordinatesRemainNumericWithoutLosingPrecision() {
        assertEquals("37.1234567890123456789", JsonText.coordinate("{\"x\":37.1234567890123456789}", "x", true));
        assertThrows(IllegalArgumentException.class, () -> JsonText.coordinate("{\"x\":\"12\"}", "x", true));
        assertThrows(IllegalArgumentException.class, () -> JsonText.coordinate("{\"x\":1e9999}", "x", true));
        assertThrows(IllegalArgumentException.class, () -> JsonText.coordinate("{\"x\":null}", "x", true));
        assertThrows(IllegalArgumentException.class, () -> JsonText.coordinate("{\"x\":1000001}", "x", true));
        assertNull(JsonText.coordinate("{}", "x", false));
    }

    @Test
    void handlesEscapesAndNestedKeysWithoutConfusion() {
        String text = "Ключ \"\\\n";
        String json = "{\"nested\":{\"id\":\"wrong\"},\"id\":" + JsonText.quote(text) + "}";
        assertEquals(text, JsonText.string(json, "id"));
        assertThrows(IllegalArgumentException.class, () -> JsonText.string("{\"id\":{}}", "id"));
        assertThrows(IllegalArgumentException.class, () -> JsonText.bool("{\"blocked\":\"false\"}", "blocked"));
    }

    @Test
    void enforcesBodyLimit() {
        byte[] bytes = ("{\"x\":\"" + "a".repeat(65536) + "\"}").getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> JsonText.read(new ByteArrayInputStream(bytes)));
    }
}
