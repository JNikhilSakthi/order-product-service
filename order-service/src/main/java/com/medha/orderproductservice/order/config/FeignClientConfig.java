package com.medha.orderproductservice.order.config;

import feign.Logger;
import feign.Retryer;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Custom behaviour for the {@code ProductClient} Feign client, wired in via
 * {@code @FeignClient(configuration = FeignClientConfig.class)}.
 *
 * <p>With only one Feign client in this service it is safe for this class to
 * also be picked up by component scanning; in a service with <em>multiple</em>
 * Feign clients needing different settings, a configuration class like this
 * should live outside the base package (or be excluded from
 * {@code @ComponentScan}) so it stays scoped to the one client that
 * references it, instead of becoming the default for every client.
 */
@Configuration
public class FeignClientConfig {

    /**
     * FULL logs headers, body and metadata for both the request and the
     * response. Verbose on purpose - this is a learning project. In a real
     * system this would typically be dialed down to BASIC or HEADERS.
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public ErrorDecoder errorDecoder(ObjectMapper objectMapper) {
        return new ProductClientErrorDecoder(objectMapper);
    }

    @Bean
    public RequestInterceptor correlationIdRequestInterceptor() {
        return new CorrelationIdRequestInterceptor();
    }

    /**
     * Retries idempotent GET reads up to 3 times with exponential backoff
     * between 100ms and 1s. POST reserve/release calls are NOT safe to
     * blindly retry (a retried reserve could double-decrement stock), so
     * {@code ProductServiceImpl.reserveStock/releaseStock} are naturally
     * idempotent-by-design would be a further improvement; here the retry is
     * scoped at the Feign transport level and only kicks in for connection
     * failures, not for successfully-received error responses.
     */
    @Bean
    public Retryer retryer(@Value("${product-service.retry.period-ms:100}") long period,
                            @Value("${product-service.retry.max-period-ms:1000}") long maxPeriod,
                            @Value("${product-service.retry.max-attempts:3}") int maxAttempts) {
        return new Retryer.Default(period, maxPeriod, maxAttempts);
    }
}
