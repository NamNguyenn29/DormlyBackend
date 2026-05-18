package com.example.DormlyBackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@SpringBootApplication
public class DormlyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(DormlyBackendApplication.class, args);
	}

}
