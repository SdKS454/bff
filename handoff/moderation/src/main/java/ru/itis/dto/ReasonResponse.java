package ru.itis.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReasonResponse {

    private String reasonType;
    private String description;
    private Long durationSeconds;
}
