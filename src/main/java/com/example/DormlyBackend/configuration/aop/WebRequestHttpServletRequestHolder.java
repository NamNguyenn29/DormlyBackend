package com.example.DormlyBackend.configuration.aop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;

@Configuration
public class WebRequestHttpServletRequestHolder {

    @Bean
    @RequestScope
    public HttpServletRequest httpServletRequest() {
        return null; // placeholder; actual injection handled by Spring
    }
}
