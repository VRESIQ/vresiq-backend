# VRESIQ Backend — Production Deployment Guide

This document describes how to deploy the VRESIQ Spring Boot backend application to production environments like **Render**, **Heroku**, **Docker**, or direct self-managed Linux virtual private servers (VPS).

---

## ⚡ Deployment Options

### Option A: Render (Recommended Cloud Host)
Render is a developer-friendly cloud platform that supports automatic Spring Boot builds from Git.

1. Create a new **Web Service** on Render and link your VRESIQ Backend Git repository.
2. Select the following configuration:
   - **Runtime**: `Docker` (Render automatically builds the Dockerfile) OR **Java** runtime.
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java -jar target/resumebuilderapi-0.0.1-SNAPSHOT.jar`
3. Add the required environment variables in the Render Service Dashboard under the **Environment** tab:
   - `JWT_SECRET` (generate a strong base64 key)
   - `MONGO_URI` (MongoDB Atlas production cluster link)
   - `SMTP_USER` & `SMTP_PASS` (Brevo credentials)
   - `CLOUDINARY_URL` (Asset uploads credentials)
   - `RAZORPAY_KEY_ID` & `RAZORPAY_KEY_SECRET` (Payment triggers keys)
   - `GEMINI_API_KEY` (AI model access key)
   - `MAIL_FROM` = `admin@vresiq.app`
4. Deploy the service.

---

### Option B: Docker Container Deployment
Deploying with Docker guarantees environment uniformity across any virtual machine.

1. Ensure a `Dockerfile` exists at the root of the backend repository:
   ```dockerfile
   # Stage 1: Build stage
   FROM maven:3.9.6-eclipse-temurin-21 AS build
   WORKDIR /app
   COPY . .
   RUN mvn clean package -DskipTests

   # Stage 2: Production run stage
   FROM eclipse-temurin:21-jre-jammy
   WORKDIR /app
   COPY --from=build /app/target/*.jar app.jar
   EXPOSE 8081
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

2. Build and run the image locally:
   ```bash
   docker build -t vresiq-backend .
   docker run -p 8081:8081 --env-file .env vresiq-backend
   ```

---

### Option C: Systemd Service on Linux VPS (Ubuntu/Debian)
For direct deployments on dedicated Linux servers:

1. Install JDK 21:
   ```bash
   sudo apt update
   sudo apt install openjdk-21-jdk -y
   ```
2. Build the JAR file on your local machine and transfer the target JAR file to the server:
   ```bash
   scp target/resumebuilderapi-0.0.1-SNAPSHOT.jar user@vps-ip:/var/www/vresiq-backend/
   ```
3. Set up a secure Systemd service file `/etc/systemd/system/vresiq-backend.service`:
   ```ini
   [Unit]
   Description=VRESIQ Spring Boot Backend API
   After=network.target

   [Service]
   User=vresiq
   WorkingDirectory=/var/www/vresiq-backend
   ExecStart=/usr/bin/java -jar resumebuilderapi-0.0.1-SNAPSHOT.jar
   SuccessExitStatus=143
   TimeoutStopSec=10
   Restart=on-failure
   RestartSec=5

   # Environment variables
   Environment=JWT_SECRET=your_jwt_secret
   Environment=MONGO_URI=mongodb://localhost:27017/vresiq
   Environment=SMTP_HOST=smtp-relay.brevo.com
   Environment=SMTP_PORT=587
   Environment=SMTP_USER=smtp_username
   Environment=SMTP_PASS=smtp_password
   Environment=CLOUDINARY_URL=cloudinary_url
   Environment=RAZORPAY_KEY_ID=razorpay_key
   Environment=RAZORPAY_KEY_SECRET=razorpay_secret
   Environment=GEMINI_API_KEY=gemini_key
   Environment=MAIL_FROM=admin@vresiq.app

   [Install]
   WantedBy=multi-user.target
   ```
4. Enable and start the background API service:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable vresiq-backend.service
   sudo systemctl start vresiq-backend.service
   ```
5. Check status logs:
   ```bash
   journalctl -u vresiq-backend.service -n 50 -f
   ```

---

## 🔒 Post-Deployment Checks
- Check the console logs on startup to ensure that **Database Seeder** successfully ran and logged seeded credentials.
- Verify that standard endpoints respond appropriately (e.g. `GET /api/resumes` should block with a `401 Unauthorized` response when requested without a JWT header).
