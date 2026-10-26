package ru.itis.repository.impl;

import lombok.RequiredArgsConstructor;
import ru.itis.config.DatabaseConfig;
import ru.itis.exception.ApiException;
import ru.itis.model.RideEntity;
import ru.itis.repository.RideRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
public class RideRepositoryJdbcImpl implements RideRepository {
    private static final String INSERT = "INSERT INTO rides "
            + "(id, user_id, number_plate, rate_id, status, started_at, start_pos_x, start_pos_y, "
            + "unlock_fee, price_per_minute, currency, payment_mode) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String FIND_BY_ID = "SELECT * FROM rides WHERE id = ?";
    private static final String FIND_BY_USER = "SELECT * FROM rides WHERE user_id = ? AND status IN ('ACTIVE','FINISHING','FINISHED','PAID') ORDER BY started_at DESC, id";
    private static final String FINISH = "UPDATE rides SET status = 'FINISHING', finished_at = ?, end_pos_x = ?, "
            + "end_pos_y = ?, duration_seconds = ?, amount = ? WHERE id = ? AND status = 'ACTIVE'";
    private static final String PAY = "UPDATE rides SET status = 'PAID', payment_mode='EDUCATIONAL', paid_at = ? "
            + "WHERE id = ? AND status = 'FINISHED'";

    private final DatabaseConfig database;

    @Override
    public void save(RideEntity ride) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, ride.getId());
            statement.setObject(2, ride.getUserId());
            statement.setString(3, ride.getNumberPlate());
            statement.setObject(4, ride.getRateId());
            statement.setString(5, ride.getStatus());
            statement.setTimestamp(6, Timestamp.from(ride.getStartedAt()));
            statement.setObject(7, ride.getStartPosX());
            statement.setObject(8, ride.getStartPosY());
            statement.setBigDecimal(9, ride.getUnlockFee());
            statement.setBigDecimal(10, ride.getPricePerMinute());
            statement.setString(11, ride.getCurrency());
            statement.setString(12,ride.getPaymentMode());
            statement.executeUpdate();
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new ApiException(409, "У пользователя или самоката уже есть активная поездка");
            }
            throw new ApiException(503, "Хранилище поездок недоступно");
        }
    }

    @Override
    public Optional<RideEntity> findById(UUID id) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new ApiException(503, "Хранилище поездок недоступно");
        }
    }

    @Override
    public List<RideEntity> findByUserId(UUID userId) {
        List<RideEntity> rides = new ArrayList<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_USER)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rides.add(map(resultSet));
                }
            }
            return rides;
        } catch (SQLException e) {
            throw new ApiException(503, "Хранилище поездок недоступно");
        }
    }

    @Override
    public boolean finish(RideEntity ride) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(FINISH)) {
            statement.setTimestamp(1, Timestamp.from(ride.getFinishedAt()));
            statement.setObject(2, ride.getEndPosX());
            statement.setObject(3, ride.getEndPosY());
            statement.setObject(4, ride.getDurationSeconds());
            statement.setBigDecimal(5, ride.getAmount());
            statement.setObject(6, ride.getId());
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new ApiException(503, "Хранилище поездок недоступно");
        }
    }

    @Override
    public boolean pay(UUID id, Instant paidAt) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement(PAY)) {
            statement.setTimestamp(1, Timestamp.from(paidAt));
            statement.setObject(2, id);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new ApiException(503, "Хранилище поездок недоступно");
        }
    }

    @Override public boolean activate(UUID id, Instant startedAt) {
        return update("UPDATE rides SET status='ACTIVE', started_at=? WHERE id=? AND status='STARTING'", Timestamp.from(startedAt),id)==1;
    }
    @Override public void cancel(UUID id) { update("UPDATE rides SET status='CANCELLING' WHERE id=? AND status='STARTING'",id); }
    @Override public void removeCancelled(UUID id) { update("DELETE FROM rides WHERE id=? AND status='CANCELLING'",id); }
    @Override public boolean completeFinish(UUID id) { return update("UPDATE rides SET status='FINISHED' WHERE id=? AND status='FINISHING'",id)==1; }
    private int update(String sql,Object... args) {
        try(var c=database.getConnection();var q=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]); return q.executeUpdate();
        }catch(SQLException e){throw new ApiException(503,"Хранилище поездок недоступно");}
    }
    @Override public List<RideEntity> pending() {
        update("UPDATE rides SET status='CANCELLING' WHERE status='STARTING' AND started_at < NOW()-INTERVAL '30 seconds'");
        try(var c=database.getConnection();var q=c.prepareStatement("SELECT * FROM rides WHERE status IN ('CANCELLING','FINISHING') ORDER BY started_at LIMIT 50");var r=q.executeQuery()) {
            var result=new ArrayList<RideEntity>();while(r.next())result.add(map(r));return result;
        }catch(SQLException e){throw new ApiException(503,"Хранилище поездок недоступно");}
    }

    private RideEntity map(ResultSet resultSet) throws SQLException {
        RideEntity ride = new RideEntity();
        ride.setId(resultSet.getObject("id", UUID.class));
        ride.setUserId(resultSet.getObject("user_id", UUID.class));
        ride.setNumberPlate(resultSet.getString("number_plate"));
        ride.setRateId(resultSet.getObject("rate_id", UUID.class));
        ride.setStatus(resultSet.getString("status"));
        ride.setStartedAt(readInstant(resultSet, "started_at"));
        ride.setFinishedAt(readInstant(resultSet, "finished_at"));
        ride.setStartPosX(resultSet.getObject("start_pos_x", Double.class));
        ride.setStartPosY(resultSet.getObject("start_pos_y", Double.class));
        ride.setEndPosX(resultSet.getObject("end_pos_x", Double.class));
        ride.setEndPosY(resultSet.getObject("end_pos_y", Double.class));
        ride.setDurationSeconds(resultSet.getObject("duration_seconds", Long.class));
        ride.setAmount(resultSet.getBigDecimal("amount"));
        ride.setCurrency(resultSet.getString("currency"));
        ride.setPaymentMode(resultSet.getString("payment_mode"));
        ride.setPaidAt(readInstant(resultSet, "paid_at"));
        ride.setUnlockFee(resultSet.getBigDecimal("unlock_fee"));
        ride.setPricePerMinute(resultSet.getBigDecimal("price_per_minute"));
        return ride;
    }

    private Instant readInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        if (value == null) {
            return null;
        }
        return value.toInstant();
    }
}
