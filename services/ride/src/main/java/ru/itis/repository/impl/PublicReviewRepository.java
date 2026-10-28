package ru.itis.repository.impl;

import ru.itis.config.DatabaseConfig;
import ru.itis.exception.ApiException;
import ru.itis.model.PublicReview;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class PublicReviewRepository {
    private final DatabaseConfig database;
    public PublicReviewRepository() { this(new DatabaseConfig()); }
    public PublicReviewRepository(DatabaseConfig database) { this.database = database; }

    public List<PublicReview> list(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new ApiException(400, "Некорректные limit/offset");
        String sql = "SELECT ride_rating, scooter_rating, ride_comment, scooter_comment, reviewed_at FROM rides "
                + "WHERE reviewed_at IS NOT NULL AND status IN ('FINISHED','PAID') "
                + "ORDER BY reviewed_at DESC, id DESC LIMIT ? OFFSET ?";
        try (var connection = database.getConnection(); var query = connection.prepareStatement(sql)) {
            query.setInt(1, limit); query.setInt(2, offset);
            try (var rows = query.executeQuery()) {
                var result = new ArrayList<PublicReview>();
                while (rows.next()) result.add(new PublicReview(rows.getInt("ride_rating"), rows.getInt("scooter_rating"),
                        rows.getString("ride_comment"), rows.getString("scooter_comment"), rows.getTimestamp("reviewed_at").toInstant()));
                return result;
            }
        } catch (SQLException e) { throw new ApiException(503, "Отзывы временно недоступны"); }
    }
}
