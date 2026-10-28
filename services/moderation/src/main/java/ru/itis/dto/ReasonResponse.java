package ru.itis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReasonResponse {

    private String reasonType;
    private String description;
    private Long durationSeconds;
}
