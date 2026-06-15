package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeTypeRequest {

    @NotBlank(message = "Name cannot be blank")
    String name;

    @Min(value = 0, message = "Level must be >= 0")
    int level;
}
