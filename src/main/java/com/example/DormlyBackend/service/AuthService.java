package com.example.DormlyBackend.service;

import com.example.DormlyBackend.configuration.security.oauth2.OAuth2AuthCodeStore;
import com.example.DormlyBackend.dto.request.ForgotPasswordRequest;
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
import com.example.DormlyBackend.entity.information.StudentProfile;
import com.example.DormlyBackend.entity.information.StudentProfileHistory;
import com.example.DormlyBackend.repository.StudentProfileRepository;
import com.example.DormlyBackend.repository.StudentProfileHistoryRepository;
import org.springframework.web.multipart.MultipartFile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

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
    private final  OAuth2AuthCodeStore oAuth2AuthCodeStore;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentProfileHistoryRepository studentProfileHistoryRepository;
    private final UserDocumentService userDocumentService;


    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.jwt.expiration-ms}")
    private long accessExpirationMs;

    public void register(RegisterRequest request, MultipartFile citizenIdFile, MultipartFile studentCardFile) {
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

        User savedUser = userRepository.save(user);

        // Save documents
        if (citizenIdFile != null && !citizenIdFile.isEmpty()) {
            userDocumentService.upsert(savedUser.getId(), "CCCD_FRONT", "PENDING", null, citizenIdFile);
        }
        if (studentCardFile != null && !studentCardFile.isEmpty()) {
            userDocumentService.upsert(savedUser.getId(), "STUDENT_CARD", "PENDING", null, studentCardFile);
        }

        // Create student profile
        StudentProfile profile = new StudentProfile();
        profile.setId(savedUser.getId());
        profile.setUser(savedUser);
        profile.setStudentCode(request.getStudentCode());
        profile.setMajor(request.getMajor());
        profile.setIdentityNumber(request.getIdentityNumber());
        profile.setStartYear(request.getStartYear());
        profile.setEndYear(request.getEndYear());

        // Raw preferences
        profile.setSleepTime(request.getSleepTime());
        profile.setWakeUpTime(request.getWakeUpTime());
        profile.setSleepScore(request.getSleepScore());
        profile.setWakeScore(request.getWakeScore());
        profile.setQuietPreference(request.getQuietPreference());
        profile.setQuietPreferenceScore(request.getQuietPreferenceScore());
        profile.setSocialPreference(request.getSocialPreference());
        profile.setSocialPreferenceScore(request.getSocialPreferenceScore());
        profile.setStudyHabit(request.getStudyHabit());
        profile.setStudyHabitScore(request.getStudyHabitScore());
        profile.setRoutineStrictness(request.getRoutineStrictness());
        profile.setRoutineStrictnessScore(request.getRoutineStrictnessScore());
        profile.setAdaptability(request.getAdaptability());
        profile.setAdaptabilityScore(request.getAdaptabilityScore());
        profile.setRoommatePreference(request.getRoommatePreference());
        profile.setFriendName(request.getFriendName());
        profile.setFriendStudentId(request.getFriendStudentId());
        profile.setFriendBlock(request.getFriendBlock());
        profile.setFriendFloor(request.getFriendFloor());
        profile.setFriendRoom(request.getFriendRoom());

        // Calculate scores using PersonalityUtil
        int sleepRhythm = com.example.DormlyBackend.util.PersonalityUtil.mapSleepTime(request.getSleepTime());
        int wakeRhythm = com.example.DormlyBackend.util.PersonalityUtil.mapWakeTime(request.getWakeUpTime());
        int quietPref = com.example.DormlyBackend.util.PersonalityUtil.mapPreference(request.getQuietPreference());
        int socialPref = com.example.DormlyBackend.util.PersonalityUtil.mapPreference(request.getSocialPreference());
        int studyHab = com.example.DormlyBackend.util.PersonalityUtil.mapPreference(request.getStudyHabit());
        int routineStric = com.example.DormlyBackend.util.PersonalityUtil.mapPreference(request.getRoutineStrictness());
        int adapt = com.example.DormlyBackend.util.PersonalityUtil.mapPreference(request.getAdaptability());

        profile.setSleepRhythmScore(sleepRhythm);
        profile.setWakeRhythmScore(wakeRhythm);
        profile.setQuietPreferenceScore(quietPref);
        profile.setSocialPreferenceScore(socialPref);
        profile.setStudyHabitScore(studyHab);
        profile.setRoutineStrictnessScore(routineStric);
        profile.setAdaptabilityScore(adapt);

        profile.setCalculationVersion("PERSONALITY_VECTOR_V1");
        profile.setCalculatedAt(LocalDateTime.now());

        StudentProfile savedProfile = studentProfileRepository.save(profile);

        // Save profile history snapshot
        StudentProfileHistory history = new StudentProfileHistory();
        history.setStudentProfile(savedProfile);
        history.setStartYear(savedProfile.getStartYear());
        history.setEndYear(savedProfile.getEndYear());
        history.setSleepTime(savedProfile.getSleepTime());
        history.setWakeUpTime(savedProfile.getWakeUpTime());
        history.setQuietPreference(savedProfile.getQuietPreference());
        history.setSocialPreference(savedProfile.getSocialPreference());
        history.setStudyHabit(savedProfile.getStudyHabit());
        history.setRoutineStrictness(savedProfile.getRoutineStrictness());
        history.setAdaptability(savedProfile.getAdaptability());
        history.setRoommatePreference(savedProfile.getRoommatePreference());
        history.setFriendName(savedProfile.getFriendName());
        history.setFriendStudentId(savedProfile.getFriendStudentId());
        history.setFriendBlock(savedProfile.getFriendBlock());
        history.setFriendFloor(savedProfile.getFriendFloor());
        history.setFriendRoom(savedProfile.getFriendRoom());

        history.setSleepRhythmScore(savedProfile.getSleepRhythmScore());
        history.setWakeRhythmScore(savedProfile.getWakeRhythmScore());
        history.setQuietPreferenceScore(savedProfile.getQuietPreferenceScore());
        history.setSocialPreferenceScore(savedProfile.getSocialPreferenceScore());
        history.setStudyHabitScore(savedProfile.getStudyHabitScore());
        history.setRoutineStrictnessScore(savedProfile.getRoutineStrictnessScore());
        history.setAdaptabilityScore(savedProfile.getAdaptabilityScore());

        history.setCalculationVersion(savedProfile.getCalculationVersion());
        history.setCalculatedAt(savedProfile.getCalculatedAt());
        history.setTriggerReason("INITIAL_REGISTRATION");
        history.setChangedAt(LocalDateTime.now());

        studentProfileHistoryRepository.save(history);
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

    private AuthTokensResponse issueTokens(User user, HttpServletResponse response) {
        String accessToken  = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        String refreshToken = jwtService.generateRefreshToken(userDetailsService.loadUserByUsername(user.getEmail()));


        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        setRefreshCookie(response, refreshToken, Duration.ofMillis(refreshExpirationMs));

        return AuthTokensResponse.builder()
                .accessToken(accessToken)
                .build();
    }

    private void clearOAuth2CodeCookie(HttpServletResponse response) {
        Cookie expired = new Cookie("OAUTH2_CODE", "");
        expired.setHttpOnly(true);
        expired.setMaxAge(0);
        expired.setPath("/api/v1/auth/oauth2/token");
        response.addCookie(expired);
    }

    private Set<Role> resolveRolesByName(Set<String> roleNames) {
        if (roleNames == null)
            return Set.of();
        return roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Role", name)))
                .collect(java.util.stream.Collectors.toSet());
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, request.getEmail()));

        if(!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw ExceptionFactory.business(ErrorCode.PASSWORD_NOT_EQUAL,request.getConfirmPassword());
        }

        RequestCode forgotCode =  requestCodeRepository.findTopByRecipientContactAndPurposeOrderByExpiryTimeDesc(request.getEmail(), PurposeCode.FORGOT_PASSWORD)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND,request.getCode()));

        if(!forgotCode.getCode().equals(request.getCode()) || forgotCode.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Invalid code");
        }
        requestCodeRepository.delete(forgotCode);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    public AuthTokensResponse exchangeOAuth2Code(String code, HttpServletResponse response) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing OAuth2 code");
        }

        String email = oAuth2AuthCodeStore.consumeEmail(code);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OAuth2 code");
        }

        User user = userRepository.findUserWithRolesByEmail(email)
                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.USER_NOT_FOUND, email));

        // Xóa OAUTH2_CODE cookie
        clearOAuth2CodeCookie(response);

        return issueTokens(user, response);
    }

    public AuthTokensResponse loginWithFirebase(String idToken, HttpServletResponse response) {
        if (idToken == null || idToken.isBlank()) {
            throw ExceptionFactory.business(ErrorCode.INVALID_REQUEST, "Missing Firebase ID Token");
        }

        try {
            // 1. Verify token thông qua Firebase Admin SDK
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String email = decodedToken.getEmail();
            String fullName = decodedToken.getName();

            // 2. Tìm hoặc tạo mới người dùng
            User user = userRepository.findUserWithRolesByEmail(email)
                    .orElseGet(() -> {
                        log.info("Creating new Firebase Google user with email: {}", email);
                        Role userRole = roleRepository.findByName("User")
                                .orElseThrow(() -> ExceptionFactory.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Role", "User"));
                        
                        User newUser = new User();
                        newUser.setEmail(email);
                        newUser.setPassword(""); // Không có mật khẩu thô
                        newUser.setFullName(fullName != null ? fullName : email);
                        newUser.setRoles(Set.of(userRole));
                        newUser.setActive(true); // Tự động kích hoạt vì email đã được Google/Firebase verify
                        return userRepository.save(newUser);
                    });

            if (!user.isActive()) {
                throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "User is not active");
            }

            // 3. Cấp phát Token mới và trả về
            return issueTokens(user, response);

        } catch (com.google.firebase.auth.FirebaseAuthException e) {
            log.error("Firebase token verification failed: {}", e.getMessage());
            throw ExceptionFactory.business(ErrorCode.UNAUTHORIZED, "Invalid Firebase Token: " + e.getMessage());
        }
    }
}
