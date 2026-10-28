package ru.kicksharing.documents.dto.internal;

import java.time.LocalDate;
import java.util.List;

public class UserHistoryDto {
    private String userFullName;
    private String userLogin;
    private LocalDate from;
    private LocalDate to;
    private List<RidePaymentDto> rides;

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public List<RidePaymentDto> getRides() {
        return rides;
    }

    public void setRides(List<RidePaymentDto> rides) {
        this.rides = rides;
    }
}