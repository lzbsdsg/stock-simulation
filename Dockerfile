# ============================================================
# Stage 1: Build
# ============================================================
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# 非 root 用户
RUN groupadd -r app && useradd -r -g app app

COPY --from=builder /build/target/*.jar app.jar

# JVM 参数（根据容器 memory limit 自动调整）
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# Prometheus metrics 端口
EXPOSE 8080
EXPOSE 8081

USER app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
