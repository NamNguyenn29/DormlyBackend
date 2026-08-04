package com.example.DormlyBackend.configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    private final ResourceLoader resourceLoader;

    @Value("${notification.firebase.credentials-path}")
    private String credentialsPath;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing Firebase App with credentials from: {}", credentialsPath);
            Resource resource = resourceLoader.getResource(credentialsPath);
            
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(is))
                            .build();
                    if (FirebaseApp.getApps().isEmpty()) {
                        FirebaseApp.initializeApp(options);
                        log.info("Firebase App initialized successfully with service account.");
                    }
                }
            } else {
                log.warn("Firebase credentials file not found at {}. Fallback to Application Default Credentials.", credentialsPath);
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.getApplicationDefault())
                            .build();
                    FirebaseApp.initializeApp(options);
                    log.info("Firebase App initialized successfully with Application Default Credentials.");
                }
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase App: {}", e.getMessage(), e);
        }
    }
}
