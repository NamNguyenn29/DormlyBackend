package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserDocumentsRequest {

    @NotBlank
    private String documentType;

    // frontend can send either fileUrl or multipart image (file). If file is
    // provided, backend stores it and overrides fileUrl.
    private String fileUrl;

    // multipart upload
    private org.springframework.web.multipart.MultipartFile file;

    private String status;

    private String rejectReason;
}
