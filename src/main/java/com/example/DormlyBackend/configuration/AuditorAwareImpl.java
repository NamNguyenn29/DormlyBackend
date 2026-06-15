package com.example.DormlyBackend.configuration;

import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.SecurityUserDetails;
import com.example.DormlyBackend.configuration.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditorAware")
@RequiredArgsConstructor
public class AuditorAwareImpl implements AuditorAware<String> {

    private final UserRepository userRepository;

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Chưa login hoặc anonymous
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal().equals("anonymousUser")) {
            return Optional.of("SYSTEM");
        }

        // Lấy username (email) từ JWT principal
        String username = authentication.getName();

        // Query DB lấy fullName
        Object principal = authentication.getPrincipal();

        // Ép kiểu sang SecurityUserDetails để lấy fullName từ bộ nhớ (không query DB)
        if (principal instanceof UserPrincipal) {
            return Optional.ofNullable(((UserPrincipal) principal).getFullName()); // Gọi trực tiếp getter
        }

        return Optional.of(authentication.getName());
    }
}
