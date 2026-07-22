package com.medha.orderproductservice.order.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Propagates (or mints) an {@code X-Correlation-Id} header on every outbound
 * Feign call to product-service, so a single request can be traced across
 * both services' logs. If the inbound HTTP request that triggered this call
 * already carries the header, it is forwarded as-is; otherwise a new id is
 * generated for calls with no originating HTTP request (e.g. scheduled jobs).
 */
public class CorrelationIdRequestInterceptor implements RequestInterceptor {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public void apply(RequestTemplate template) {
        String correlationId = resolveIncomingCorrelationId();
        template.header(CORRELATION_ID_HEADER, correlationId);
    }

    private String resolveIncomingCorrelationId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String existing = request.getHeader(CORRELATION_ID_HEADER);
            if (existing != null && !existing.isBlank()) {
                return existing;
            }
        }
        return UUID.randomUUID().toString();
    }
}
