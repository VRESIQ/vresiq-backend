# Batch 5 — Independent Integration & Acceptance Verification

**Date:** 2026-11-09
**Verifier stance:** independent reproduction; nothing from the previous Batch 5 report was accepted without re-running it.
**Stack under test:** Vite dev server on `127.0.0.1:5182` → Spring Boot on `127.0.0.1:8081` → local MongoDB `localhost:27017`. No API stubs, no `page.setRequestInterception`.
**Git:** no commit, no push, no reset, no clean. Both working trees are in the same state as before verification, plus the one fix and one harness described in §7.

---

## 1. Tests independently reproduced

| Claim from the Batch 5 report | How reproduced | Result |
|---|---|---|
| Backend suite green (27 tests) | `./mvnw.cmd -o -DforkCount=0 test` | **27 run, 0 failures, 0 errors, 3 skipped** (pre-existing PDF tests) |
| Frontend production build | `npx vite build` | **exit 0** |
| Canada upload returns a source ATS report | Real `POST /api/admin/reconstruct/upload` with a JWT | **HTTP 200**; `sourceAtsReport` present, `available=true`, 6 headings, 2 findings, 8 748 extracted characters |
| Portrait extracted (14 766-char data URI) | Same response | **14 766 chars**, `data:image/png;base64,` — reproduced exactly |
| Parsed structure 3 / 2 / 1 / 2 | Same response | 3 work experience, 2 education, 1 project, 2 certifications, 0 unclassified |
| Draft ATS reuses the existing rules engine | Real `POST /api/admin/reconstruct/analyze` | **HTTP 200**, `durationMs=44`, 6 findings with `fieldPath`, disclaimer present |
| Tailoring only proposes evidenced keywords | Real analyze with JD “Required: SQL, Python. Preferred: Power BI” | Python/SQL/Power BI all `present`; 3 proposals, each quoting candidate text; no unsupported additions |
| Export is 2 pages, correct watermarks | Real `POST /api/resumes/{id}/export-pdf` with both tier flags | **2 pages / 2 pages; premium 0, free 2 watermark occurrences** |
| Authenticated Studio works | Browser login → upload → tabs → actions against the live backend | **Pass** (§4) |
| Field-linked findings navigate | Clicked a *section-level* finding in the real UI | **Failed before the fix, passes after** (§2, §7) |

## 2. Tests that failed

| Test | Observed | Status |
|---|---|---|
| **Field jump for section-level findings** | Real findings use `workExperience`, `skills`, `certifications`, `profileInfo.profilePreviewUrl`; the editor only rendered `workExperience[0].company`-style attributes. “Open this field →” silently did nothing for 2 of 6 draft findings. The stubbed Batch 5 test passed because the mock used `contactInfo.email`, which happened to match. | **Fixed and re-verified** — after the fix the click focuses `workExperience[0].company` (INPUT) inside the Work Experiences section |
| **Employer (“company”) structured extraction** | All three Canada roles have `company = null`; the employer text sits at the start of `description` (`"IQVIA India\nTransformed…"`). Source line is `Software Developer Analyst IQVIA`, i.e. role and employer share one text line. Exported PDF shows role + dates with **no employer heading**, and the draft ATS reports “Add the company or organization name” (−12). | **Open, pre-existing (Batch 4 parser)** — content is not invented or deleted (employer remains in the bullet text), but the structured field is empty. Not fixed: requires a role/employer split heuristic, which is outside a verification pass. |
| **Batch 5 Studio numbers vs real data** | Batch 5 reported “draft score 78/100, 4 findings” from mock JSON. Real Canada draft yields **50/100, 6 draft findings** (2 errors: missing employer, no skills; 3 warnings; 1 tip). The UI renders these correctly; the earlier figure was mock-only. | **Reported as a discrepancy**, UI behaviour itself correct |
| **Free-tier export for a genuinely free user** | Registration is blocked by the SMTP outage (see §3), so no free account could be created. Both tier branches were exercised through the real controller by posting `isFreePlan` `true`/`false`. | **Partially verified** — controller/generator/watermark verified; account-plan resolution to `isFreePlan` not verified |
| **Autosave → reload persistence** | Create → PUT → GET in MongoDB is verified (§4), and reopening the saved resume in the editor loads its stored values. Editing a field and letting the debounced autosave run, then reloading, was **not** completed: the harness’s label-based selector for the Designation input did not resolve within the run. | **Not verified** (browser-side autosave only) |
| **p50 / p95 performance** | Single-run and three-run figures only; no fixture corpus. | **Not measured** |

## 3. Tests blocked by missing credentials or infrastructure

| Blocked item | Exact blocker |
|---|---|
| Real free-plan account | `POST /api/auth/register` returns `{"message":"Failed to send verification email: Email provider operation failed"}` — no working SMTP credentials locally (`VRESIQ_MAIL_*` unset). Email verification therefore cannot be completed. |
| Live email delivery regression (Batch 2/3) | Same SMTP blocker. **Not claimed as verified.** |
| Admin account | Not blocked — the application’s own `DatabaseSeeder` created a local admin (`b5.verify@vresiq.local`, role ADMIN, premium, email-verified) against the local MongoDB. This is a **test account in the local dev database**; it remains in place and is not part of the repository. |
| CORS for the dev origin | Initially rejected (`403 Invalid CORS request`) because `.env` supplied `FRONTEND_URL`. Worked around by launching Spring Boot with `--app.frontend.cors-origins=http://localhost:5173,http://127.0.0.1:5182,http://localhost:5182`; no source change. This is environment configuration, not a product defect. |

## 4. Actual frontend → backend → database results

All of the following came from `tools/batch5/real-e2e.mjs` against the live stack; **0 page errors, 0 console errors, 0 ≥400 API responses** during the run.

| Flow | Evidence |
|---|---|
| Login through the form | `{"token":true,"path":"/dashboard"}` — JWT in session storage, dashboard reached |
| Upload Canada PDF through the Studio input | `{"portrait":true,"portraitChars":14766,"hasName":true,"experienceInputs":15}` |
| Source ATS displayed | “Uploaded file report” block rendered; source findings visible in the ATS tab |
| Draft ATS displayed | `{"draftScore":"50/100","draftFindings":16 (6 draft + source)}` with severities, sections, recommendations |
| Draft report uses the current editable draft | `analyzeReconstruction(reconstructData.resume, jobDescription)` — the request body is the in-memory draft, and the response reflects it |
| Field-linked navigation | Section-level finding → `editorTabActive: true`, focused element `workExperience[0].company` (`INPUT`), section present |
| Job-description analysis | Chips `python/sql/power bi` all `present`; each proposal shows the candidate’s own sentence as evidence |
| Accept | `"1 approved change applied. Last: Added “python” to Skills."` |
| Undo | Change log cleared, suggestion re-offered |
| Dismiss | Suggestion count `3 → 2` |
| Portrait remove | `afterRemove: false` (preview photo removed) |
| Portrait upload | Synthetic 200×260 PNG injected as a real `File` → `rendered: true`, `data:image/png;base64,` |
| Portrait replace | Uses the same upload input as “Replace photo”; path verified through upload, not a separate code path |
| JSON export | Keys `schemaVersion, exportedAt, sourceDocument, resume`; no `userId`/`email`/`password`/`token`; portrait included in the draft |
| Persistence (API) | Created resume `6af221cd0a87cc43156cbb63`, PUT the reconstructed draft, GET returned portrait 14 766 chars, 3 work experience, 2 education, 2 certifications, owner set. Survived the backend restart that occurred between create and browser reopen. |
| Reopen saved resume | `/resume/{id}/edit` loads with `Full name`/`Designation` populated from MongoDB and the live preview rendered |

## 5. Free and Premium PDF evidence

Generated through the live `ResumeExportController` (Puppeteer + patched `pdf-generator.js`), using the real export document for the Canada draft (105 540 chars, portrait embedded).

| | Premium (`isFreePlan=false`) | Free (`isFreePlan=true`) |
|---|---|---|
| HTTP | 200, 2.37 s | 200, 2.30 s |
| Pages | **2** | **2** |
| Watermark occurrences | **0** | **2** (“Made with VRESIQ”, one per page) |
| Extracted non-space characters | 8 171 | 8 199 |
| Portrait | present on page 1 | present on page 1 |
| Trailing blank/footer-only page | none | none |

Visual inspection of rendered pages (`test-fixtures/canada-tester-evidence/verify-pages-page-1.png`, `-page-2.png`; free `free-page-page-2.png`):

- Page 1: portrait, name, contact, SUMMARY, two roles — no clipping, no overlap, generous but not overflowing margins.
- Page 2: end of role 2, role 3, PROJECTS, EDUCATION, CERTIFICATIONS, PUBLICATIONS, Paper Link.
- Free watermark sits below the last content line on page 2 and does not overlap text.
- Text is selectable (`pdftotext` returns clean, readable text).
- Content is not duplicated between pages; section order matches the preview.
- **Content gap:** Experience headings show role + dates only, with the employer absent (see §2), and `IQVIA India` is the first line of the first bullet.

## 6. Regression results (Batch 1–3)

| Regression | Method | Result |
|---|---|---|
| Internship “Relevant URL” field | Code inspection `ResumeEditor.jsx:202` (`url: { label: "Relevant URL (optional)" }` under the internship section) | **Present, unchanged** (no Batch 5 file touched it) |
| Training section | `ResumePreview.jsx:1382,1577`, `ResumeEditor.jsx:146,204` | **Present, unchanged** |
| Neutral light theme / dark theme | No theme CSS was modified by Batch 5 (only `ResumeReconstructionStudio.css` additions and print CSS). Studio smoke screenshot shows the dark admin theme intact. | **Code-inspected; light theme toggle not re-toggled in this pass** |
| Premium upgrade notification | `Dashboard.jsx` / `Dashboard.css` upgrade paths untouched | **Present, unchanged** |
| Build and suite | `vite build` exit 0; 27 backend tests green | **Pass** |
| Reconstruction output | 2 pages, portrait, watermark correct | **Pass** |

No regression was observed in the surfaces Batch 5 touched. Batch 1–3 flows were **not** re-driven end to end in the browser during this pass; the statements above are static verification plus the build/test suite, and are labelled accordingly.

## 7. Fixes made in this verification pass

1. `vresiq-frontend/src/pages/ResumeReconstructionStudio.jsx`
   - `goToField` now resolves a field path through exact → parent item → section (`workExperience[0].description` → `workExperience[0]` → `workExperience`), focuses the first input inside a container, and waits 120 ms for the tab render.
   - Added section anchors `data-field="workExperience|skills|education|projects|certifications"` and `data-field="profileInfo.profilePreviewUrl"` on the portrait row so section-level and photo findings have a real destination.
   - Rationale: real backend findings are section-level; the stubbed test never exercised them.
2. `vresiq-frontend/tools/batch5/real-e2e.mjs` (new, verification-only harness; no product impact).
3. No backend source change was required to make the reviewed flows work.

## 8. Remaining limitations

- **Employer extraction** is the one substantive content defect: `company` is empty for all three roles. Exported resumes still show the employer inside the first bullet, but the heading and ATS check treat it as missing. Fixing it needs a parser rule for “role + employer on one line”, with regression coverage — deliberately not attempted here.
- **Free-plan account path** and **live email delivery** are unverified because SMTP is not configured locally.
- **Autosave → reload** in the editor is unverified (DB persistence itself is verified).
- **Performance** is not p50/p95; only three-run figures exist from the earlier batch.
- The Studio smoke test is mock-based and will keep producing optimistic numbers (78/100) unless it is pointed at the real backend; the real Canada draft scores 50/100.
- Certifications contain a source “↗” link glyph inside the title text (`Base SAS Programmer ↗ SAS`); it is present in the source PDF, not an encoding artifact — but it reads oddly in plain text.

## 9. Verdict

**CONDITIONAL PASS.**

The two Batch 4 acceptance defects are genuinely fixed and were reproduced end to end against the real backend, database and exporter: **2 pages in both tiers, no blank page, portrait rendered and persisted**, correct watermark behaviour, selectable text. The ATS/tailoring loop works with real data — evidence-backed proposals, apply, undo, dismiss, and field navigation after the fix above. One real integration defect was found and fixed; one substantive content defect (missing structured employer names) remains open and is documented rather than papered over. Free-tier account resolution, email delivery, editor autosave and percentile performance remain unverified for stated environmental reasons.
