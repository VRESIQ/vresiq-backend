# VResIQ Backend

[![Root Docs](https://img.shields.io/badge/Root%20Docs-Read%20me-111827?style=for-the-badge)](../README.md)
[![Frontend](https://img.shields.io/badge/Frontend-React%20Client-61DAFB?style=for-the-badge)](../vresiq-frontend/README.md)
[![License](https://img.shields.io/badge/License-MIT-111827?style=for-the-badge)](../LICENSE)

Spring Boot API for VResIQ.

## What It Does

- Handles authentication and authorization
- Stores resumes and user data in MongoDB
- Integrates with Cloudinary, email delivery, and Razorpay
- Exports resumes to PDF through a server-side rendering flow

## Tech Stack

| Area | Stack |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 3 |
| Security | Spring Security, JWT |
| Data | Spring Data MongoDB |
| Integrations | Cloudinary, Razorpay, SMTP |

## Setup

```bash
./mvnw spring-boot:run
```

## Environment

Configure the values in `src/main/resources/application.properties` or via environment variables.

```properties
MONGO_URI=mongodb+srv://...
JWT_SECRET=your_jwt_signing_key_here
CLOUD_NAME=...
CLOUD_KEY=...
CLOUD_SECRET=...
RAZORPAY_KEY_ID=...
RAZORPAY_KEY_SECRET=...
RAZORPAY_WEBHOOK_SECRET=...
```

## Deployment

See [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) and [FINAL_DEPLOYMENT_GUIDE.md](./FINAL_DEPLOYMENT_GUIDE.md).

