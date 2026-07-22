# --- Build stage ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY product-service/pom.xml /workspace/product-service/pom.xml
COPY order-service/pom.xml /workspace/order-service/pom.xml

# Warm the dependency cache for the reactor before copying sources
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -f /workspace/pom.xml -pl order-service -am -DskipTests dependency:go-offline || true

COPY order-service/src /workspace/order-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -f /workspace/pom.xml -pl order-service -am -DskipTests package

# --- Run stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build /workspace/order-service/target/order-service.jar /app/order-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/order-service.jar"]
