package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentProfileRequest {

    @NotBlank
    @Size(max = 50)
    private String studentCode;

    @Size(max = 100)
    private String major;

    @Size(max = 20)
    private String identityNumber;
}
