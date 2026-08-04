package com.example.DormlyBackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FirebaseLoginRequest {
    @NotBlank(message = "Firebase ID Token cannot be blank")
    String token;
}
