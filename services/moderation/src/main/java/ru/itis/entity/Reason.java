package ru.itis.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.entity.enums.BlockReason;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Reason {

    private BlockReason reasonType;
    private String description;
    private Long lockTime;

}
