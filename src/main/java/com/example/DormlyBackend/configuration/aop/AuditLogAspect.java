package com.example.DormlyBackend.configuration.aop;

import com.example.DormlyBackend.configuration.aop.Audit;
import com.example.DormlyBackend.configuration.security.UserPrincipal;
import com.example.DormlyBackend.dto.request.AuditLogCreateRequest;
import com.example.DormlyBackend.dto.request.LoginRequest;
import com.example.DormlyBackend.entity.authentication.User;

import com.example.DormlyBackend.repository.UserRepository;

import com.example.DormlyBackend.service.AuditLogService;
import com.example.DormlyBackend.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    private final UserRepository userRepository;
    private final JwtService jwtService;

    private String resolveAction(String methodName) {

        if (methodName.startsWith("create") || methodName.startsWith("save") || methodName.startsWith("add"))
            return "CREATE";
        if (methodName.startsWith("update") || methodName.startsWith("edit") || methodName.startsWith("patch"))
            return "UPDATE";

        if (methodName.startsWith("delete") || methodName.startsWith("remove"))
            return "DELETE";
        if (methodName.startsWith("get") || methodName.startsWith("find") || methodName.startsWith("fetch")
                || methodName.startsWith("list"))
            return "READ";
        return methodName.toUpperCase();
    }

    private String resolveEntityType(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        return className.replace("Service", "").replace("Impl", "").toUpperCase();
    }

    private String resolveEntityId(Object result, Object[] args, String action) {
        if ("CREATE".equals(action) && result != null) {
            try {
                var m = result.getClass().getMethod("getId");
                Object id = m.invoke(result);
                return id != null ? id.toString() : "";
            } catch (Exception ignored) {
                return "";
            }
        }

        for (Object arg : args) {
            if (arg instanceof UUID uuid)
                return uuid.toString();
            if (arg instanceof Long l)
                return l.toString();
            if (arg instanceof String s && isUuidLike(s))
                return s;
        }
        return "";
    }

    private boolean isUuidLike(String s) {
        return s != null && s.matches("[0-9a-fA-F\\-]{36}");
    }

    @Around("@annotation(audit)")
    public Object aroundAnnotated(ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        UUID currentUserId = currentUserId(joinPoint);
        Object result = joinPoint.proceed();

        String entityId = resolveEntityIdFromSpEL(audit.entityId(), joinPoint, result);
        log(currentUserId, audit.action(), audit.entityType(), entityId);
        return result;
    }

    @Around("execution(* com.example.DormlyBackend.service..*.*(..))" +
            " && !@annotation(com.example.DormlyBackend.configuration.aop.Audit)" +
            " && !within(com.example.DormlyBackend.service.AuditLogService)")
    public Object aroundAutoConvention(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID currentUserId = currentUserId(joinPoint);

        String methodName = joinPoint.getSignature().getName();
        String action = resolveAction(methodName);
        String entityType = resolveEntityType(joinPoint);

        Object result = joinPoint.proceed();

        String entityId = resolveEntityId(result, joinPoint.getArgs(), action);
        log(currentUserId, action, entityType, entityId);
        return result;
    }

    private String safeToJson(Object o) {
        try {
            return o == null ? null : objectMapper.writeValueAsString(o);
        } catch (Exception ex) {
            return null;
        }
    }

    private void log(UUID userId, String action, String entityType, String entityId) {

        // Hard skip: noisy JWT internals should never create audit rows.
        log.debug("AUDIT action={}, entityType={}, entityId={} userId={} ", action, entityType, entityId, userId);

        if ("EXTRACTUSERNAME".equalsIgnoreCase(action)
                || "GENERATETOKEN".equalsIgnoreCase(action)
                || "GENERATEREFRESHTOKEN".equalsIgnoreCase(action)
                || "ISTOKENVALID".equalsIgnoreCase(action)
                || "ISBLACKLISTED".equalsIgnoreCase(action)) {
            return;
        }

        // Ticket read paths run on every page load; mutations are still audited
        // via @Audit(entityType = "TICKET").
        if (("TICKETME".equalsIgnoreCase(entityType) || "TICKETADMIN".equalsIgnoreCase(entityType))
                && "READ".equalsIgnoreCase(action)) {
            return;
        }

        // Cron and event-driven fan-out have no authenticated principal, so every
        // run would write rows with a null userId and no meaning.
        if ("TICKETOVERDUESCHEDULER".equalsIgnoreCase(entityType)
                || "TICKETNOTIFICATIONPUBLISHER".equalsIgnoreCase(entityType)) {
            return;
        }

        try {

            HttpServletRequest httpRequest = getCurrentRequest();
            var req = new AuditLogCreateRequest();
            req.setUserId(userId);
            req.setAction(action);
            req.setEntityType(entityType);
            req.setEntityId(entityId);
            // For annotated sensitive actions, capture OldValues/NewValues (before/after)
            // for USER.
            if ("UPDATE_PASSWORD".equalsIgnoreCase(action) && entityId != null && !entityId.isBlank()) {
                // For password updates we currently only record best-effort snapshots.
                // To avoid lazy-graph serialization issues, we serialize the loaded entity
                // directly.
                if (!isUuidLike(entityId)) {
                    req.setOldValues(null);
                    req.setNewValues(null);
                } else {
                    UUID userUuid = UUID.fromString(entityId);

                    User before = userRepository.findById(userUuid).orElse(null);
                    String oldJson = before != null ? safeToJson(before) : null;

                    User after = userRepository.findById(userUuid).orElse(null);
                    String newJson = after != null ? safeToJson(after) : null;

                    req.setOldValues(oldJson);
                    req.setNewValues(newJson);
                }

            } else if ("UPDATE".equalsIgnoreCase(action) && entityId != null && !entityId.isBlank()) {

                // Currently we only know how to snapshot for USER updates because we have
                // UserRepository.
                // This can be extended by adding a registry mapping entityType -> repository.
                // NOTE: This generic snapshot diff requires helper methods to find + serialize
                // entities.
                // For now keep safe placeholders (null) until we add a scalable entity
                // snapshot/serializer registry.
                req.setOldValues(null);
                req.setNewValues(null);

            } else {
                req.setOldValues(null);
                req.setNewValues(null);
            }

            auditLogService.create(req, httpRequest);

        } catch (Exception e) {
            log.warn("Failed to write audit log", e);
        }
    }

    private UUID currentUserId(ProceedingJoinPoint joinPoint) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal up) {
            return up.getId();
        }

        // Resolve for auth-related flows when SecurityContext is not yet populated
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        try {
            // Skip noisy auth internals that shouldn't be audit-logged
            // (login/refresh/logout are still logged via main AuthService methods)
            if ("JwtService".equals(className)) {
                // Skip all JwtService internal helper actions (no audit noise)
                return null;
            }

            if ("TokenBlacklistService".equals(className) && "isBlacklisted".equals(methodName)) {
                return null;
            }

            if ("AuthService".equals(className) && "login".equals(methodName)) {

                Object[] args = joinPoint.getArgs();
                // AuthService.login(LoginRequest request, HttpServletResponse response)
                for (Object arg : args) {
                    if (arg instanceof LoginRequest lr && lr.getEmail() != null) {
                        return userRepository.findByEmail(lr.getEmail()).map(u -> u.getId()).orElse(null);
                    }
                }
            }

            if ("AuthService".equals(className) && ("refresh".equals(methodName) || "logout".equals(methodName))) {
                Object[] args = joinPoint.getArgs();
                HttpServletRequest http = null;
                for (Object arg : args) {
                    if (arg instanceof HttpServletRequest r) {
                        http = r;
                        break;
                    }
                }
                if (http == null)
                    http = getCurrentRequest();

                // refresh(): token from cookies
                String refreshToken = getCookieValue(http, "refreshToken");
                if (refreshToken != null) {
                    String username = jwtService.extractUsername(refreshToken);
                    return userRepository.findByEmail(username).map(u -> u.getId()).orElse(null);
                }

                // logout(): bearer token
                if (http != null) {
                    String authHeader = http.getHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String accessToken = authHeader.substring(7);
                        String username = jwtService.extractUsername(accessToken);
                        return userRepository.findByEmail(username).map(u -> u.getId()).orElse(null);
                    }
                }
            }

            // TokenBlacklistService.isBlacklisted(String token)
            if ("TokenBlacklistService".equals(className) && "isBlacklisted".equals(methodName)) {
                for (Object arg : joinPoint.getArgs()) {
                    if (arg instanceof String token && !token.isBlank()) {
                        String username = jwtService.extractUsername(token);
                        return userRepository.findByEmail(username).map(u -> u.getId()).orElse(null);
                    }
                }
            }
        } catch (Exception ignored) {
            // best-effort; never break business logic
        }

        return null;
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null)
            return null;
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName()))
                return cookie.getValue();
        }
        return null;
    }

    private String resolveEntityIdFromSpEL(String expression, ProceedingJoinPoint joinPoint, Object result) {
        if (expression == null || expression.isBlank())
            return "";
        if (!expression.startsWith("#"))
            return expression;

        try {
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            StandardEvaluationContext ctx = new StandardEvaluationContext();

            String[] names = sig.getParameterNames();
            Object[] args = joinPoint.getArgs();
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
            if (result != null)
                ctx.setVariable("result", result);

            Object val = new SpelExpressionParser().parseExpression(expression).getValue(ctx);
            return val != null ? val.toString() : "";
        } catch (Exception e) {
            log.warn("Failed to resolve entityId SpEL: {}", expression);
            return "";
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
