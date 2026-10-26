package ru.itis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserResponse {
    private UUID id;
    private boolean blocked;
    private boolean deleted;
}
