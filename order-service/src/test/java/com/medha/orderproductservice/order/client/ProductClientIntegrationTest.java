package com.medha.orderproductservice.order.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.medha.orderproductservice.order.client.dto.ProductDto;
import com.medha.orderproductservice.order.client.dto.StockChangeRequest;
import com.medha.orderproductservice.order.exception.InsufficientStockException;
import com.medha.orderproductservice.order.exception.ProductNotFoundException;
import com.medha.orderproductservice.order.exception.ProductServiceUnavailableException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real, Spring-generated {@code ProductClient} proxy against a
 * WireMock HTTP server standing in for product-service. This is the test
 * that proves the Feign contract - path templating, JSON (de)serialization,
 * the custom {@code ErrorDecoder}, and the correlation-id
 * {@code RequestInterceptor} - all actually work together over the wire,
 * which a Mockito-mocked {@code ProductClient} elsewhere in the test suite
 * cannot verify.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProductClientIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    @Autowired
    private ProductClient productClient;

    @BeforeAll
    static void startServer() {
        wireMockServer.start();
    }

    @AfterAll
    static void stopServer() {
        wireMockServer.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void productServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("product-service.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void getProductById_deserializesResponse_andSendsCorrelationIdHeader() {
        wireMockServer.stubFor(get(urlEqualTo("/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"sku":"SKU-001","name":"Laptop","description":"desc","price":999.99,"stockQuantity":5,"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}
                                """)));

        ProductDto product = productClient.getProductById(1L);

        assertThat(product.id()).isEqualTo(1L);
        assertThat(product.name()).isEqualTo("Laptop");
        assertThat(product.stockQuantity()).isEqualTo(5);

        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/products/1"))
                .withHeader("X-Correlation-Id", matching(".+")));
    }

    @Test
    void reserveStock_sendsJsonBody_andReturnsUpdatedProduct() {
        wireMockServer.stubFor(post(urlEqualTo("/api/products/1/reserve"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"id":1,"sku":"SKU-001","name":"Laptop","description":"desc","price":999.99,"stockQuantity":3,"createdAt":"2024-01-01T00:00:00Z","updatedAt":"2024-01-01T00:00:00Z"}
                                """)));

        ProductDto product = productClient.reserveStock(1L, new StockChangeRequest(2));

        assertThat(product.stockQuantity()).isEqualTo(3);
        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/products/1/reserve")));
    }

    @Test
    void getProductById_throwsProductNotFoundException_on404() {
        wireMockServer.stubFor(get(urlEqualTo("/api/products/999"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"timestamp":"2024-01-01T00:00:00Z","status":404,"error":"Not Found","message":"Product not found with id: 999","path":"/api/products/999"}
                                """)));

        assertThatThrownBy(() -> productClient.getProductById(999L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void reserveStock_throwsInsufficientStockException_on409() {
        wireMockServer.stubFor(post(urlEqualTo("/api/products/1/reserve"))
                .willReturn(aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"timestamp":"2024-01-01T00:00:00Z","status":409,"error":"Conflict","message":"Insufficient stock for product 1: requested 999 but only 5 available","path":"/api/products/1/reserve"}
                                """)));

        assertThatThrownBy(() -> productClient.reserveStock(1L, new StockChangeRequest(999)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void getProductById_throwsProductServiceUnavailable_on500() {
        wireMockServer.stubFor(get(urlEqualTo("/api/products/1"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"timestamp":"2024-01-01T00:00:00Z","status":500,"error":"Internal Server Error","message":"Database is down","path":"/api/products/1"}
                                """)));

        assertThatThrownBy(() -> productClient.getProductById(1L))
                .isInstanceOf(ProductServiceUnavailableException.class)
                .hasMessageContaining("500");
    }
}
