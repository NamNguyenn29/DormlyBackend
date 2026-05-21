package com.example.DormlyBackend.configuration.security.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2AuthCodeStore authCodeStore;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req,
                                        HttpServletResponse res,
                                        Authentication auth) throws IOException {

        OAuth2UserPrincipal principal = (OAuth2UserPrincipal) auth.getPrincipal();

        // Tạo auth code ngắn hạn — KHÔNG truyền JWT lên URL
        String code = authCodeStore.generate(principal.getUser().getEmail());

        // Đặt vào HttpOnly cookie, chỉ gửi khi gọi đúng path
        Cookie cookie = new Cookie("OAUTH2_CODE", code);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);                     // → true khi production (HTTPS)
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(300);                       // 5 phút
        res.addCookie(cookie);

        // Redirect về frontend, không kèm token trên URL
        getRedirectStrategy().sendRedirect(req, res, frontendUrl + "/oauth2/callback");
    }
}