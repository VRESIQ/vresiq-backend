# VResIQ — Final Conditional Acceptance Closure

**Date:** 2026-11-10
**Starting state:** backend `358a73a`, frontend `263c760`; both trees carried the existing uncommitted Batch 5 work (verified with `git status` before any edit). No commit, push, reset, checkout, stash or clean was performed.
**Environment:** real Spring Boot (`127.0.0.1:8081`) → real MongoDB (`localhost:27017`) → real Vite app (`127.0.0.1:5182`). No request stubbing; the only interception was a deliberate `PUT /api/resumes/*` abort used to test the save-failure path.

---

## 1. Files modified

| File | Change |
|---|---|
| `vresiq-backend/src/main/java/in/rithik/resumebuilderapi/service/reconstruction/DeterministicParser.java` | Conservative employer mapping in `parseExperiences` + `isStandaloneOrganizationLine`, `isAmbiguousOrganizationLine`, `looksLikeProperNounPhrase`, `startsWithActionVerb` |
| `vresiq-backend/src/test/java/in/rithik/resumebuilderapi/service/reconstruction/DeterministicParserTest.java` | 3 new tests (Canada layout, ambiguous lines, inline `Company` header regression) |
| `vresiq-frontend/tools/batch5/final-closure.mjs` | New verification harness (autosave/reload/regressions; not product code) |
| `vresiq-frontend/tools/batch5/final-pdf.mjs` | New harness that captures the real Studio export payload for both PDF tiers |

No other product file was changed. A print-density experiment in `ResumePreview.css` produced byte-identical output and was reverted, so that file's diff is unchanged from the start of this pass.

## 2. Root causes

**P1 — Employer extraction.** `parseExperiences` opens a new item on any line matching `DATE_RANGE`, then treats *every* following line as description. The Canada source places the employer on the line directly after the role/date header:

```
Software Developer Analyst Apr 2021 – Oct 2025
IQVIA India
Transformed complex pharmaceutical…
```

The header split (`\s{2,}`, `|`, ` at `) therefore saw only role + dates, `company` stayed null, and `IQVIA India` became the first description line. The same layout repeats for the other two roles.

**P2 — Autosave.** Not a defect: a 1.5s debounce keyed on `resume`/`hasUnsavedChanges` exists (`ResumeEditor.jsx:636-669`) and works. Verified rather than changed.

**P3 — Themes.** No defect found; measured directly.

**P4 — Registration.** Two distinct causes, see §9.

**P5 — PDF pagination.** Employer headings add three heading lines to a document that was already at two-page capacity. The rendered page 2 has visible white space but the trailing `PUBLICATIONS` + `LINKS` blocks move as a unit to page 3, producing **3 pages** in both tiers (detail in §8).

## 3. Employer fix — rule and safety

Applied only when all of the following hold:

- the candidate line is the **first** line after a role/date header and the item has no company yet;
- the description is still empty (so bullets are never reclassified);
- 2–8 words, ≤ 80 characters, starts uppercase, ≥ 60% proper-noun words (connectors like `of`/`and`/`&` exempt);
- no digits, no `@`, no URL, no date range;
- no bullet glyph, no trailing `, . ! ? ; :`;
- the first word is not a sentence verb (`-ed`, `-ing`, or a small irregular list).

Otherwise the line stays in the description untouched — never moved into `company` on a guess — and, if it is organization-shaped but ambiguous (e.g. `Oshawa, Canada`), it is appended to `unclassifiedContent` as `Possible organization line needs review: …`, which the API returns for review. Every original sentence and date is preserved; the employer line is not duplicated in the description.

## 4. Before / after — real Canada upload

`POST /api/admin/reconstruct/upload` with the fixture PDF, authenticated session, same file before and after:

| | Before | After |
|---|---|---|
| `workExperience[0].company` | `null` | `IQVIA India` |
| `workExperience[1].company` | `null` | `Clinical Biometrics Solutions Pvt Ltd India` |
| `workExperience[2].company` | `null` | `Clinical Biometrics Solutions Pvt Ltd India` |
| `[0].description` first line | `IQVIA India` | `Transformed complex pharmaceutical…` (no employer duplication) |
| Dates | `Apr 2021 – Oct 2025`, `Jun 2018 – Apr 2021`, `May 2016 – May 2018` | identical |
| Draft ATS score | 50/100 | **58/100** |
| Draft findings | 6, incl. *“Add the company or organization name”* error | 6; **organization error gone**, remaining: missing skills, certification issuer, metrics, year format, photo, action-verb tip |
| Parse duration | 740 ms cold / 237 ms warm | 199–237 ms |
| Unclassified content | 0 | 0 |

Notes: role 1 keeps both source roles in one string (`Biometrics Team Lead Market Research & Commercial Intelligence Lead`) because the source prints them on one line — pre-existing, content preserved, not touched here.

## 5. Tests executed

| Check | Result |
|---|---|
| `DeterministicParserTest` (5 existing + 3 new) | **8 passed** |
| Full backend suite `./mvnw.cmd -o -DforkCount=0 test` | **30 run, 0 failures, 0 errors, 3 skipped** (PDF tests skipped by design) |
| Frontend `npx vite build` | **exit 0** |
| Real upload + analyze (Canada) | HTTP 200, 199 ms / 116 ms |
| Browser autosave, reload, failure path, themes | see §6 |
| Real Free + Premium PDF export | HTTP 200, 2.29 s / 2.34 s, see §8 |

## 6. Real browser / backend / database evidence (Priority 2 and 3)

Run: `tools/batch5/final-closure.mjs` against the live stack. Result: `tools/batch5/out/final-closure-result.json`.

**Autosave and reload**
- Edited 8 fields across Profile, Experience, Training and Internships.
- **1 `PUT /api/resumes/{id}`** for the whole burst (debounce coalesces correctly), toast `Changes Saved`.
- After `page.reload()` and reopening the same resume: `fullName`, `designation`, portrait (`2` data-URI images), training title/provider/dates and internship role + URL all present.
- **0 PUT** during page load and **0 extra PUT** on a second idle reload → no duplicate writes from mount/autosave loops.
- Save-failure injection (abort the PUT): visible toast `⚠ Could not auto-save changes.` plus inline error text; after reload the last successfully saved value returned, i.e. no silent corruption. The only console errors in the run are the deliberately aborted request.
- Not exercised: typing then reloading *inside* the 1.5s debounce window (known data-loss window inherent to debounced autosave, no `beforeunload` guard observed).

**Training** — added via Section Layout → Training → Add; three fields filled; saved; after reload values intact. **Browser-tested.**

**Internship Relevant URL** — field label `Relevant URL (optional)` rendered, URL saved and reloaded intact. **Browser-tested.**

**Neutral light theme** — `#theme-toggle`: light mode `data-theme="light"`, body background `rgb(255,255,255)`, text `rgb(23,23,23)`; screenshot `tools/batch5/out/closure-light-theme.png` inspected: neutral white, no tint, legible. **Browser-tested + visually inspected.**

**Dark theme unchanged** — before and after toggling: `data-theme="dark"`, background `rgb(10,11,9)`, text `rgb(240,241,238)` — identical values. **Browser-tested.**

**Premium upgrade notification** — implementation at `Dashboard.jsx:249`, rendered only when `hasReachedLimit` (free plan, one-resume cap). This account is `Pro`, so the strip was correctly absent; body text showed `Plan Pro`, no upgrade prompt. Free-plan rendering **not browser-tested** (no free account possible, §9). **Code + premium-state browser evidence only.**

## 7. Remaining limitations

- **PDF is 3 pages after the fix** (§8) — the single open acceptance item.
- Free-plan account path still untestable locally; premium/Pro path only.
- Registration failure leaves an orphaned unverified account (§9).
- Editor autosave has no `beforeunload` guard; edits within the debounce window are lost on immediate navigation (not a regression, documented).
- Role line that contains two roles is not split (source fidelity preserved).

## 8. Free / Premium PDF evidence (Priority 5)

Payload captured from the real Studio "Download Reconstructed PDF" request after the parser fix (198 230 chars of HTML incl. the portrait), then replayed against the live controller for both tiers.

| | Premium (`isFreePlan=false`) | Free (`isFreePlan=true`) |
|---|---|---|
| HTTP / time | 200, 2.29 s | 200, 2.34 s |
| Pages | **3** | **3** |
| Non-space chars per page | 4 007 / 4 011 / 328 | 4 021 / 4 025 / 342 |
| Watermarks | 0 | 1 per page (3 total) |
| Portrait | present, circular, page 1 | present, page 1 |

Visual inspection (`finalcheck-premium-page-1.png`, `-page-2.png`, `-page-3.png`, `finalcheck-free-page-*`):
- Page 1: portrait, name, contact line, SUMMARY, role 1 **with `IQVIA India` heading**, dates right-aligned, role 2 begins. No clipping or overlap.
- Page 2: continuation of role 2 bullets, role 3 with `Clinical Biometrics Solutions Pvt Ltd India` heading, PROJECTS, EDUCATION, CERTIFICATIONS; ~20 % of the page left empty.
- Page 3: `PUBLICATIONS` (title, journal, Paper Link) and `LINKS` (two URLs) — **real content, not a blank or footer-only page**.
- Watermark on free pages sits below content, no overlap; text is selectable on every page.

**Cause of the third page:** the two extra employer heading lines consumed the remaining slack, and the trailing `PUBLICATIONS` + `LINKS` blocks fragment onto page 3 despite the white space on page 2 (the content-flow print mode already compacts to `--rp-item-margin-bottom: 10px` / `--rp-section-margin-bottom: 12px` at `ResumePreview.css:2717-2718`). Control: the pre-fix payload still renders **2 pages** through the same controller, confirming the change is content height, not the generator.

A print-only spacing override was tried (10px/18px on the print block) and produced byte-identical page output, so it was reverted rather than left in unverified. Reaching 2 pages now needs either a typography/spacing decision for the Harvard-ATS layout (e.g. tighter section rules or allowing the trailing custom-section group to break) or accepting 3 pages for this fixture — both are product decisions outside a closure pass, so they are documented instead of forced.

## 9. Registration / SMTP diagnosis (Priority 4)

Observed exactly:

```
POST /api/auth/register → 400 {"message":"Failed to send verification email: Email provider operation failed"}
POST same payload again → 409 {"message":"Email already exists"}
```

Backend log:

```
WARN  EmailService  Email provider is not configured: set BREVO_API_KEY or both SMTP username and password variables
ERROR AuthService   Failed to send verification email to closure.probe@vresiq.local: Email provider operation failed
```

Diagnosis:

1. **Primary cause — missing local provider configuration.** `application.properties` points at `smtp-relay.brevo.com:587`, but `BREVO_API_KEY`, `VRESIQ_MAIL_USERNAME` and `VRESIQ_MAIL_PASSWORD` are all empty. `EmailService.requireSmtpConfiguration()` rejects the send before any network call. This is configuration, not a provider outage or wrong host.
2. **Registration-flow dependency.** Sign-up cannot complete without a verification email by design; verification was **not** disabled or weakened.
3. **Backend error-handling defect (confirmed by the 409 on retry).** `AuthService.register` persists the user (`userRepository.save(newUser)`, line ~70) *before* sending the email (lines ~93-94). When the send throws, the transaction is not rolled back, leaving an orphan unverified account that blocks re-registration and can only be recovered via resend-verification (which also fails without credentials). The response is also a generic 400 rather than a specific “account created, verification email could not be sent” state.

**Not changed:** reordering persistence is an authentication-behaviour change and needs approval; it is recorded here with the exact lines. Local side effect: one orphan account `closure.probe@vresiq.local` remains in the local dev database.

## 10. Verdict

**CONDITIONAL PASS.**

Priority 1 is genuinely closed: employers map on all three Canada roles from a real upload, with a conservative rule that never fabricates, never duplicates text, and surfaces ambiguous candidates for review; unit and real-stack evidence agree, and the draft ATS score improves 50 → 58 with the organization error cleared. Priority 2 passes end-to-end (multi-field edit → single debounced write → reload → values and portrait intact, no duplicate writes on idle reload, visible error and intact prior state on forced save failure). Priority 3 passes for Training, Internship Relevant URL, light and dark themes in a real browser with visual confirmation; the premium-only state of the upgrade notice is code + account evidence. Priority 4 is diagnosed to configuration plus a confirmed persistence-order defect, not weakened auth.

The condition is Priority 5: after the employer fix the Canada document exports as **3 pages** in both tiers (page 3 is real `PUBLICATIONS`/`LINKS` content, not blank, and the watermark/portrait/pagination checks otherwise pass). Restoring two pages requires a typography or pagination-break decision that this pass was not authorized to make.
