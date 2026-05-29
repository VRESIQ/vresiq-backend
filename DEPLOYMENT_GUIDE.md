# VRESIQ Backend Production Deployment Guide

This guide is aligned to runtime code in `src/main/resources/application.properties`.

## Deployment Runtime
1. Build artifact:
   - `./mvnw clean package -DskipTests`
2. Start artifact:
   - `java -jar target/resumebuilderapi-0.0.1-SNAPSHOT.jar`

## Required Production Environment Variables
Set these before starting the backend:

1. `MONGO_URI`
2. `JWT_SECRET`
3. `FRONTEND_URL` (exact trusted frontend origin, comma-separated if multiple)
4. `RAZORPAY_KEY_ID`
5. `RAZORPAY_KEY_SECRET`
6. `CLOUD_NAME`
7. `CLOUD_KEY`
8. `CLOUD_SECRET`
9. One mail username variable:
   - `VRESIQ_MAIL_USERNAME` or `MAIL_USERNAME`
10. One mail password variable:
   - `VRESIQ_MAIL_PASSWORD` or `MAIL_PASSWORD`

Optional but recommended:
1. `MAIL_FROM`
2. `AI_API_KEY` and/or `GEMINI_API_KEY`
3. `SEED_ADMIN_EMAIL`
4. `SEED_ADMIN_PASSWORD`

## Render (Java Runtime)
1. Create a Web Service from `VRESIQ/vresiq-backend`.
2. Use:
   - Build command: `./mvnw clean package -DskipTests`
   - Start command: `java -jar target/resumebuilderapi-0.0.1-SNAPSHOT.jar`
3. Add all required environment variables above.
4. Deploy and verify health endpoint:
   - `GET /actuator/health` returns `{"status":"UP"}`.

## Linux Systemd Deployment
Use a service unit and set environment values with production values only.

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

Environment=MONGO_URI=mongodb+srv://<db_user>:<db_password>@<atlas_cluster>/<prod_db>?retryWrites=true&w=majority&appName=<app_name>
Environment=JWT_SECRET=<high_entropy_secret>
Environment=FRONTEND_URL=https://app.vresiq.com
Environment=RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxxxxx
Environment=RAZORPAY_KEY_SECRET=<razorpay_live_secret>
Environment=CLOUD_NAME=<cloudinary_cloud_name>
Environment=CLOUD_KEY=<cloudinary_api_key>
Environment=CLOUD_SECRET=<cloudinary_api_secret>
Environment=VRESIQ_MAIL_USERNAME=<smtp_username>
Environment=VRESIQ_MAIL_PASSWORD=<smtp_password_or_api_key>
Environment=MAIL_FROM=noreply@vresiq.com
Environment=AI_API_KEY=<provider_api_key>

[Install]
WantedBy=multi-user.target
```

## Post-Deployment Validation
1. Verify unauthorized protection:
   - `GET /api/resumes` without JWT returns `401 Unauthorized`.
2. Verify CORS:
   - Request with `Origin: https://app.vresiq.com` succeeds.
3. Verify payment flow:
   - `POST /api/payment/create-order` then `POST /api/payment/verify`.
4. Verify Cloudinary upload path through profile/image flow.
