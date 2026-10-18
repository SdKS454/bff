package bff.dto;

import java.util.UUID;

public record DocumentRequest(
        UUID userId,
        String docType,
        UUID sourceId,
        String from,
        String to) {
}
