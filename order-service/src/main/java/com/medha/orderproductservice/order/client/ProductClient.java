package com.medha.orderproductservice.order.client;

import com.medha.orderproductservice.order.client.dto.ProductDto;
import com.medha.orderproductservice.order.client.dto.StockChangeRequest;
import com.medha.orderproductservice.order.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Declarative OpenFeign client for product-service. This interface is the
 * entire "how do I call the other service" story - Spring generates the HTTP
 * implementation at startup from these annotations, wired with the timeouts,
 * retries, logging, request interceptor and error decoder registered in
 * {@link FeignClientConfig}.
 *
 * <p>{@code name} registers this client under a logical id (used in metrics
 * and log lines); {@code url} is resolved from {@code product-service.url}
 * in application.yml, pointing at localhost for local dev and at the Docker
 * Compose service name in containers - no service discovery is involved.
 */
@FeignClient(
        name = "product-service",
        url = "${product-service.url}",
        configuration = FeignClientConfig.class
)
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    ProductDto getProductById(@PathVariable("id") Long id);

    @PostMapping("/api/products/{id}/reserve")
    ProductDto reserveStock(@PathVariable("id") Long id, @RequestBody StockChangeRequest request);

    @PostMapping("/api/products/{id}/release")
    ProductDto releaseStock(@PathVariable("id") Long id, @RequestBody StockChangeRequest request);
}
