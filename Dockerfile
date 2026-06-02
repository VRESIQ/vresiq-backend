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


FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    nodejs \
    npm \
    ca-certificates \
    fonts-liberation \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libc6 \
    libcairo2 \
    libcups2 \
    libdbus-1-3 \
    libexpat1 \
    libfontconfig1 \
    libgbm1 \
    libgcc-s1 \
    libglib2.0-0 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libpango-1.0-0 \
    libpangocairo-1.0-0 \
    libstdc++6 \
    libx11-6 \
    libx11-xcb1 \
    libxcb1 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxext6 \
    libxfixes3 \
    libxi6 \
    libxrandr2 \
    libxrender1 \
    libxss1 \
    libxtst6 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

COPY --from=java-build /app/target/resumebuilderapi-0.0.1-SNAPSHOT.jar /app/app.jar
COPY pdf-generator.js package.json package-lock.json /app/
COPY --from=node-deps /app/node_modules /app/node_modules
COPY --from=node-deps /app/.cache /app/.cache

ENV NODE_ENV=production
ENV PUPPETEER_CACHE_DIR=/app/.cache/puppeteer

EXPOSE 8081

CMD ["java", "-jar", "/app/app.jar"]
