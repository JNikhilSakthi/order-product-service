# --- Build stage ---
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY product-service/pom.xml /workspace/product-service/pom.xml
COPY order-service/pom.xml /workspace/order-service/pom.xml

# Warm the dependency cache for the reactor before copying sources
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -f /workspace/pom.xml -pl product-service -am -DskipTests dependency:go-offline || true

COPY product-service/src /workspace/product-service/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -f /workspace/pom.xml -pl product-service -am -DskipTests package

# --- Run stage ---
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

COPY --from=build /workspace/product-service/target/product-service.jar /app/product-service.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/product-service.jar"]
