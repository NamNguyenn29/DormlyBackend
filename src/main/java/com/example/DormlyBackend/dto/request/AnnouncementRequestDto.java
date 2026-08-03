package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnnouncementRequestDto {
    @NotBlank(message = "Title cannot be blank")
    String title;

    @NotBlank(message = "Content cannot be blank")
    String content;

    @NotBlank(message = "Priority cannot be blank")
    String priority;

    @NotBlank(message = "Author cannot be blank")
    String author;
}
