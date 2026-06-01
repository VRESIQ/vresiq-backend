FROM node:20-bookworm-slim AS node-deps
WORKDIR /app

COPY package.json package-lock.json ./
# Keep Puppeteer browser download inside the project so it can be copied to runtime.
ENV PUPPETEER_CACHE_DIR=/app/.cache/puppeteer
RUN npm ci --omit=dev


FROM maven:3.9.9-eclipse-temurin-21 AS java-build
WORKDIR /app

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw mvnw
COPY src src
RUN chmod +x mvnw && ./mvnw clean package -DskipTests


FROM eclipse-temurin:21-jre-bookworm AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium \
    nodejs \
    npm \
    fonts-liberation \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY --from=java-build /app/target/resumebuilderapi-0.0.1-SNAPSHOT.jar /app/app.jar
COPY pdf-generator.js package.json package-lock.json /app/
COPY --from=node-deps /app/node_modules /app/node_modules
COPY --from=node-deps /app/.cache /app/.cache

ENV NODE_ENV=production
ENV PUPPETEER_CACHE_DIR=/app/.cache/puppeteer
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium

EXPOSE 8081

CMD ["java", "-jar", "/app/app.jar"]
