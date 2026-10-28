package ru.itis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import ru.itis.exception.ApiException;

public record ReviewRequest(int rideRating, int scooterRating, String rideComment, String scooterComment) {
    public ReviewRequest {
        if (rideRating < 1 || rideRating > 5 || scooterRating < 1 || scooterRating > 5)
            throw new ApiException(400, "Оценки поездки и самоката должны быть целыми числами от 1 до 5");
        rideComment = comment(rideComment);
        scooterComment = comment(scooterComment);
    }

    public static ReviewRequest from(JsonNode body) {
        return new ReviewRequest(rating(body, "rideRating"), rating(body, "scooterRating"),
                text(body, "rideComment"), text(body, "scooterComment"));
    }

    private static int rating(JsonNode body, String key) {
        var value = body.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToInt())
            throw new ApiException(400, "Нужна целая оценка " + key);
        return value.intValue();
    }

    private static String text(JsonNode body, String key) {
        var value = body.get(key);
        if (value == null || value.isNull()) return "";
        if (!value.isTextual()) throw new ApiException(400, "Комментарий должен быть строкой");
        return value.textValue();
    }

    private static String comment(String value) {
        if (value == null) return "";
        if (value.length() > 1000 || value.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
            throw new ApiException(400, "Комментарий: до 1000 символов без управляющих символов");
        return value.trim();
    }
}
