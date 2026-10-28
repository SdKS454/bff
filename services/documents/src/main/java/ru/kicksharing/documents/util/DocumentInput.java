package ru.kicksharing.documents.util;

import ru.kicksharing.documents.dto.request.GenerateDocumentRequest;

import java.time.LocalDate;
import java.util.UUID;

public final class DocumentInput {
    private DocumentInput() {
    }

    public static GenerateDocumentRequest parse(String json) {
        try {
            var tree = JsonUtil.getMapper().readTree(json);
            if (tree == null || !tree.isObject()) throw new IllegalArgumentException();
            for (String field : new String[]{"userId", "docType", "sourceId", "from", "to"}) {
                var value = tree.get(field);
                if (value != null && !value.isNull() && !value.isTextual()) throw new IllegalArgumentException();
                if (value != null && value.isTextual()) {
                    String text = value.textValue();
                    if (field.equals("userId") || field.equals("sourceId")) {
                        if (!text.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException();
                        UUID.fromString(text);
                    }
                    if (field.equals("from") || field.equals("to")) {
                        if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
                        LocalDate.parse(text);
                    }
                }
            }
            return JsonUtil.getMapper().treeToValue(tree, GenerateDocumentRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Некорректный JSON или тип поля", e);
        }
    }
}
