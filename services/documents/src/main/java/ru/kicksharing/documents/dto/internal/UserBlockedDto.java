package ru.kicksharing.documents.dto.internal;

import java.time.OffsetDateTime;

public class UserBlockedDto {
    private String userFullName;
    private String userLogin;
    private String reason;
    private OffsetDateTime blockedAt;
    private OffsetDateTime blockedUntil;

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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public OffsetDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(OffsetDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public OffsetDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(OffsetDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }
}