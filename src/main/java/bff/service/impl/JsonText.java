package bff.service.impl;

import jakarta.json.*;
import jakarta.json.stream.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;




public final class JsonText {
    private JsonText() {
    }

    public static String read(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(65537);
        if (bytes.length > 65536) throw new IllegalArgumentException("Тело запроса слишком большое");
        String json = new String(bytes, StandardCharsets.UTF_8);
        object(json);
        return json;
    }

    public static JsonObject object(String json) {
        if (json == null) throw new IllegalArgumentException("Ожидается JSON-объект");
        try (JsonParser parser = Json.createParser(new StringReader(json))) {
            if (!parser.hasNext() || parser.next() != JsonParser.Event.START_OBJECT)
                throw new IllegalArgumentException("Ожидается JSON-объект");
            JsonObject value = parser.getObject();
            if (parser.hasNext()) throw new IllegalArgumentException("Лишние данные после JSON");
            return value;
        } catch (JsonException e) {
            throw new IllegalArgumentException("Некорректный JSON", e);
        }
    }

    public static String string(String json, String field) {
        JsonValue value = object(json).get(field);
        if (value == null || value == JsonValue.NULL) return null;
        if (!(value instanceof JsonString text))
            throw new IllegalArgumentException("Поле " + field + " должно быть строкой");
        return text.getString();
    }

    public static String requiredString(String json, String field) {
        String s = string(json, field);
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Укажите " + field);
        return s;
    }

    public static String raw(String json, String field) {
        JsonValue value = object(json).get(field);
        return value == null ? null : value.toString();
    }

    public static String coordinate(String json, String field, boolean required) {
        JsonValue value = object(json).get(field);
        if (value == null || value == JsonValue.NULL) {
            if (required) throw new IllegalArgumentException("Нужны posX и posY");
            return null;
        }
        if (!(value instanceof JsonNumber number) || !Double.isFinite(number.doubleValue()) || Math.abs(number.doubleValue()) > 1_000_000)
            throw new IllegalArgumentException("Поле " + field + " должно быть числом в диапазоне от -1000000 до 1000000");
        return number.bigDecimalValue().toString();
    }

    public static Long longOrNull(String json, String field) {
        JsonValue value = object(json).get(field);
        if (value == null || value == JsonValue.NULL) return null;
        if (!(value instanceof JsonNumber number))
            throw new IllegalArgumentException("Ожидается целое число: " + field);
        return number.longValueExact();
    }

    public static UUID uuidOrNull(String json, String field) {
        String value = string(json, field);
        return value == null ? null : uuid(value);
    }

    public static UUID uuid(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Ожидается UUID");
        return UUID.fromString(value);
    }

    public static boolean bool(String json, String field) {
        JsonValue value = object(json).get(field);
        if (value == null || value == JsonValue.NULL) return false;
        if (value != JsonValue.TRUE && value != JsonValue.FALSE)
            throw new IllegalArgumentException("Ожидается boolean: " + field);
        return value == JsonValue.TRUE;
    }

    public static String quote(Object value) {
        return value == null ? "null" : Json.createValue(value.toString()).toString();
    }
}
