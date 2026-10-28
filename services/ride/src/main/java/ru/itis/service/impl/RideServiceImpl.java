package ru.itis.service.impl;

import lombok.RequiredArgsConstructor;
import ru.itis.exception.ApiException;
import ru.itis.model.Rate;
import ru.itis.model.RideEntity;
import ru.itis.repository.RideRepository;
import ru.itis.service.RideIntegrationService;
import ru.itis.service.RideService;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class RideServiceImpl implements RideService {
    private final RideRepository rideRepository;
    private final RideIntegrationService integrationService;

    @Override
    public RideEntity start(UUID userId, String numberPlate, UUID rateId, Double posX, Double posY) {
        if (numberPlate == null || numberPlate.isBlank() || numberPlate.length() > 64) {
            throw new ApiException(400, "Некорректный numberPlate");
        }
        checkCoordinates(posX, posY);
        Rate rate = integrationService.getRate(rateId);


        RideEntity ride = new RideEntity();
        ride.setId(UUID.randomUUID());
        ride.setUserId(userId);
        ride.setNumberPlate(numberPlate);
        ride.setRateId(rate.getId());
        ride.setStatus("STARTING");
        ride.setStartedAt(now());
        ride.setStartPosX(posX);
        ride.setStartPosY(posY);
        ride.setCurrency(rate.getCurrency());
        ride.setPaymentMode(integrationService.paymentMode());
        ride.setUnlockFee(rate.getUnlockFee());
        ride.setPricePerMinute(rate.getPricePerMinute());
        rideRepository.save(ride);
        try {
            integrationService.reserve(ride);
            Instant started = now();
            if (!rideRepository.activate(ride.getId(), started))
                throw new ApiException(503, "Начало поездки отменено, повторите запрос");
            ride.setStartedAt(started);
            ride.setStatus("ACTIVE");
            return ride;
        } catch (RuntimeException failure) {
            try {
                rideRepository.cancel(ride.getId());
                recover();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public void position(UUID userId, UUID id, double x, double y) {
        checkCoordinates(x, y);
        RideEntity ride = getOwnedRide(userId, id);
        if (!ride.getStatus().equals("ACTIVE"))
            throw new ApiException(409, "Позицию можно обновить только в активной поездке");
        integrationService.position(ride, x, y);
        if (!rideRepository.position(id, x, y, now())) throw new ApiException(409, "Поездка уже завершается");
    }

    @Override
    public RideEntity finish(UUID userId, UUID id, double posX, double posY) {
        checkCoordinates(posX, posY);
        RideEntity ride = getOwnedRide(userId, id);
        if (ride.getStatus().equals("FINISHED") || ride.getStatus().equals("PAID")) {
            if (Double.valueOf(posX).equals(ride.getEndPosX()) && Double.valueOf(posY).equals(ride.getEndPosY()))
                return ride;
            throw new ApiException(409, "Поездка уже завершена с другими координатами");
        }
        if (ride.getStatus().equals("ACTIVE")) {
            Instant finishedAt = now();
            if (finishedAt.isBefore(ride.getStartedAt())) finishedAt = ride.getStartedAt();
            Duration elapsed = Duration.between(ride.getStartedAt(), finishedAt);
            long seconds = elapsed.getSeconds() + (elapsed.getNano() > 0 ? 1 : 0);
            ride.setAmount(integrationService.calculatePrice(ride, seconds));
            ride.setFinishedAt(finishedAt);
            ride.setDurationSeconds(seconds);
            ride.setEndPosX(posX);
            ride.setEndPosY(posY);
            ride.setStatus("FINISHING");
            if (!rideRepository.finish(ride)) {
                ride = getOwnedRide(userId, id);
                if (ride.getStatus().equals("FINISHED") || ride.getStatus().equals("PAID")) {
                    if (Double.valueOf(posX).equals(ride.getEndPosX()) && Double.valueOf(posY).equals(ride.getEndPosY()))
                        return ride;
                    throw new ApiException(409, "Поездка завершена с другими координатами");
                }
                if (!ride.getStatus().equals("FINISHING")) throw new ApiException(409, "Состояние поездки изменилось");
            }
        } else if (!ride.getStatus().equals("FINISHING")) throw new ApiException(409, "Поездка не начата");
        if (!Double.valueOf(posX).equals(ride.getEndPosX()) || !Double.valueOf(posY).equals(ride.getEndPosY()))
            throw new ApiException(409, "Завершение уже запрошено с другими координатами");
        integrationService.release(ride, true);
        rideRepository.completeFinish(ride.getId());
        ride.setStatus("FINISHED");
        return ride;
    }

    @Override
    public RideEntity pay(UUID userId, UUID id) {
        RideEntity ride = getOwnedRide(userId, id);
        if (ride.getStatus().equals("PAID")) {
            return ride;
        }
        if (!ride.getStatus().equals("FINISHED")) {
            throw new ApiException(409, "Сначала завершите поездку");
        }
        integrationService.processPayment(ride);
        ride.setStatus("PAID");
        ride.setPaymentMode("EDUCATIONAL");
        ride.setPaidAt(now());
        if (rideRepository.pay(id, ride.getPaidAt())) {
            return ride;
        }
        RideEntity saved = getOwnedRide(userId, id);
        if (saved.getStatus().equals("PAID")) {
            return saved;
        }
        throw new ApiException(409, "Состояние поездки изменилось");
    }

    @Override
    public RideEntity review(UUID userId, UUID id, ru.itis.dto.ReviewRequest request) {
        RideEntity ride = getOwnedRide(userId, id);
        if (!ride.getStatus().equals("FINISHED") && !ride.getStatus().equals("PAID"))
            throw new ApiException(409, "Отзыв доступен только после завершения поездки");
        var review = new ru.itis.model.RideReview(request.rideRating(), request.scooterRating(),
                request.rideComment(), request.scooterComment(), now());
        if (!rideRepository.review(id, review)) throw new ApiException(409, "Состояние поездки изменилось");
        return getOwnedRide(userId, id);
    }

    @Override
    public List<RideEntity> history(UUID userId, UUID requestedUserId) {
        if (!userId.equals(requestedUserId)) {
            throw new ApiException(403, "Нельзя читать чужие поездки");
        }
        return rideRepository.findByUserId(userId);
    }

    @Override
    public RideEntity get(UUID userId, UUID id) {
        RideEntity ride = getOwnedRide(userId, id);
        if (ride.getStatus().equals("STARTING") || ride.getStatus().equals("CANCELLING"))
            throw new ApiException(409, "Запрос поездки обрабатывается");
        return ride;
    }

    public void recover() {
        for (RideEntity ride : rideRepository.pending()) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                if (ride.getStatus().equals("CANCELLING")) {
                    try {
                        integrationService.release(ride, false);
                    } catch (ApiException e) {
                        if (e.getStatus() != 404 && e.getStatus() != 409) throw e;
                    }
                    rideRepository.removeCancelled(ride.getId());
                } else {
                    integrationService.release(ride, true);
                    rideRepository.completeFinish(ride.getId());
                }
            } catch (ApiException unavailable) {                                                          }
        }
    }

    private RideEntity getOwnedRide(UUID userId, UUID id) {
        RideEntity ride = rideRepository.findById(id)
                .orElseThrow(() -> new ApiException(404, "Поездка не найдена"));
        if (!ride.getUserId().equals(userId)) {
            throw new ApiException(403, "Чужая поездка");
        }
        return ride;
    }

    private void checkCoordinates(Double posX, Double posY) {
        if ((posX != null && (!Double.isFinite(posX) || Math.abs(posX) > 1_000_000)) || (posY != null && (!Double.isFinite(posY) || Math.abs(posY) > 1_000_000))) {
            throw new ApiException(400, "Координаты должны быть числами от -1000000 до 1000000");
        }
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
