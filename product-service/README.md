# product-service

Owns the product catalog and inventory. Plain REST provider — has no knowledge of `order-service`; it is the **callee** in this project's OpenFeign story, not a Feign consumer.

Port: **8081** · Schema: `product_db` · DB user: `product_user`

## What lives here

- `domain/Product.java` — the catalog entity: `sku` (unique business key), `name`, `description`, `price`, `stockQuantity`, plus a `@Version` column for optimistic locking so two concurrent `reserveStock` calls against the same product can't both succeed and oversell inventory.
- `controller/ProductController.java` — REST surface under `/api/products`. Its endpoint shapes (`GET /{id}`, `POST /{id}/reserve`, `POST /{id}/release`) are exactly what order-service's `ProductClient` Feign interface calls.
- `service/ProductServiceImpl.java` — business logic: CRUD, plus `reserveStock`/`releaseStock` which mutate `stockQuantity` and throw `InsufficientStockException` when a reservation would go negative.
- `exception/GlobalExceptionHandler.java` — maps `ProductNotFoundException` → 404, `InsufficientStockException`/`DuplicateSkuException`/`OptimisticLockingFailureException` → 409, validation failures → 400, everything else → 500, all as a uniform `ApiError` JSON body. This is the exact JSON shape order-service's Feign `ErrorDecoder` parses.
- `config/JpaAuditingConfig.java` — `@EnableJpaAuditing` kept off the `@SpringBootApplication` class so `@WebMvcTest` slices aren't forced to initialize JPA auditing infrastructure they don't need.
- `resources/data.sql` — idempotent seed data (5 sample products) that only runs against a real MySQL database (`sql.init.mode: always`, `platform: mysql`); disabled in tests via the test `application.yml`.

## Endpoints

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/api/products` | — | `200` `List<ProductResponse>` |
| GET | `/api/products/{id}` | — | `200` `ProductResponse` / `404` |
| POST | `/api/products` | `ProductRequest` | `201` `ProductResponse` / `400` / `409` (dup sku) |
| PUT | `/api/products/{id}` | `ProductRequest` | `200` `ProductResponse` / `404` / `409` |
| DELETE | `/api/products/{id}` | — | `204` / `404` |
| POST | `/api/products/{id}/reserve` | `{"quantity": n}` | `200` `ProductResponse` (decremented) / `404` / `409` insufficient stock |
| POST | `/api/products/{id}/release` | `{"quantity": n}` | `200` `ProductResponse` (incremented) / `404` |

## Running standalone

```bash
# Needs MySQL reachable per application.yml, or just run the compose mysql service:
docker compose up mysql
mvn -pl product-service -am spring-boot:run
```
Defaults to `localhost:3306/product_db` with user `product_user`/`product_pass`; override via `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` env vars.

## Tests (14)

```bash
mvn -pl product-service -am test
```
- `ProductServiceImplTest` — Mockito unit tests for CRUD + reserve/release, including duplicate-SKU and insufficient-stock paths.
- `ProductControllerTest` (`@WebMvcTest`) — status codes and JSON shape assertions with the service layer mocked.
- `ProductRepositoryTest` (`@DataJpaTest`) — `findBySku`/`existsBySku`, and that auditing timestamps + `version` populate on save.

See the root [README.md](../README.md) for the full architecture, configuration walkthrough, Docker setup, and interview prep covering the OpenFeign side of this integration.
