package com.example.DormlyBackend.configuration;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// TraceIdProvider
@Component
@RequiredArgsConstructor
public class MdcTraceIdProvider implements TraceIdProvider {


    private final Tracer tracer;

    @Override
    public String current() {
        Span span = tracer.currentSpan();
        if (span == null) return UUID.randomUUID().toString();
        return span.context().traceId();
    }
}
