package com.example.DormlyBackend.configuration.security;

import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.repository.UserRepository;
import com.example.DormlyBackend.service.JwtService;
import com.example.DormlyBackend.service.TokenBlacklistService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService            jwtService;
    private final UserRepository        userRepository;      // ← thay UserDetailsService
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        if (tokenBlacklistService.isBlacklisted(jwt)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked");
            return;
        }

        try {
            final String email = jwtService.extractUsername(jwt);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                userRepository.findByEmailWithRolesAndPermissions(email)
                        .ifPresent(user -> {

                            // Validate token với email
                            if (!jwtService.isTokenValid(jwt, email)) return;

                            Set<SimpleGrantedAuthority> authorities = buildAuthorities(user);

                            UserPrincipal userPrincipal = new UserPrincipal(
                                    user.getId(),
                                    user.getEmail(),
                                    user.getFullName(),   // lấy fullName ở đây
                                    authorities
                            );


                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(
                                            userPrincipal, null, authorities);

                            authToken.setDetails(
                                    new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authToken);

                            log.debug("Authenticated [{}] with authorities: {}", email, authorities);
                        });
            }

        } catch (JwtException e) {
            log.warn("JWT invalid: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // ─────────────────────────────────────────────────────────
    // Build authorities từ roles + permissions của user
    //   ROLE_ADMIN
    //   PERMISSION_USERMANAGEMENT_VIEW
    //   PERMISSION_USERMANAGEMENT_EDIT
    // ─────────────────────────────────────────────────────────
    private Set<SimpleGrantedAuthority> buildAuthorities(User user) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        for (Role role : user.getRoles()) {

            String roleName = role.getName();
            if (roleName != null && !roleName.isBlank()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase(Locale.ROOT)));
            }

            for (Permission permission : role.getPermissions()) {
                authorities.add(
                        new SimpleGrantedAuthority(permission.getCode())
                );
            }
        }
        return authorities;
    }
}

