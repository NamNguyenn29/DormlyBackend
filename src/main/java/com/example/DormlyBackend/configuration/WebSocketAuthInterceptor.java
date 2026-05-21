package com.example.DormlyBackend.configuration;

import com.example.DormlyBackend.exception.factory.ExceptionFactory;
import com.example.DormlyBackend.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {

        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String authHeader =
                    accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null ||
                    !authHeader.startsWith("Bearer ")) {

                throw new IllegalArgumentException("Missing JWT");
            }

            String token = authHeader.substring(7);

            try {

                String username =
                        jwtService.extractUsername(token);


                boolean valid =
                        jwtService.isTokenValid(token, username);

                if (!valid) {
                    throw new IllegalArgumentException("Invalid JWT");
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of()
                        );


                accessor.setUser(authentication);

                log.info("WS CONNECTED USER = {}", username);

            } catch (Exception e) {

                log.error("WS AUTH FAILED", e);

                throw new IllegalArgumentException("Invalid JWT");
            }
        }

        return message;
    }
}