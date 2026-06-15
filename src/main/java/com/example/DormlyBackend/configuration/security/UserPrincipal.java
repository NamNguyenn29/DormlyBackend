package com.example.DormlyBackend.configuration.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private UUID id;
    private String email;
    private String fullName;
    private Set<SimpleGrantedAuthority> authorities;
}
