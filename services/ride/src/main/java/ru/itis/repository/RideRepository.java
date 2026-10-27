package ru.itis.repository;

import ru.itis.model.RideEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RideRepository {
    void save(RideEntity ride);

    Optional<RideEntity> findById(UUID id);

    List<RideEntity> findByUserId(UUID userId);

    boolean position(UUID id,double x,double y,Instant at);

    boolean finish(RideEntity ride);

    boolean activate(UUID id, Instant startedAt);
    void cancel(UUID id);
    void removeCancelled(UUID id);
    boolean completeFinish(UUID id);
    List<RideEntity> pending();
    boolean pay(UUID id, Instant paidAt);
}
