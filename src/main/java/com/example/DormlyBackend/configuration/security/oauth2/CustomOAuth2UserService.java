package com.example.DormlyBackend.configuration.security.oauth2;

import com.example.DormlyBackend.configuration.security.oauth2.OAuth2UserPrincipal;
import com.example.DormlyBackend.entity.authentication.User;
import com.example.DormlyBackend.repository.RoleRepository;
import com.example.DormlyBackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String email    = oAuth2User.getAttribute("email");
        String fullName = oAuth2User.getAttribute("name");

        User user = userRepository.findByEmailWithRolesAndPermissions(email)
                .orElseGet(() -> createNewGoogleUser(email, fullName));

        return new OAuth2UserPrincipal(user, oAuth2User.getAttributes());
    }


    private User createNewGoogleUser(String email, String fullName) {
        log.info("Creating new user with email: {}", email);
        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("Role USER not found in DB"));

        User user = new User();
        user.setEmail(email);
        user.setPassword("");                                    // không có password
        user.setFullName(fullName != null ? fullName : email);
        user.setRoles(Set.of(userRole));

        return userRepository.save(user);
    }
}