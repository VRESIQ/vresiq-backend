# VResIQ — Spring Boot Backend API

[![Live Demo](https://img.shields.io/badge/%F0%9F%8C%90_Live-Website-blue?style=for-the-badge)](https://vresiq.app)
[![Frontend Repository](https://img.shields.io/badge/%F0%9F%92%BB_Frontend-Repository-blue?style=for-the-badge)](https://github.com/vresiq/vresiq-frontend)
[![License](https://img.shields.io/badge/%F0%9F%93%9C_License-MIT-green?style=for-the-badge)](https://github.com/vresiq/vresiq-backend/blob/main/LICENSE)

The Java/Spring Boot REST API backend engine powering VResIQ, handling database storage, authentication scopes, webhook handling, and PDF printing contexts.

---

## Core Server Features

- **JWT Security Scope**: Managed via Spring Security filters validating tokens on every incoming header, supporting seamless OAuth lifecycle rotation.
- **Fail-Closed Razorpay Webhook**: Hardened webhook payment credits system which rejects unsigned payloads and enforces fail-closed policies in production when secrets are misconfigured.
- **Puppeteer-powered PDF Exporter**: Spins up headless Chromium containers to rasterize vectors, matching CSS margins and layout sizes exactly.
- **Cascading Profile Erasures**: Cascades purging of MongoDB user documents, payment configurations, and Cloudinary image assets to support privacy compliance.

---

## Tech Stack

* **Core**: Java 21, Spring Boot 3.x, Spring Data MongoDB
* **Security**: Spring Security, JWT (Json Web Token)
* **Integrations**: Razorpay SDK, Cloudinary Image SDK

---

## Setup & Running

1. **Build Jar**:
   ```bash
   mvn clean package -DskipTests
   ```
2. **Launch API Application Server**:
   ```bash
   mvn spring-boot:run
   ```

---

## Environment Variables Configuration

Configure the application variables inside `src/main/resources/application.properties` or environment:
```properties
SPRING_DATA_MONGODB_URI=mongodb+srv://...
JWT_SECRET=your_jwt_signing_key_here
CLOUDINARY_URL=cloudinary://...
RAZORPAY_KEY_ID=rzp_live_...
RAZORPAY_KEY_SECRET=razorpay_secret_key
RAZORPAY_WEBHOOK_SECRET=razorpay_webhook_secret_key
```
