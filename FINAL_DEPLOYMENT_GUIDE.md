# FINAL_DEPLOYMENT_GUIDE

Date: 2026-05-30
Scope: Release candidate deployment package only (no app logic changes)

## 1) Prepare Production Environment Variables
1. Copy `.env.production.example` to `.env` on the server.
2. Set production values for all placeholders.
3. Ensure:
   - `MONGO_URI` points to Atlas production cluster/database.
   - `FRONTEND_URL` is the production frontend origin.
   - `RAZORPAY_KEY_ID/SECRET` use sandbox for pre-prod verification, then live at cutover.

## 2) Create MongoDB Atlas Production Database
1. In Atlas, create a dedicated production project/cluster.
2. Create dedicated DB user with least privilege on production DB only.
3. Add deployment egress IP to Atlas network access list (or private endpoint).
4. Create production DB (example: `vresiq_prod`).
5. Put full Atlas SRV URI in `MONGO_URI`.

## 3) Configure Production Frontend URL
1. Set `FRONTEND_URL=https://app.vresiq.com` (or your exact production frontend origin).
2. If multiple trusted origins are required, use comma-separated URLs.

## 4) Configure Production CORS Origin
1. Keep CORS restricted to exact trusted origins only.
2. Do not use wildcard origins in production.
3. Validate with preflight:
   - `Origin: https://app.vresiq.com`
   - Expect successful response headers.

## 5) Razorpay Sandbox Checkout + Callback Verification (Required Before Go-Live)
1. Login with a test account in frontend.
2. Start premium purchase from pricing/payment page.
3. Complete checkout using Razorpay sandbox test payment method.
4. Verify backend callback path:
   - `POST /api/payment/verify` returns success for valid signature.
5. Confirm:
   - payment record status becomes `paid`
   - user plan upgrades to `premium`
   - payment appears in history/admin views

## 6) Deployment Steps (Backend RC Artifact)
1. Artifact:
   - `target/resumebuilderapi-0.0.1-SNAPSHOT.jar`
2. Run:
   - `java -jar resumebuilderapi-0.0.1-SNAPSHOT.jar`
3. Health check:
   - `GET /actuator/health` => `{"status":"UP"}`

## 7) Push Release Candidate to Git
1. Ensure working tree includes intended RC files only.
2. Commit with release message.
3. Push to release branch or main as per policy.

## Pre-Deploy Exit Criteria
- Old backend instance stopped.
- Remediated artifact built and checksummed.
- Production env configured (`MONGO_URI`, `FRONTEND_URL`, CORS origins, Razorpay keys).
- Atlas connectivity verified from deployment host.
- Razorpay sandbox checkout + callback verified end-to-end.
