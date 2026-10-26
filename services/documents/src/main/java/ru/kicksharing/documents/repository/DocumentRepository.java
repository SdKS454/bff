package ru.kicksharing.documents.repository;

import ru.kicksharing.documents.config.DatabaseConnection;
import ru.kicksharing.documents.entity.Document;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DocumentRepository {

    // 1. Сохранить новый документ (статус PENDING)
    public Document save(Document document) {
        String sql = """
            INSERT INTO documents (id, user_id, document_type, source_id, period_from, period_to, status, created_at, updated_at, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, document.getId());
            stmt.setObject(2, document.getUserId());
            stmt.setString(3, document.getDocumentType());
            stmt.setObject(4, document.getSourceId());
            stmt.setObject(5, document.getPeriodFrom());
            stmt.setObject(6, document.getPeriodTo());
            stmt.setString(7, document.getStatus());
            stmt.setObject(8, document.getCreatedAt());
            stmt.setObject(9, document.getUpdatedAt());
            stmt.setBoolean(10, document.isDeleted());

            stmt.executeUpdate();
            return document;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка сохранения документа в БД", e);
        }
    }

    // 2. Обновить статус и время генерации
    public void updateStatus(UUID id, String status, OffsetDateTime generatedAt) {
        String sql = "UPDATE documents SET status = ?, generated_at = ?, updated_at = ? WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setObject(2, generatedAt);
            stmt.setObject(3, OffsetDateTime.now());
            stmt.setObject(4, id);

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка обновления статуса документа", e);
        }
    }

    // 3. Найти по ID (только неудаленные)
    public Optional<Document> findById(UUID id) {
        String sql = "SELECT * FROM documents WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToDocument(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка поиска документа по ID", e);
        }
    }

    // 4. История документов пользователя (без удаленных)
    public List<Document> findByUserId(UUID userId) {
        String sql = "SELECT * FROM documents WHERE user_id = ? AND is_deleted = FALSE ORDER BY created_at DESC";
        List<Document> documents = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    documents.add(mapRowToDocument(rs));
                }
            }
            return documents;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения истории документов", e);
        }
    }

    // 5. Soft delete
    public void softDelete(UUID id, UUID userId) {
        String sql = "UPDATE documents SET is_deleted = TRUE, deleted_at = ?, updated_at = ? WHERE id = ? AND user_id = ? AND is_deleted = FALSE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, OffsetDateTime.now());
            stmt.setObject(2, OffsetDateTime.now());
            stmt.setObject(3, id);
            stmt.setObject(4, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка удаления документа", e);
        }
    }

    // Приватный метод маппинга ResultSet -> Document
    private Document mapRowToDocument(ResultSet rs) throws SQLException {
        Document doc = new Document();
        doc.setId(rs.getObject("id", UUID.class));
        doc.setUserId(rs.getObject("user_id", UUID.class));
        doc.setDocumentType(rs.getString("document_type"));
        doc.setSourceId(rs.getObject("source_id", UUID.class));
        doc.setPeriodFrom(rs.getObject("period_from", java.time.LocalDate.class));
        doc.setPeriodTo(rs.getObject("period_to", java.time.LocalDate.class));
        doc.setStatus(rs.getString("status"));
        doc.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
        doc.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
        doc.setGeneratedAt(rs.getObject("generated_at", OffsetDateTime.class));
        doc.setDeleted(rs.getBoolean("is_deleted"));
        doc.setDeletedAt(rs.getObject("deleted_at", OffsetDateTime.class));
        return doc;
    }
}