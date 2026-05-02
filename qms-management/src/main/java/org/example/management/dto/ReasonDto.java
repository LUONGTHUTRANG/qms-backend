package org.example.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.management.entity.enums.ReasonType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonDto {
    private Long id;
    private String code;
    private String name;
    private ReasonType type;
    private Boolean isActive;
}
