# order-service

Places orders against the catalog owned by `product-service`. Hosts the **OpenFeign declarative client** (`ProductClient`) that is the sole focus technology of this whole project.

Port: **8080** · Schema: `order_db` · DB user: `order_user`

## What lives here

- `client/ProductClient.java` — the OpenFeign interface. `@FeignClient(name = "product-service", url = "${product-service.url}", configuration = FeignClientConfig.class)` declares `getProductById`, `reserveStock`, `releaseStock` against product-service's REST contract; Spring generates the HTTP implementation at startup.
- `client/dto/ProductDto.java`, `client/dto/StockChangeRequest.java` — order-service's own local view of the JSON shapes product-service returns/accepts. Not shared code with product-service — only the JSON shape matches.
- `config/FeignClientConfig.java` — all the custom Feign behavior in one place:
  - `Logger.Level.FULL` bean — verbose request/response logging (paired with `feign.client.config.*.loggingLevel: full` in `application.yml` and a `DEBUG` logger for `ProductClient`).
  - `ErrorDecoder` bean → `ProductClientErrorDecoder`.
  - `RequestInterceptor` bean → `CorrelationIdRequestInterceptor`.
  - `Retryer` bean, configured from `product-service.retry.*` properties.
- `config/ProductClientErrorDecoder.java` — turns product-service's HTTP-level failures into typed exceptions: 404 → `ProductNotFoundException`, 409 → `InsufficientStockException`, 5xx → `ProductServiceUnavailableException`, everything else delegates to Feign's `ErrorDecoder.Default`. Parses product-service's `ApiError` JSON defensively (falls back to raw body / HTTP reason phrase if parsing fails).
- `config/CorrelationIdRequestInterceptor.java` — forwards the inbound request's `X-Correlation-Id` header onto every outbound Feign call, or mints a new UUID if there was no inbound HTTP request (e.g. a background job), so one request can be traced across both services' logs.
- `config/FeignClientsEnablement.java` / `config/JpaAuditingConfig.java` — `@EnableFeignClients`/`@EnableJpaAuditing` deliberately live on their own small `@Configuration` classes rather than on `OrderServiceApplication`. `@WebMvcTest` slice tests process annotations on the root `@SpringBootConfiguration` class regardless of slicing, so leaving these on the application class would drag Feign/JPA infrastructure into narrow controller tests — this was caught by an actual `mvn test` failure during development.
- `service/OrderServiceImpl.java` — `createOrder` reserves stock **item-by-item via sequential Feign calls**; if a later item fails (`ProductNotFoundException`, `InsufficientStockException`, or any `FeignException`), it calls `releaseStock` on every already-reserved item (logging a warning if even that fails) before rethrowing — the project's "manual saga" teaching moment. `cancelOrder` releases stock for every item and rejects an already-cancelled order.
- `domain/OrderItem.java` — stores `productId` plus a `productName`/`unitPrice` **snapshot** captured from the Feign response at order time, not a JPA relationship — Order and Product live in separate databases.

## Endpoints

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/orders` | — | `200` `List<OrderResponse>` |
| GET | `/api/orders/{id}` | — | `200` `OrderResponse` / `404` |
| POST | `/api/orders` | `OrderRequest` | `201` `OrderResponse` / `400` / `409` / `422` / `503` |
| PUT | `/api/orders/{id}/cancel` | — | `200` `OrderResponse` / `404` / `409` (already cancelled) |

## Running standalone

```bash
docker compose up mysql product-service
mvn -pl order-service -am spring-boot:run
```
Defaults `product-service.url` to `http://localhost:8081` and the datasource to `localhost:3306/order_db` (`order_user`/`order_pass`) — override via `PRODUCT_SERVICE_URL`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`.

## Tests (17)

```bash
mvn -pl order-service -am test
```
- `OrderServiceImplTest` — Mockito unit tests, including the two compensation scenarios (a later item hits insufficient stock / hits a not-found product) that assert `releaseStock` is called for exactly the previously-reserved items and nothing is persisted.
- `OrderControllerTest` (`@WebMvcTest`) — status/JSON assertions for 201/400/409/503/404/200 with `OrderService` mocked.
- `ProductClientIntegrationTest` (`@SpringBootTest`, WireMock-backed) — starts a real WireMock server, points `product-service.url` at it via `@DynamicPropertySource`, and drives the **real generated Feign proxy** end-to-end: JSON deserialization, the `X-Correlation-Id` header, and 404/409/500 → typed exception decoding.

See the root [README.md](../README.md) for the full architecture, configuration walkthrough, Docker setup, and interview prep.
