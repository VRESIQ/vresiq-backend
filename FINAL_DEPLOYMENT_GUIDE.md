# FINAL_DEPLOYMENT_GUIDE

Date: 2026-05-30
Scope: Release candidate deployment package only (no app logic changes)

## 1) Prepare Production Environment Variables
1. Copy `.env.production.example` to `.env` on the deployment host.
2. Replace all placeholders with production values.
3. Set required backend variables:
   - `MONGO_URI` (Atlas SRV URI)
   - `JWT_SECRET`
   - `FRONTEND_URL` (exact trusted frontend origin, comma-separated if multiple)
   - `RAZORPAY_KEY_ID`
   - `RAZORPAY_KEY_SECRET`
   - `CLOUD_NAME`
   - `CLOUD_KEY`
   - `CLOUD_SECRET`
   - mail username: `VRESIQ_MAIL_USERNAME` or `MAIL_USERNAME`
   - mail password: `VRESIQ_MAIL_PASSWORD` or `MAIL_PASSWORD`
4. Set optional variables as needed:
   - `MAIL_FROM`
   - `AI_API_KEY` and/or `GEMINI_API_KEY`
   - `SEED_ADMIN_EMAIL`
   - `SEED_ADMIN_PASSWORD`

## 2) Create MongoDB Atlas Production Database
1. Create a dedicated production project/cluster in Atlas.
2. Create a least-privilege DB user for production DB only.
3. Add deployment egress IP or private endpoint.
4. Put full Atlas SRV URI in `MONGO_URI`.

## 3) Configure Production Frontend URL and CORS
1. Set `FRONTEND_URL=https://app.vresiq.com` (or your exact production frontend origin).
2. For multiple trusted origins, set comma-separated origins in `FRONTEND_URL`.
3. Keep CORS restricted to trusted origins only (no wildcard).
4. Validate preflight with `Origin: https://app.vresiq.com`.

## 4) Razorpay Verification Before Live Cutover
1. Use Razorpay test keys for pre-prod verification.
2. Run purchase flow from frontend and verify backend endpoints:
   - `POST /api/payment/create-order`
   - `POST /api/payment/verify`
3. Confirm payment is stored as `paid` and user plan upgrades.
4. Switch to Razorpay live keys only at go-live.

## 5) Backend Deployment Steps
1. Build backend artifact:
   - `./mvnw clean package -DskipTests`
2. Run backend:
   - `java -jar target/resumebuilderapi-0.0.1-SNAPSHOT.jar`
3. Health check:
   - `GET /actuator/health` returns `{"status":"UP"}`.

## 6) Frontend Deployment Requirements
1. Set frontend env variables:
   - `VITE_API_BASE_URL` (production backend URL)
   - `VITE_RAZORPAY_KEY_ID` (matching environment key)
   - `VITE_OPENROUTER_API_KEY`
2. Deploy frontend and verify API calls target production backend.

## 7) Pre-Deploy Exit Criteria
- Production env configured with runtime-correct variable names.
- Atlas connectivity verified from deployment host.
- CORS verified with production frontend origin.
- Razorpay end-to-end verification completed.
- Backend health endpoint is UP.
- Frontend points to production API URL.
