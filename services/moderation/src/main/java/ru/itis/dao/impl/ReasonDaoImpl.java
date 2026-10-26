package ru.itis.dao.impl;

import ru.itis.dao.ReasonDao;
import ru.itis.entity.Reason;
import ru.itis.entity.enums.BlockReason;
import ru.itis.util.DbConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReasonDaoImpl implements ReasonDao {

    private static final String SQL_SAVE = "INSERT INTO reasons (type, description, default_duration_seconds) VALUES (?, ?, ?) ON CONFLICT (type) DO UPDATE SET description = EXCLUDED.description, default_duration_seconds = EXCLUDED.default_duration_seconds;";
    private static final String SQL_FIND_ALL = "SELECT type, description, default_duration_seconds FROM reasons;";

    @Override
    public void save(Reason reason) {
        try (Connection connection = DbConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SQL_SAVE)){

            preparedStatement.setString(1, reason.getReasonType().name());
            preparedStatement.setString(2, reason.getDescription());
            if(reason.getLockTime() == null) preparedStatement.setNull(3, Types.BIGINT);
            else preparedStatement.setLong(3, reason.getLockTime());

            int rows = preparedStatement.executeUpdate();
            if (rows != 1) throw new RuntimeException("Ошибка сохранения причины");

        } catch (SQLException e) {
            throw new RuntimeException("Ошибка сохранения причины блокировки", e);
        }
    }

    @Override
    public List<Reason> findAll() {
        List<Reason> reasons = new ArrayList<>();
        try (Connection connection = DbConnection.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(SQL_FIND_ALL)){
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                reasons.add(mapper(rs));
            }
            return reasons;
        } catch (SQLException e) {
            throw new RuntimeException("Ошибка получения причин блокировки", e);
        }
    }

    private static Reason mapper(ResultSet rs) throws SQLException {
        return new Reason(
                BlockReason.valueOf(rs.getString("type")),
                rs.getString("description"),
                rs.getObject("default_duration_seconds", Long.class)
        );
    }
}
