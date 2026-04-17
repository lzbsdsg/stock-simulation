# syntax=docker/dockerfile:1.7

# ============================================================
# Stage 1: Backend Build (Spring Boot)
# ============================================================
FROM eclipse-temurin:17-jdk-jammy AS backend-builder
WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw package -DskipTests -B

# ============================================================
# Stage 2: Backend Runtime
# ============================================================
FROM eclipse-temurin:17-jre-jammy AS backend-runtime
WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

RUN groupadd -r app && useradd -r -g app app
COPY --from=backend-builder /workspace/target/*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8080 8081

USER app
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

# ============================================================
# Stage 3: Frontend Build (Vue + Vite)
# ============================================================
FROM node:20-alpine AS frontend-builder
WORKDIR /web

COPY stock-simulation-web/package.json stock-simulation-web/pnpm-lock.yaml ./
RUN corepack enable && corepack prepare pnpm@10.4.1 --activate && pnpm install --frozen-lockfile

COPY stock-simulation-web .
RUN pnpm build

# ============================================================
# Stage 4: Frontend Nginx Runtime
# ============================================================
FROM nginx:1.27-alpine AS frontend-nginx
RUN apk add --no-cache openssl \
		&& mkdir -p /etc/nginx/certs \
		&& openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
			-keyout /etc/nginx/certs/privkey.pem \
			-out /etc/nginx/certs/fullchain.pem \
			-subj "/CN=localhost"
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=frontend-builder /web/dist /usr/share/nginx/html

EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
