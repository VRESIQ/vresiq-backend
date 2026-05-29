# VRESIQ Backend — Project Structure

This document outlines the Java package architecture, configuration files, and REST endpoint controllers structure of the VRESIQ Spring Boot backend application.

---

## 📂 Logical Directory Tree

```text
vresiq-backend/
├── src/
│   ├── main/
│   │   ├── java/in/rithik/resumebuilderapi/
│   │   │   ├── config/             # Spring beans, security filter chains, and database seeder configs
│   │   │   │   ├── AppConfig.jsx   # General app configs (Password encoders, CORS mappings)
│   │   │   │   ├── DatabaseSeeder.java # Auto-seeds Admin & Premium test credentials securely
│   │   │   │   └── SecurityConfig.java # Custom stateless Spring Security filter chains
│   │   │   ├── controller/         # REST Controllers exposing HTTP endpoints to the React frontend
│   │   │   │   ├── AdminController.java  # User management and site stats endpoints (Admin-only)
│   │   │   │   ├── AiController.java     # AI rewrite, tonality, and ATS optimization endpoints
│   │   │   │   ├── AuthController.java   # Signup, login, verification, and session validation
│   │   │   │   ├── EmailController.java  # Outgoing custom email triggers
│   │   │   │   ├── PaymentController.java # Razorpay order creation and payment verification
│   │   │   │   ├── ResumeController.java # CRUD endpoints for resume sheets management
│   │   │   │   └── VerifyController.java # Token-based email validation endpoint
│   │   │   ├── document/           # MongoDB Document schemas
│   │   │   │   ├── AiRewriteCache.java # Bullet rewrite prompt cache schema
│   │   │   │   ├── Resume.java         # Master resume schema (Structured experience, education, etc.)
│   │   │   │   ├── User.java           # User profile schema with subscription markers
│   │   │   │   └── UserAiStats.java    # AI rate-limiting usage tracker schema
│   │   │   ├── dto/                # Data Transfer Objects (Payload models)
│   │   │   │   ├── AuthResponse.java
│   │   │   │   ├── CreateResumeRequest.java
│   │   │   │   ├── RefineResponse.java
│   │   │   │   └── RefineSuggestion.java
│   │   │   ├── exception/          # Global Exception controller advices
│   │   │   │   └── GlobalExceptionHandler.java # Uniform standard JSON format error payload
│   │   │   ├── repository/         # Spring Data MongoDB Repository interfaces
│   │   │   │   ├── AiRewriteCacheRepository.java
│   │   │   │   ├── PaymentRepository.java
│   │   │   │   ├── ResumeRepository.java
│   │   │   │   ├── UserAiStatsRepository.java
│   │   │   │   └── UserRepository.java
│   │   │   ├── security/           # Low-level JWT validation filters and interceptors
│   │   │   │   ├── JwtAuthenticationFilter.java # Intercepts headers and builds Security Context
│   │   │   │   └── RateLimitingFilter.java      # Basic security limits filters
│   │   │   ├── service/            # Core business logic handlers (Layer decoupling)
│   │   │   │   ├── AdminService.java
│   │   │   │   ├── AiService.java
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── FileUploadService.java  # Cloudinary file uploading service
│   │   │   │   ├── PaymentService.java
│   │   │   │   └── ResumeService.java
│   │   │   └── util/               # Cryptographic and JWT utilities
│   │   │       └── JwtUtil.java    # Tokens encoder/decoder helper
│   │   └── resources/
│   │       ├── application.properties # Configurations (Port mapping, Database URI, SMTP channels)
│   │       └── templates/          # Transactional email HTML bodies
│   └── test/                       # Core JUnit and Spring Boot integration tests
├── pom.xml                         # Apache Maven Dependency configuration XML
└── mvnw / mvnw.cmd                 # Linux and Windows shell execution wrappers
```

---

## 🏗️ Layer decoupling model

The codebase follows the strict Service-Oriented Model:
1. **HTTP Routing**: `Controller` receives incoming JSON payload from frontend, validates payload schemas, and delegates execution immediately to the `Service` layer.
2. **Business Operations**: `Service` implements the actual core domain operations (validations, calling third party services like Cloudinary, Razorpay or OpenAI-compatible endpoint, making DB changes).
3. **Storage Mapping**: `Repository` performs data mutations against MongoDB collections mapped as `Document` schemas.
4. **Data Containers**: `DTO` (Data Transfer Objects) are utilized strictly for receiving HTTP payloads, maintaining clear structural decoupling from DB schemas.
