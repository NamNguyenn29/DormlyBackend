package com.example.DormlyBackend.service;

import com.example.DormlyBackend.dto.request.LoginRequest;
import com.example.DormlyBackend.dto.request.RegisterRequest;
import com.example.DormlyBackend.dto.response.AuthTokensResponse;
import com.example.DormlyBackend.entity.authentication.RequestCode;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.enums.PurposeCode;
import com.example.DormlyBackend.exception.code.ErrorCode;
import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.repository.RequestCodeRepository;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RequestCodeRepository requestCodeRepository;


    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.jwt.expiration-ms}")
    private long accessExpirationMs;

    public void register(RegisterRequest request) {
        userRepository.findByEmail(request.getEmail())
                .ifPresent(u -> {
                    throw ExceptionFactory.business(ErrorCode.USER_ALREADY_EXISTS, request.getEmail());
                });


        RequestCode regisCode =  requestCodeRepository.findTopByRecipientContactAndPurposeOrderByExpiryTimeDesc(request.getEmail(), PurposeCode.REGISTRATION)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND,request.getRegistrationCode()));

        if(!regisCode.getCode().equals(request.getRegistrationCode()) || regisCode.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Invalid registration code");
        }
        requestCodeRepository.delete(regisCode);

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setActive(false);

        Set<Role> roles = Set.of( roleRepository.findByName("User").orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND,"Role","User")));

        user.setRoles(roles);

        userRepository.save(user);
    }

    public AuthTokensResponse login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findUserWithRolesByEmail(request.getEmail())
                .orElseThrow(() -> ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        if(!user.isActive()){
            throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "User is not active");
        }

        String accessToken = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        String refreshToken = jwtService.generateRefreshToken(userDetailsService.loadUserByUsername(user.getEmail()));

        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        setRefreshCookie(response, refreshToken, Duration.ofMillis(refreshExpirationMs));

        return AuthTokensResponse.builder().accessToken(accessToken).build();
    }

    public AuthTokensResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getRefreshTokenFromCookies(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "Refresh token is missing");
        }

        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "Refresh token is invalid");
        }

        String username = jwtService.extractUsername(refreshToken);
        if (username == null) {
            throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "Refresh token is invalid");
        }

        User user = userRepository.findUserWithRolesByEmail(username)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, username));

        if (user.getRefreshToken() == null || !user.getRefreshToken().equals(refreshToken)) {
            throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "Refresh token mismatch");
        }

        // blacklist old refresh token (optional)
        tokenBlacklistService.blacklist(refreshToken, Duration.ofMillis(refreshExpirationMs));

        String newAccessToken = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        String newRefreshToken = jwtService
                .generateRefreshToken(userDetailsService.loadUserByUsername(user.getEmail()));

        user.setRefreshToken(newRefreshToken);
        userRepository.save(user);

        setRefreshCookie(response, newRefreshToken, Duration.ofMillis(refreshExpirationMs));

        return AuthTokensResponse.builder().accessToken(newAccessToken).build();
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            tokenBlacklistService.blacklist(accessToken, Duration.ofMillis(accessExpirationMs));
        }

        String refreshToken = getRefreshTokenFromCookies(request);
        if (refreshToken != null) {
            tokenBlacklistService.blacklist(refreshToken, Duration.ofMillis(refreshExpirationMs));
        }

        clearRefreshCookie(response);

        // best-effort: clear refresh token in DB
        try {
            if (refreshToken != null && jwtService.extractUsername(refreshToken) != null) {
                String email = jwtService.extractUsername(refreshToken);
                userRepository.findByEmail(email).ifPresent(u -> {
                    u.setRefreshToken(null);
                    userRepository.save(u);
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken, Duration ttl) {
        // HttpOnly cookie
        int maxAgeSeconds = (int) ttl.getSeconds();

        // SameSite=Lax by default; use secure in prod
        boolean secure = false;

        String cookie = String.format(
                "%s=%s; Path=/api/v1/auth/refresh; HttpOnly; Max-Age=%d; SameSite=Lax%s",
                REFRESH_COOKIE_NAME,
                refreshToken,
                maxAgeSeconds,
                secure ? "; Secure" : "");

        response.addHeader("Set-Cookie", cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        boolean secure = true;
        String cookie = String.format(
                "%s=; Path=/api/v1/auth/refresh; HttpOnly; Max-Age=0; SameSite=Lax%s",
                REFRESH_COOKIE_NAME,
                secure ? "; Secure" : "");
        response.addHeader("Set-Cookie", cookie);
    }

    private String getRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null)
            return null;
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private Set<Role> resolveRolesByName(Set<String> roleNames) {
        if (roleNames == null)
            return Set.of();
        return roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Role", name)))
                .collect(java.util.stream.Collectors.toSet());
    }

    public void forgotPassword(String email,String code,String newPassword,String confirmPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, email));

        if(!newPassword.equals(confirmPassword)) {
            throw ExceptionFactory.business(ErrorCode.PASSWORD_NOT_EQUAL,confirmPassword);
        }

        RequestCode forgotCode =  requestCodeRepository.findTopByRecipientContactAndPurposeOrderByExpiryTimeDesc(email, PurposeCode.FORGOT_PASSWORD)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND,code));

        if(!forgotCode.getCode().equals(code) || forgotCode.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Invalid code");
        }
        requestCodeRepository.delete(forgotCode);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
