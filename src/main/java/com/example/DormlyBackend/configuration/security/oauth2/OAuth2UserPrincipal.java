package com.example.DormlyBackend.configuration.security.oauth2;


import com.example.DormlyBackend.entity.authentication.Permission;
import com.example.DormlyBackend.entity.authentication.Role;
import com.example.DormlyBackend.entity.authentication.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class OAuth2UserPrincipal implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    public OAuth2UserPrincipal(User user, Map<String, Object> attributes) {
        this.user       = user;
        this.attributes = attributes;
    }

    @Override public Map<String, Object> getAttributes()  { return attributes; }
    @Override public String              getName()         { return user.getEmail(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return buildAuthorities(user);
    }


    private Set<SimpleGrantedAuthority> buildAuthorities(User user) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        for (Role role : user.getRoles()) {

            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));

            for (Permission permission : role.getPermissions()) {
                authorities.add(
                        new SimpleGrantedAuthority(permission.getCode())
                );
            }
        }
        return authorities;
    }
}