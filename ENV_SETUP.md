# VRESIQ Backend — Environment Setup Guide

This document covers all details regarding the backend environment configuration parameters, security considerations, and local credentials.

---

## 🔑 Environment Variables Overview

Spring Boot maps standard system environment variables directly to its properties on startup. Below is the master list of all required environment variables:

| Variable Name | Required | Default/Example Value | Description |
| :--- | :---: | :--- | :--- |
| `JWT_SECRET` | **Yes** | `dnhFcThkMk45bEpXcjN...` | A secure, high-entropy Base64-encoded string used to sign and verify JWT authorization tokens. |
| `MONGO_URI` | **Yes** | `mongodb://localhost:27017/vresiq` | Connection URI pointing to the MongoDB instance or cluster. |
| `SMTP_HOST` | **Yes** | `smtp-relay.brevo.com` | Outbound mail server hostname. |
| `SMTP_PORT` | **Yes** | `587` | Outbound mail server SMTP port (typically 587 for TLS). |
| `SMTP_USER` | **Yes** | `user@smtp.brevo.com` | Brevo SMTP account email or username. |
| `SMTP_PASS` | **Yes** | `xsmtpsib-...` | Brevo SMTP authorization master password or API key. |
| `CLOUDINARY_URL` | **Yes** | `cloudinary://key:sec@cloud` | Connection URL for storing user profile avatars securely on the cloud. |
| `RAZORPAY_KEY_ID` | **Yes** | `rzp_test_your_key` | Razorpay public key ID. |
| `RAZORPAY_KEY_SECRET`| **Yes** | `your_razorpay_secret` | Razorpay private API key secret. |
| `GEMINI_API_KEY` | **Yes** | `sk-or-v1-your_openrouter_key`| OpenAI-compatible endpoint key (OpenRouter/Gemini API key) for AI text updates. |
| `MAIL_FROM` | No | `admin@vresiq.app` | Outbound sender address visible in transactional verification emails. |

---

## 🛠️ Step-by-Step Local Configuration

1. **Locate Properties File**:
   Locate `resume-builder-backend/src/main/resources/application.properties`. It contains configuration properties that resolve variables using `${ENV_VAR:default_value}` structures.

2. **Setup Local Environment Variables**:
   In local development, you can set these variables in your OS, shell, or run profile.
   
   - **For PowerShell (Local Session)**:
     ```powershell
     $env:JWT_SECRET="generate_a_secure_base64_string"
     $env:MONGO_URI="mongodb://localhost:27017/vresiq"
     $env:SMTP_HOST="smtp-relay.brevo.com"
     $env:SMTP_PORT="587"
     $env:SMTP_USER="your_brevo_user"
     $env:SMTP_PASS="your_brevo_password"
     $env:CLOUDINARY_URL="cloudinary://your_cloudinary_url"
     $env:RAZORPAY_KEY_ID="rzp_test_key"
     $env:RAZORPAY_KEY_SECRET="rzp_secret"
     $env:GEMINI_API_KEY="your_openrouter_api_key"
     $env:MAIL_FROM="admin@vresiq.app"
     ```

   - **For Git-Ignored `.env` (Optional IDE plugins)**:
     If using an IDE env-file loader plugin, you can create a local git-ignored `.env` inside the `resume-builder-backend/` directory.

3. **Verify Protection**:
   The backend `.gitignore` file includes `.env` and compiled classes by default. Ensure your environment configurations are never staged or tracked by running:
   ```bash
   git status
   ```

---

## 🔒 Security Best Practices
- **JWT Entropy**: Ensure the `JWT_SECRET` key is at least 256 bits (32 bytes) long to prevent brute-force signature forging.
- **Strict Rate Limiting**: Ensure that the `GEMINI_API_KEY` is rate-limited (automatically tracked via `UserAiStats` database documents) to prevent excessive billing in case of API abuse.
- **Credential Rotation**: Check all dashboards regularly to ensure no development keys are committed to third-party tracking, and rotate SMTP/Razorpay keys on a bi-annual basis.
