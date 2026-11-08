# VResIQ Batch 3: Premium Upgrade Email Delivery

**Date:** 8 November 2026  
**Scope:** Admin-initiated Basic → Premium membership conversion and its email notification

## Finding

The admin action did reach a notification trigger in the source. `AdminDashboard.jsx` calls `PUT /api/admin/users/{userId}/subscription?plan=...`; `AdminController` delegates to `AdminService`, which calls `SubscriptionService`. The former subscription service saved the plan, detected a non-Premium → Premium transition, and called `EmailService`.

The strongest concrete failure in that path was provider selection. `VRESIQ_MAIL_PASSWORD` and `MAIL_PASSWORD` are documented as SMTP passwords, but `EmailService` also treated either value as a Brevo HTTP API key. If deployment used the documented SMTP variables without a separate `BREVO_API_KEY`, the service sent the SMTP password in Brevo's `api-key` header. Brevo would reject that request. `SubscriptionService` then caught the exception and returned the successfully updated membership, so the admin saw success while the user received no email.

That failure mode is source-confirmed, but the deployed request cannot be reconstructed from this workspace: production logs and live environment values were not available. The code's catch-and-log behavior also means there is no available delivery evidence for the reported upgrade.

## Traced flow

1. **Admin Dashboard:** the subscription selector in `src/pages/AdminDashboard.jsx` calls `updateAdminSubscription` from `src/api/index.js`.
2. **Membership API:** the client sends `PUT /api/admin/users/{userId}/subscription?plan=premium`. `/api/admin/**` is restricted to admins by `SecurityConfig`.
3. **Database update:** `SubscriptionService` now uses one conditional MongoDB update to set `subscriptionPlan=premium` and persist the notification event. A database error stops the flow before the email call.
4. **Notification dispatch:** a worker atomically claims the persisted event and calls `EmailService`. Repeated Premium requests do not match the Basic-to-Premium transition query and do not create another event.
5. **Provider:** `BREVO_API_KEY` explicitly selects the Brevo HTTPS API. Otherwise, the service uses configured SMTP credentials. An SMTP password is no longer treated as an API key.
6. **Delivery status:** Brevo's message ID is stored after provider acceptance. An authenticated webhook can record later `delivered` and bounce events. Brevo's `delivered` event is provider-reported delivery to the recipient's mail server; it does not prove inbox placement.

## Changes made

- Added an embedded notification outbox to the user record so the Premium update and pending email event persist together.
- Added atomic event claiming, duplicate suppression, a scheduled retry worker, and an index for pending events.
- Retries definite transient rejections (such as HTTP 429 and missing/rejected SMTP authentication) with backoff, up to five total attempts. Ambiguous network/provider outcomes and stale in-progress attempts move to `UNKNOWN` and are not blindly resent.
- Preserved the Premium membership after any email failure. Statuses and safe failure codes are persisted as `PENDING`, `IN_PROGRESS`, `RETRY`, `ACCEPTED`, `DELIVERED`, `DELIVERY_FAILED`, `FAILED`, or `UNKNOWN`.
- Added the authenticated endpoint `POST /api/webhooks/brevo/transactional-email`. It requires `BREVO_WEBHOOK_TOKEN` in the `X-VResIQ-Brevo-Token` header. Brevo must be configured to send the transactional events to this endpoint before delivery events will appear.
- Removed recipient addresses, provider response bodies, and exception details from email-service logs. Logs use notification IDs, provider names, HTTP status codes, and safe failure codes; API keys are never logged.
- Replaced the inline email string with a responsive VResIQ-branded HTML template. It names Premium (Pro), lists the currently advertised Pro benefits, includes the UTC activation date, escapes the user's name, and explains that Pro is sold as one-time access with no expiry date currently stored on the account.
- Returned the notification status to the admin UI and displayed provider acceptance, delivery, retry, failure, and unknown-outcome states.
- Corrected configuration docs: use either a dedicated `BREVO_API_KEY`, or SMTP username and password variables. `VRESIQ_MAIL_PASSWORD`/`MAIL_PASSWORD` are SMTP secrets only. Documented the webhook token setup.

## Configuration and evidence limits

The current local process and ignored backend `.env` have no values set for `BREVO_API_KEY`, `VRESIQ_MAIL_USERNAME`, `VRESIQ_MAIL_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, or `BREVO_WEBHOOK_TOKEN`. The production example file contains placeholders, not deployed credentials. No production environment values or mail logs were available to inspect.

As a result, I did not send a real message. The workspace also did not provide a controlled test inbox. A provider acceptance or delivered event cannot be claimed from these tests; configure a real provider credential and controlled address, then check Brevo's transactional log and webhook event. Brevo notes that a `delivered` event means the recipient's mail server accepted the message and does not guarantee inbox placement: [Brevo transactional email logs](https://help.brevo.com/hc/en-us/articles/35699922048146-View-and-export-your-event-logs).

To enable delivery-state updates in a deployment:

1. Set `BREVO_API_KEY` to a dedicated Brevo API key, or set both `VRESIQ_MAIL_USERNAME` and `VRESIQ_MAIL_PASSWORD` (or their `MAIL_*` aliases) to SMTP credentials.
2. Set `MAIL_FROM` to a sender address verified with the provider.
3. Set a high-entropy `BREVO_WEBHOOK_TOKEN`, then configure Brevo's transactional webhook to POST `delivered`, `soft_bounce`, `hard_bounce`, `blocked`, and `error` events to `/api/webhooks/brevo/transactional-email` with that token header.
4. Send a test upgrade to a controlled inbox. Confirm the provider message ID/acceptance in Brevo, then verify the webhook status. Inbox placement still requires checking the controlled mailbox and its spam folder.

Brevo documents the send response's `messageId` and transactional delivery webhooks in its [send email API guide](https://developers.brevo.com/docs/send-a-transactional-email) and [transactional webhooks guide](https://developers.brevo.com/docs/transactional-webhooks).

## Verification

`mvnw.cmd -DforkCount=0 test` passed **10/10 unit tests**:

| Scenario | Result |
|---|---|
| Basic → Premium | Email body includes the Premium plan, activation date, benefits, and escaped display name; accepted state is persisted. |
| Repeated Premium update | No second email attempt is created. |
| Invalid user | No notification or email is created. |
| Database update failure | Error propagates before any email attempt. |
| Definite provider rejection (simulated HTTP 429) | Membership remains Premium; a retry is recorded with a safe failure code. |
| Ambiguous provider outcome | Membership remains Premium; status becomes `UNKNOWN` and automatic resend is suppressed. |
| SMTP credentials | SMTP password selects SMTP transport and is not sent in an API key header. |
| Dedicated Brevo API key | HTTPS API path is selected and the provider message ID is captured. |
| Brevo rejection | HTTP rejection status is recorded without exposing the response body. |
| Admin response serialization | Safe notification status is exposed; outbox identifiers remain hidden. |

`npm run build` in `vresiq-frontend` also passed. The provider, live database, delivery webhook, and controlled inbox were not exercised because local email credentials and a controlled address are unavailable.
