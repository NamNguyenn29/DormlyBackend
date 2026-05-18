package com.example.DormlyBackend.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthTokensResponse {

    String accessToken;

    // refresh is stored in cookie (httpOnly), not returned in body
}
