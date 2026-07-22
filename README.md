# order-product-service

Order Service calling Product Service via **Spring Cloud OpenFeign** — a two-service Maven multi-module monorepo that teaches declarative HTTP clients, custom error decoding, request interceptors, retries and manual saga-style compensation.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-brightgreen)
![OpenFeign](https://img.shields.io/badge/OpenFeign-declarative%20HTTP%20client-blue)
![MySQL](https://img.shields.io/badge/MySQL-8.4-blue)
![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED)
![Maven](https://img.shields.io/badge/Build-Maven-red)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**Learning Track:** `springboot-openfeign-demo` (Project 17 of 17)
**Real-World Service Name:** `order-product-service`

---

## 1. Project Overview

### The problem this solves

In real systems, catalog/inventory data and order data are almost never owned by the same service. `product-service` owns the product catalog and stock levels; `order-service` needs to place orders against that catalog — check a product exists, reserve stock, and release it again on cancellation or failure. The two services are independently deployable: they run in separate processes, own separate MySQL schemas, and must talk to each other **over HTTP**, not through a shared database or a shared Java model.

Writing that HTTP integration by hand with `RestTemplate` or `WebClient` means hand-rolling URL building, JSON (de)serialization, timeout/retry configuration, and error translation for every single downstream call. **OpenFeign** turns that entire integration into a plain Java interface — the developer declares *what* to call, and Spring generates the HTTP client implementation at startup.

### Why OpenFeign

- **Declarative contracts.** `ProductClient` reads like a local service interface (`getProductById`, `reserveStock`, `releaseStock`) — the HTTP verb, path, and body are just annotations, no boilerplate `HttpEntity`/`ResponseEntity` juggling.
- **Pluggable everything.** The HTTP client (`feign-okhttp`), the error handling (`ErrorDecoder`), the retry policy (`Retryer`), the outgoing headers (`RequestInterceptor`), and per-client timeouts are all first-class extension points, configured once in `FeignClientConfig` and reused on every call the interface makes.
- **Same programming model Netflix/Spring Cloud popularized.** OpenFeign came out of Netflix OSS (Feign) and was adopted into Spring Cloud specifically for this "call a REST endpoint like a Java method" experience — it's still the default declarative HTTP client choice for Spring microservices that haven't moved to reactive `WebClient`/`@HttpExchange`.

### Where this pattern shows up in real companies

Any organization running more than a handful of Spring Boot microservices that talk to each other synchronously over REST uses this exact shape: an e-commerce checkout service calling an inventory service before confirming a cart; a payments service calling a fraud-scoring service before authorizing a charge; a booking service calling an availability service before confirming a reservation. Wherever there's a synchronous "check/reserve, and roll back on partial failure" flow between two independently owned REST services, this is the pattern.

---

## 2. Architecture

### High-Level Design (HLD)

```
                          ┌────────────────────────────┐
                          │        API Consumer        │
                          │   (curl / Postman / UI)    │
                          └─────────────┬──────────────┘
                                        │ HTTP :8080
                                        ▼
                          ┌────────────────────────────┐
                          │        order-service         │
                          │   /api/orders (port 8080)    │
                          │                              │
                          │  OrderController              │
                          │       │                       │
                          │  OrderServiceImpl              │
                          │       │                       │
                          │  ProductClient (OpenFeign) ───┼────┐
                          │       │                       │    │
                          │  OrderRepository               │    │  HTTP :8081
                          │       │                       │    │  (Feign-generated
                          │       ▼                       │    │   proxy)
                          │   order_db (MySQL)             │    │
                          └────────────────────────────┘    │
                                                             ▼
                          ┌────────────────────────────┐
                          │       product-service         │
                          │  /api/products (port 8081)    │
                          │                              │
                          │  ProductController             │
                          │       │                       │
                          │  ProductServiceImpl             │
                          │       │                       │
                          │  ProductRepository              │
                          │       │                       │
                          │       ▼                       │
                          │   product_db (MySQL)           │
                          └────────────────────────────┘
```

Both services share one MySQL 8.4 instance in Docker Compose but use **separate schemas and separate DB users** (`order_db`/`order_user` and `product_db`/`product_user`) — there is no cross-schema foreign key, and no shared Java module between the two services. The only contract between them is the JSON shape exchanged over HTTP.

### Low-Level Design (LLD) — `createOrder` request flow

```
Client                OrderController        OrderServiceImpl        ProductClient (Feign)        product-service
  │  POST /api/orders        │                       │                        │                         │
  ├──────────────────────────►                       │                        │                         │
  │                          ├───createOrder(req)────►                        │                         │
  │                          │                       │  for each item:        │                         │
  │                          │                       ├───reserveStock(id,qty)─►                        │
  │                          │                       │                        ├──POST /api/products/{id}/reserve──►
  │                          │                       │                        │                         │  decrements stockQuantity
  │                          │                       │                        │◄────ProductDto (200)────┤  (optimistic-locked)
  │                          │                       │◄──ProductDto───────────┤                         │
  │                          │                       │  (repeat per item)     │                         │
  │                          │                       │                        │                         │
  │                          │                       │  [if a later item      │                         │
  │                          │                       │   fails: 404/409]      │                         │
  │                          │                       ├───releaseStock(...)────► (compensate each already-reserved item)
  │                          │                       │   for each reserved    │                         │
  │                          │                       │   line, then rethrow   │                         │
  │                          │                       │                        │                         │
  │                          │                       │  else: build Order +   │                         │
  │                          │                       │  OrderItems, save      │                         │
  │                          │                       │  (snapshotted name/    │                         │
  │                          │                       │   price/qty)           │                         │
  │                          │◄──OrderResponse────────┤                        │                         │
  │◄──201 Created────────────┤                       │                        │                         │
```

This is the **manual saga / compensation** teaching moment: there is no distributed transaction and no saga orchestration library. `OrderServiceImpl.createOrder` reserves stock item-by-item via sequential Feign calls; if any later item fails, it calls `releaseStock` on every item already reserved (logging a warning if even that compensating call fails) before rethrowing the original exception. This is exactly the trade-off you must reason about explicitly when integrating services over synchronous REST instead of an async event bus.

### Folder structure

```
order-product-service/                     (parent POM, packaging=pom)
├── pom.xml                                 Parent: Spring Boot 3.3.4 + Spring Cloud 2023.0.3 BOMs
├── docker-compose.yml                      MySQL + both services wired together
├── mysql-init/
│   └── 01-init-databases.sql               Creates product_db/order_db + their dedicated users
├── product-service/                        Owns the catalog (port 8081)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/.../product/
│       ├── ProductServiceApplication.java
│       ├── config/JpaAuditingConfig.java
│       ├── controller/ProductController.java
│       ├── domain/Product.java
│       ├── dto/ (ProductRequest, ProductResponse, StockChangeRequest)
│       ├── exception/ (ApiError, GlobalExceptionHandler, ...)
│       ├── repository/ProductRepository.java
│       └── service/ (ProductService, ProductServiceImpl)
└── order-service/                          Places orders, hosts the Feign client (port 8080)
    ├── pom.xml
    ├── Dockerfile
    └── src/main/java/.../order/
        ├── OrderServiceApplication.java
        ├── client/ProductClient.java       ← the OpenFeign interface
        ├── client/dto/ (ProductDto, StockChangeRequest)
        ├── config/
        │   ├── FeignClientConfig.java        ← ErrorDecoder, Retryer, Logger.Level, RequestInterceptor beans
        │   ├── FeignClientsEnablement.java    ← @EnableFeignClients (own class, see note below)
        │   ├── JpaAuditingConfig.java
        │   ├── ProductClientErrorDecoder.java
        │   └── CorrelationIdRequestInterceptor.java
        ├── controller/OrderController.java
        ├── domain/ (Order, OrderItem, OrderStatus)
        ├── dto/ (OrderRequest, OrderItemRequest, OrderResponse, OrderItemResponse)
        ├── exception/ (ApiError, GlobalExceptionHandler, ProductNotFoundException, InsufficientStockException, ProductServiceUnavailableException, ...)
        ├── repository/OrderRepository.java
        └── service/ (OrderService, OrderServiceImpl)
```

### Database design

**product_db.products**

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK, identity | |
| sku | VARCHAR(40), unique, not null | business key |
| name | VARCHAR(150), not null | |
| description | VARCHAR(500) | |
| price | DECIMAL(10,2), not null | |
| stock_quantity | INT, not null | mutated by reserve/release |
| version | BIGINT | `@Version` — optimistic locking so concurrent reserve calls fail fast instead of overselling |
| created_at / updated_at | TIMESTAMP | Spring Data JPA auditing |

**order_db.orders**

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK, identity | |
| customer_name / customer_email | VARCHAR | |
| status | VARCHAR(20) enum | PENDING / CONFIRMED / CANCELLED / FAILED |
| total_amount | DECIMAL(12,2) | sum of item subtotals |
| created_at / updated_at | TIMESTAMP | JPA auditing |

**order_db.order_items**

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK, identity | |
| order_id | FK → orders.id (same schema) | |
| product_id | BIGINT, not null | **reference only** — no FK, product lives in a different database |
| product_name | VARCHAR(150) | snapshot at reservation time |
| unit_price | DECIMAL(10,2) | snapshot at reservation time |
| quantity | INT | |

There is deliberately no foreign key from `order_items.product_id` into `product_db.products` — the two tables live in different schemas owned by different services, so `OrderItem` snapshots the name/price it received from the Feign call instead of relying on a JPA `@ManyToOne` relationship across service boundaries.

---

## 3. Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.4 |
| Inter-service HTTP client | Spring Cloud OpenFeign | Spring Cloud 2023.0.3 |
| Underlying HTTP transport | feign-okhttp | via Spring Cloud BOM |
| Persistence | Spring Data JPA / Hibernate | via Spring Boot BOM |
| Database | MySQL | 8.4 |
| Test DB | H2 (in-memory, MySQL mode) | via Spring Boot BOM |
| Validation | Jakarta Bean Validation (`spring-boot-starter-validation`) | via Spring Boot BOM |
| Boilerplate reduction | Lombok | via Spring Boot BOM |
| HTTP stub server (tests) | WireMock (`wiremock-jre8-standalone`) | 2.35.2 |
| Build tool | Maven (multi-module reactor) | — |
| Containerization | Docker / Docker Compose | multi-stage, eclipse-temurin 21 |
| Observability | Spring Boot Actuator (`health`, `info`) | via Spring Boot BOM |

---

## 4. Configuration Explained

### `order-service/src/main/resources/application.yml`

```yaml
server:
  port: 8080
```
order-service listens on 8080.

```yaml
spring:
  application:
    name: order-service
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:order_db}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${DB_USERNAME:order_user}
    password: ${DB_PASSWORD:order_pass}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      pool-name: order-service-pool
      maximum-pool-size: 10
      connection-timeout: 10000
```
- Every datasource property is overridable via env vars (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`) with **localhost / order_db / order_user** defaults for running the jar directly, and container values injected by `docker-compose.yml`.
- `createDatabaseIfNotExist=true` lets the JDBC driver create the schema if it's missing (belt-and-braces alongside `mysql-init`).
- `useSSL=false` / `allowPublicKeyRetrieval=true` avoid SSL handshake friction for a local learning MySQL instance — not something you'd do for a production database.
- HikariCP pool is named per-service (`order-service-pool`) so pool metrics/logs are unambiguous when scraping both services' logs together.

```yaml
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
    show-sql: false
```
- `ddl-auto: update` — auto-migrates the schema for a learning project; a real service would use Flyway/Liquibase instead.
- `open-in-view: false` — disables the Open Session In View anti-pattern so lazy-loading exceptions surface at the service layer, not silently inside view rendering.

```yaml
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
```
Omits null fields from JSON responses and serializes `Instant` fields as ISO-8601 strings instead of epoch arrays — this is also what makes the `ApiError`/`OrderResponse` JSON human-readable and what `ProductDto` on the Feign side expects to parse.

```yaml
product-service:
  url: ${PRODUCT_SERVICE_URL:http://localhost:8081}
  retry:
    period-ms: 100
    max-period-ms: 1000
    max-attempts: 3
```
- `product-service.url` is the placeholder resolved by `@FeignClient(url = "${product-service.url}")` in `ProductClient` — defaults to localhost for running both services side-by-side on a laptop, and is overridden to `http://product-service:8081` (the Compose network alias) by the `PRODUCT_SERVICE_URL` env var in `docker-compose.yml`.
- `retry.*` feed the `Retryer.Default(period, maxPeriod, maxAttempts)` bean in `FeignClientConfig` — retries kick in only for connection-level failures, not for successfully-received error responses (a received 409/404 is decoded, not retried).

```yaml
feign:
  okhttp:
    enabled: true
  httpclient:
    enabled: false
  client:
    config:
      default:
        connectTimeout: 3000
        readTimeout: 5000
        loggingLevel: full
      product-service:
        connectTimeout: 2000
        readTimeout: 4000
        loggingLevel: full
```
- `feign.okhttp.enabled: true` + `feign.httpclient.enabled: false` swap Feign's default (JDK `HttpURLConnection`-based) transport for OkHttp (pulled in via the `feign-okhttp` dependency) — connection pooling and HTTP/2 support come for free.
- `feign.client.config.default` sets baseline timeouts/logging for any Feign client that doesn't have its own override.
- `feign.client.config.product-service` is a **per-client override**, keyed by the `name` given to `@FeignClient(name = "product-service", ...)` — it tightens the timeout specifically for calls to product-service (2s connect / 4s read) versus the 3s/5s default, demonstrating that different downstream dependencies can get different SLAs from the same Feign infrastructure.
- `loggingLevel: full` here works together with the `feignLoggerLevel()` bean in `FeignClientConfig` (both need to agree) and the `DEBUG` logger level below to actually emit Feign's request/response logs.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```
Exposes only `/actuator/health` and `/actuator/info` (not the full actuator surface) — used by the Docker Compose healthchecks.

```yaml
logging:
  level:
    root: INFO
    com.medha.orderproductservice.order: INFO
    com.medha.orderproductservice.order.client.ProductClient: DEBUG
```
Feign only emits its request/response logs (`Logger.Level.FULL` from the `feignLoggerLevel()` bean) for loggers at `DEBUG` or below — scoping `DEBUG` to the `ProductClient` interface's own logger name is what turns that verbose HTTP tracing on, without dropping the rest of the app to DEBUG noise.

### `product-service/src/main/resources/application.yml`

```yaml
server:
  port: 8081
```
product-service listens on 8081 — the port `order-service`'s `product-service.url` points at.

Datasource / JPA / Jackson blocks mirror order-service's, but default to `product_db` / `product_user` / `product_pass`.

```yaml
  jpa:
    ...
    defer-datasource-initialization: true
  sql:
    init:
      mode: always
      platform: mysql
```
- `defer-datasource-initialization: true` makes Hibernate create the schema (`ddl-auto: update`) **before** Spring runs `data.sql`, so the seed inserts below don't fail against a table that doesn't exist yet.
- `sql.init.mode: always` runs `data.sql` on every startup (not just when using an embedded DB), and `platform: mysql` selects it as the active seed script.

`product-service` has no `feign.*` or `product-service.*` block — it is the **callee**, not a Feign consumer; it never calls order-service.

### `order-service/src/test/resources/application.yml` & `product-service/src/test/resources/application.yml`

Both swap MySQL for an in-memory H2 database in `MODE=MySQL` (so MySQL-specific SQL still behaves the same in tests) with `ddl-auto: create-drop`. order-service's test config additionally shrinks the `product-service` Feign timeouts to 500ms/1000ms so WireMock-backed tests fail fast instead of waiting on the full 2s/4s production timeouts. product-service's test config sets `sql.init.mode: never` so the `@DataJpaTest`/`@WebMvcTest` slices don't try to run the MySQL-flavoured `data.sql` seed against H2.

---

## 5. Project Structure Explained

| Path | Purpose |
|---|---|
| `pom.xml` (root) | Parent POM, `packaging=pom`. Imports `spring-boot-dependencies:3.3.4` and `spring-cloud-dependencies:2023.0.3` as BOMs in `<dependencyManagement>` so both modules get consistent, compatible versions without repeating them. Declares `<modules>product-service, order-service</modules>`. |
| `docker-compose.yml` | Boots one MySQL 8.4 container plus both service containers on a shared bridge network, with healthchecks gating startup order (`mysql` → `product-service` → `order-service`). |
| `mysql-init/01-init-databases.sql` | Runs once when the MySQL container's data volume is first created; creates `product_db`/`order_db` and their dedicated, schema-scoped users — mirrors two independently owned microservice databases. |
| `.gitignore` | Excludes Maven `target/`, IDE metadata, and OS cruft from version control. |
| `product-service/` | Owns the catalog. See its own README below. |
| `order-service/` | Places orders, hosts the OpenFeign client. See its own README below. |

---

## 6. Getting Started

### Prerequisites

- Docker and Docker Compose
- (Optional, for local `mvn` runs outside Docker) JDK 21 and Maven 3.9+

### Run everything with Docker Compose

```bash
git clone https://github.com/JNikhilSakthi/order-product-service.git
cd order-product-service

# Build images and start MySQL + both services
docker compose up --build

# ...or detached
docker compose up --build -d

# Tail logs
docker compose logs -f order-service product-service

# Stop and remove containers (keeps the mysql-data volume)
docker compose down

# Stop and also wipe the MySQL volume (fresh seed data next run)
docker compose down -v
```

Once healthy:
- product-service: `http://localhost:8081/api/products`
- order-service: `http://localhost:8080/api/orders`
- MySQL: `localhost:3306` (root password `root_pass`, from the container's perspective at `mysql:3306`)

### Run locally without Docker (two terminals)

```bash
# Terminal 1 — needs a local MySQL with product_db/order_db + users created
# (or reuse `docker compose up mysql` for just the database)
mvn -pl product-service -am spring-boot:run

# Terminal 2
mvn -pl order-service -am spring-boot:run
```
order-service defaults `product-service.url` to `http://localhost:8081`, so no extra config is needed when running both side-by-side on one machine.

---

## 7. API Documentation

### product-service — `http://localhost:8081`

| Method | Path | Description |
|---|---|---|
| GET | `/api/products` | List all products |
| GET | `/api/products/{id}` | Get one product |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |
| POST | `/api/products/{id}/reserve` | Decrement stock (called by order-service via Feign) |
| POST | `/api/products/{id}/release` | Increment stock back (called by order-service via Feign) |

**Create a product**
```
POST /api/products
Content-Type: application/json

{
  "sku": "SKU-CHAIR-01",
  "name": "Ergonomic Chair",
  "description": "Mesh-back office chair",
  "price": 249.00,
  "stockQuantity": 12
}
```
```json
201 Created
{
  "id": 6,
  "sku": "SKU-CHAIR-01",
  "name": "Ergonomic Chair",
  "description": "Mesh-back office chair",
  "price": 249.00,
  "stockQuantity": 12,
  "createdAt": "2026-07-22T10:00:00Z",
  "updatedAt": "2026-07-22T10:00:00Z"
}
```

**Reserve stock**
```
POST /api/products/1/reserve
Content-Type: application/json

{ "quantity": 2 }
```
```json
200 OK
{ "id": 1, "sku": "SKU-LAPTOP-14", "name": "ThinkPro 14\" Laptop", "description": "...", "price": 1299.99, "stockQuantity": 23, "createdAt": "...", "updatedAt": "..." }
```

**Insufficient stock**
```json
409 Conflict
{
  "timestamp": "2026-07-22T10:05:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock for product 1: requested 999 but only 23 available",
  "path": "/api/products/1/reserve",
  "fieldErrors": null
}
```

### order-service — `http://localhost:8080`

| Method | Path | Description |
|---|---|---|
| GET | `/api/orders` | List all orders |
| GET | `/api/orders/{id}` | Get one order |
| POST | `/api/orders` | Place an order (reserves stock via Feign for every item) |
| PUT | `/api/orders/{id}/cancel` | Cancel an order (releases stock via Feign for every item) |

**Place an order**
```
POST /api/orders
Content-Type: application/json

{
  "customerName": "Alice",
  "customerEmail": "alice@example.com",
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```
```json
201 Created
{
  "id": 1,
  "customerName": "Alice",
  "customerEmail": "alice@example.com",
  "status": "CONFIRMED",
  "totalAmount": 2625.97,
  "items": [
    { "productId": 1, "productName": "ThinkPro 14\" Laptop", "unitPrice": 1299.99, "quantity": 2, "subtotal": 2599.98 },
    { "productId": 2, "productName": "Wireless Ergo Mouse", "unitPrice": 29.99, "quantity": 1, "subtotal": 29.99 }
  ],
  "createdAt": "2026-07-22T10:10:00Z",
  "updatedAt": "2026-07-22T10:10:00Z"
}
```

**Order with a bad product in the middle of the list** — the first item's stock reservation is compensated (released) before the 422 is returned:
```json
422 Unprocessable Entity
{
  "timestamp": "2026-07-22T10:12:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Product not found with id: 999",
  "path": "/api/orders",
  "fieldErrors": null
}
```

**Cancel an order**
```
PUT /api/orders/1/cancel
```
```json
200 OK
{ "id": 1, "customerName": "Alice", "customerEmail": "alice@example.com", "status": "CANCELLED", "totalAmount": 2625.97, "items": [...], "createdAt": "...", "updatedAt": "..." }
```

**Validation error (missing/invalid fields)**
```json
400 Bad Request
{
  "timestamp": "2026-07-22T10:13:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/orders",
  "fieldErrors": {
    "customerName": "customerName is required",
    "customerEmail": "customerEmail must be a valid email address",
    "items": "order must contain at least one item"
  }
}
```

order-service's `GlobalExceptionHandler` maps `ProductNotFoundException` → 422, `InsufficientStockException`/`InvalidOrderStateException` → 409, `ProductServiceUnavailableException` → 503, `OrderNotFoundException` → 404, and validation failures → 400 — this is the layer that turns the Feign `ErrorDecoder`'s typed exceptions into consistent HTTP responses for order-service's own API consumers.

---

## 8. Testing

31 tests total, run via JDK 21 with `mvn clean verify` from the repo root (runs both modules in the reactor).

```bash
mvn clean verify
# or per module
mvn -pl product-service -am test
mvn -pl order-service -am test
```

**product-service (14 tests)**
- `ProductServiceImplTest` — Mockito unit tests for create/get/update/delete/reserveStock/releaseStock, including the duplicate-SKU and insufficient-stock failure paths.
- `ProductControllerTest` (`@WebMvcTest`) — web-layer slice tests asserting status codes and JSON shape for 200/201/400/409 responses, with `ProductService` mocked.
- `ProductRepositoryTest` (`@DataJpaTest`) — verifies `findBySku`/`existsBySku` and that auditing (`createdAt`/`updatedAt`) and the `@Version` column populate on save.

**order-service (17 tests)**
- `OrderServiceImplTest` — Mockito unit tests, including the two compensation-on-failure scenarios: a later item hitting `InsufficientStockException` and a later item hitting `ProductNotFoundException`, both asserting `releaseStock` is called for exactly the already-reserved items and that nothing is persisted.
- `OrderControllerTest` (`@WebMvcTest`) — web-layer slice tests for 201/400/409/503/404/200 responses, with `OrderService` mocked.
- `ProductClientIntegrationTest` (`@SpringBootTest`, WireMock-backed) — the test that proves OpenFeign itself works end-to-end: starts a real WireMock HTTP server, points `product-service.url` at it via `@DynamicPropertySource`, and drives the **real Spring-generated Feign proxy** to verify JSON deserialization, the `X-Correlation-Id` header injection, and that 404/409/500 responses are correctly decoded into `ProductNotFoundException`/`InsufficientStockException`/`ProductServiceUnavailableException`.

The WireMock test is the one a Mockito-mocked `ProductClient` elsewhere in the suite cannot substitute for — it is the only test that actually exercises Feign's contract processing, JSON codec, error decoder and interceptor together over real HTTP.

---

## 9. Docker

### Dockerfiles (`product-service/Dockerfile`, `order-service/Dockerfile`)

Both are identical two-stage builds:
1. **Build stage** (`eclipse-temurin:21-jdk-jammy`) — copies the root `pom.xml` and both modules' `pom.xml` files first (so `dependency:go-offline` can populate a cached `.m2` layer before any source changes invalidate it), then copies that module's `src/`, then runs `mvn package` for just that module (`-pl <module> -am`) against the reactor.
2. **Run stage** (`eclipse-temurin:21-jre-jammy`) — installs `curl` (needed for the Compose healthcheck), creates a non-root `spring` user, copies only the built jar from the build stage, and runs it as an unprivileged user.

`--mount=type=cache,target=/root/.m2` reuses the Maven local repository cache across builds instead of re-downloading dependencies every time.

### docker-compose.yml

- **mysql** — MySQL 8.4, seeded by `mysql-init/01-init-databases.sql` on first boot, with a `mysqladmin ping` healthcheck and a persistent `mysql-data` volume.
- **product-service** — built from `product-service/Dockerfile`, waits for `mysql` to be `service_healthy`, exposes 8081, healthchecked via `curl -f http://localhost:8081/actuator/health`.
- **order-service** — built from `order-service/Dockerfile`, waits for **both** `mysql` and `product-service` to be healthy before starting, exposes 8080. Its one Compose-specific environment override is:
  ```yaml
  PRODUCT_SERVICE_URL: http://product-service:8081
  ```
  which is what lets the OpenFeign client resolve product-service by its Compose network alias instead of `localhost` — the same `ProductClient` interface, same code, just a different `product-service.url` value injected at runtime.
- All three containers share the `openfeign-demo-net` bridge network so they can address each other by service name.

---

## 10. Interview Preparation

**Q: What does `@FeignClient` actually do at startup?**
Spring Cloud OpenFeign scans for `@FeignClient`-annotated interfaces (enabled via `@EnableFeignClients`), and for each one generates a dynamic proxy implementation using JDK dynamic proxies. Calling `productClient.getProductById(1L)` triggers Feign to build an HTTP request from the method's annotations (`@GetMapping`, `@PathVariable`), encode it, send it through the configured `Client` (here, OkHttp), decode the response body into `ProductDto` via the configured `Decoder` (Jackson), and hand it back as a normal return value — or throw a decoded exception.

**Q: Why put `@EnableFeignClients` and `@EnableJpaAuditing` on their own `@Configuration` classes instead of the `@SpringBootApplication` class?**
Because `@WebMvcTest` slice tests build their `ApplicationContext` from the class annotated `@SpringBootConfiguration` (which `@SpringBootApplication` is a meta-annotation for) regardless of which controller you're slicing to — any annotation on that root class, including `@EnableFeignClients`, gets processed even in a narrow web-layer test. This project actually hit that failure during verification: a `@WebMvcTest` tried to build Feign infrastructure (and needed `product-service.url` resolved) purely because `@EnableFeignClients` lived on the application class. Moving it (and `@EnableJpaAuditing`) into small standalone `@Configuration` classes outside the application class's direct annotations keeps slice tests scoped to just what they're testing.

**Q: Why is there no shared Java module for the two services' DTOs?**
Because in a real deployment, `order-service` and `product-service` are versioned, deployed and possibly even owned by different teams independently. A shared JAR would couple their release cadences. The contract is the JSON wire shape only — each service defines its own DTO (`ProductDto` in order-service vs. `ProductResponse` in product-service) that happens to match that shape, exactly as you'd integrate with a third-party API you don't control.

**Q: How does the error decoding work?**
`FeignClientConfig` registers a custom `ErrorDecoder` (`ProductClientErrorDecoder`) that inspects the HTTP status of a failed response, attempts to parse product-service's `ApiError` JSON body for a `message`, and returns a typed exception: 404 → `ProductNotFoundException`, 409 → `InsufficientStockException`, 5xx → `ProductServiceUnavailableException`, everything else falls back to Feign's `ErrorDecoder.Default`. Parsing is defensive — a malformed or missing body never masks the original failure, it just falls back to the raw body or HTTP reason phrase.

**Q: What's the difference between retrying at the Feign transport level vs. retrying the whole business operation?**
The `Retryer` bean here (`Retryer.Default`) only retries **connection-level failures** (e.g. the socket couldn't connect, a timeout occurred before any response was received) — not successfully-received error responses like a 409. This matters because `reserveStock` is not idempotent: blindly retrying a *successful* reservation call that merely returned slowly could double-decrement stock. Retrying only pre-response failures avoids that risk while still smoothing over transient network blips.

**Q: What is the "manual saga" and why not just use `@Transactional` across both services?**
A single `@Transactional` cannot span two separate databases behind two separate services — that would require distributed transactions (2PC), which don't compose with independently deployable services or scale well. Instead, `OrderServiceImpl.createOrder` performs sequential Feign calls and manually compensates (calls `releaseStock`) on already-reserved items if a later item fails, then rethrows. This is a **best-effort compensating transaction** (the "Saga pattern" done by hand, without an orchestration framework or event bus) — if the compensating call itself fails, it's logged as needing manual reconciliation rather than silently swallowed.

**Q: Common mistakes with OpenFeign in production**
- Forgetting to set explicit timeouts — the Feign/OkHttp defaults can hang far longer than acceptable, cascading a slow downstream into your own thread pool exhaustion.
- Blindly enabling retries on non-idempotent POST operations, causing duplicate side effects downstream (over-reservation, duplicate charges).
- Leaving `Logger.Level.FULL` enabled in production, which logs full request/response bodies (potentially including PII) at high volume.
- Not propagating a correlation/trace ID (`CorrelationIdRequestInterceptor` here), making cross-service debugging via logs nearly impossible.
- Sharing a DTO module between services, quietly re-coupling their deploy cycles.

**Q: Performance considerations**
- `feign-okhttp` gives connection pooling and keep-alive across calls to the same host, which matters a lot when order-service calls product-service once per line item — without pooling that's a new TCP+TLS handshake per item.
- Per-client timeout overrides (`feign.client.config.product-service`) let you tune SLAs per downstream instead of one global timeout that's either too tight for a slow dependency or too loose for a fast one.
- Sequential per-item Feign calls in `createOrder` are simple and easy to compensate, but not the most latency-efficient approach for large orders — a batch "reserve multiple" endpoint would reduce round-trips at the cost of a more complex API contract and compensation logic.

---

## License

MIT — see [LICENSE](./LICENSE).
