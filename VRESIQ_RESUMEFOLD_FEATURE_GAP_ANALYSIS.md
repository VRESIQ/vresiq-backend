# VResIQ — ResumeFold Feature Gap Analysis

**Audit date:** 2026-11-09
**Scope:** existing VResIQ frontend (React + Vite) and backend (Java 21 + Spring Boot + MongoDB) versus the capabilities requested in the Batch 5 brief, which used ResumeFold as a product reference.
**Method:** both repositories were inspected at their current HEAD (frontend `263c760`, backend `358a73a`), both working trees were clean before any edit, and every status below comes from reading the implementation — not from the presence of a button.

This is **not** a proposal to rebuild VResIQ or adopt ResumeFold's design. It records what already exists, what was missing, and what was deliberately left out of scope.

---

## 1. What VResIQ already had

| Area | Implementation found |
|---|---|
| Resume schema | `Resume.java`: profile, contacts (8 fields), experience, education, skills, projects, certifications, languages, interests, `customSections` map, `decoratives`, `fontPairing`, 25-value template enum, last ATS score/category |
| Import | `POST /api/admin/reconstruct/upload` — PDF (PDFBox, position-sorted text + link annotations) and DOCX (Apache POI text). PDF/DOCX MIME allow-list, 10 MB cap |
| Extraction | `DeterministicParser` (548 lines): alias-based heading detection, contact extraction, section mapping, unclassified-line retention, **no invented dates or facts** |
| Normalisation | `ReconstructionNormalizer`: validation only |
| Enrichment | Deliberately skipped. No LLM receives resume text; ATS is explicitly "not run" during reconstruction |
| Preview | `ResumePreview.jsx` (2 201 lines) renders 25 templates, ATS Harvard header variant with photo support, print stylesheet, flow pagination |
| Export | `POST /api/resumes/{id}/export-pdf` → Puppeteer (`pdf-generator.js`), Letter, free-plan footer watermark, premium unwatermarked, pdf-lib metadata |
| ATS scoring | `RefineService` (1 130 lines) + `atsRules.json` (16 categories, 58 deduction keys, 11 template risks), exposed at `POST /api/ai/refine/{resumeId}` — **premium only**, saved resumes only |
| Editor | `ResumeEditor.jsx` (3 157 lines): section layout, customisation tab, bullet toggles, font pairing, live preview, autosave status |
| Drafts | Dashboard lists multiple named resumes; create-with-name modal; delete with confirmation |
| Auth/ownership | `SecurityConfig`: `/api/admin/**` requires `ROLE_ADMIN`; refine is ownership-scoped and premium-gated |

## 2. Gap matrix

Status values: **Complete** (works end to end), **Partial** (works with a documented limitation), **Missing**.

| Feature | Existing implementation | Status | Action taken |
|---|---|---|---|
| Resume PDF import | PDFBox text, position sort, link annotations, 10 MB/MIME validation | Complete (text); OCR unsupported | Unchanged, extended with portrait extraction |
| DOCX import | POI text extraction | Partial — hyperlinks and text boxes not read | Unchanged (documented) |
| TXT / paste import | None; upload rejects `text/plain` | **Missing** | **Deferred** (P2, see §4) |
| Portrait preservation | None — the reconstructed resume never received the source photo | **Missing** | **Implemented** — PDFBox image extraction (largest page-1 bitmap, 48–3000 px, portrait ratio), POI picture extraction, data-URI embed, manual replace/remove in the Studio |
| Extraction comparison | "Original" tab shows the uploaded PDF in an iframe beside the live preview; DOCX offers a download | Partial — no structured extracted-text or side-by-side diff | Unchanged; the source report now names extraction risks so the user knows *what* to compare |
| ATS source report | None | **Missing** | **Implemented** — `AtsIntelligenceService.analyzeSource`, computed at upload, returned as `sourceAtsReport` |
| ATS draft report | `RefineService` existed but only for a saved, premium-owned resume; the Studio showed "ATS analysis not run" | Partial | **Implemented in the Studio** — `POST /api/admin/reconstruct/analyze` reuses the existing rules engine on the *unsaved* draft |
| Field-linked diagnostics | `RefineSuggestion.section` was a label ("Experience 2 > Start date") with no navigation | Partial | **Implemented** — every finding maps to a `fieldPath`; "Open this field" switches tab, scrolls and focuses the input |
| Job keyword matching | Category keyword gap from `atsRules.json`; no job-description input anywhere | Partial | **Implemented** — JD input, required/preferred classification, evidence matching, coverage counts |
| Job-specific tailoring | None | **Missing** | **Implemented, user-approved only** — keyword surfacing, evidenced summary line, weak-opener bullet rewrite, relevance reorder; accept/dismiss/undo per item; unsupported keywords are refused |
| Multiple named drafts | Present | Complete | Unchanged |
| Draft duplication | Not found in the dashboard | **Missing** | **Deferred** (not required by acceptance; low value versus risk) |
| Draft deletion with confirmation | Present (`window.confirm`) | Complete | Unchanged |
| JSON backup / restore | None | **Missing** | **Implemented** — schema-tagged export (no account data), validated, key-allow-listed, unsafe-key-rejecting import |
| Typography and spacing controls | Font pairing and template/decorative options only; **no font-size, line-spacing, page-margin or section-spacing controls** | Partial | **Deferred** — see §4 for why |
| Preview zoom / page navigation | Continuous flow preview, no zoom or page stepping | **Missing** | **Deferred** (P2) |
| DOCX export | None; no DOCX library in either repository | **Missing** | **Deferred** — feasibility assessed in §4 |
| LaTeX export / studio | None; no LaTeX, KaTeX or WASM compiler dependency | **Missing** | **Deferred** — assessment in §4 |
| OCR for scanned PDFs | None | **Missing** | Out of scope (documented limitation) |

## 3. What was implemented in this batch

1. **Blank trailing page eliminated** in both free and premium exports, at the layout *and* generator level.
2. **Portrait preserved** from the uploaded file, with a manual alternative.
3. **Two independent ATS reports** — uploaded file vs editable draft — with severities, explanations, sections and field links.
4. **Field-linked navigation** from every finding to the exact editable input.
5. **Evidence-based tailoring** with explicit approval, dismissal and undo; unsupported qualifications are listed and never added.
6. **JSON backup/restore** for the draft with schema validation and account-data exclusion.

## 4. Deferred work and feasibility

| Deferred | Why | Feasibility |
|---|---|---|
| TXT / paste import | Lowest product value of the remaining items; the deterministic parser accepts raw text, so this is a small follow-up (accept `text/plain`, add a paste tab, reuse `DeterministicParser`). | **High** — roughly half a day, no new dependency. Rejected now only to protect the P0/P1 budget and avoid an unverified second ingestion path. |
| DOCX export | No DOCX library exists in either repository. A correct implementation needs WordprocessingML with semantic headings, paragraphs, lists and tab-aligned dates; `docx` (npm) or Java `docx4j` would be a new dependency, which the scope rules discourage. | **Medium** — a dependency-free OOXML writer (`ZipOutputStream` + `document.xml`) is viable in ~1–2 days including Word round-trip verification. Not started: output fidelity could not be verified inside this budget. |
| Font size / line spacing / page margins | These interact with all 25 templates, print pagination and ATS parsing. Adding them without a pagination regression suite risks the exact class of defect this batch fixed. | **Medium** — needs a per-template layout harness before exposing controls. |
| Preview zoom / page stepping | P2 polish; the continuous preview already matches PDF content order. | **High** — local UI change. |
| LaTeX export | The canonical model can express most content, but escaping, template parity and escaping of Unicode (e.g. en dashes, `&`) need a tested serialiser. | **Medium** — LaTeX *source export* with 2–3 styles is realistic (~1–2 days). |
| LaTeX Studio (WASM compile, viewer, Overleaf) | Requires a TeX distribution compiled to WASM (tens of MB), a multi-pass compile loop, virtual filesystem and error parsing; Overleaf has no public write API for anonymous projects. | **Low within this product's hosting model** — Vercel/Render bundle limits and cold-start budgets make a credible TeX Live WASM build a separate project. Recommend not pursuing until editor/export parity is complete. |
| OCR | Needs Tesseract or a hosted OCR service; neither is present. | **Medium** — external dependency and a new performance profile. |

## 5. Files touched by this batch

Backend: `AtsIntelligenceModels.java` (new), `AtsIntelligenceService.java` (new), `AtsIntelligenceServiceTest.java` (new), `ReconstructionPipeline.java`, `ReconstructionPlugin.java`, `PdfReconstructionPlugin.java`, `DocxReconstructionPlugin.java`, `ReconstructionModels.java`, `ResumeReconstructionController.java`, `pdf-generator.js`.

Frontend: `ResumeReconstructionStudio.jsx`, `ResumeReconstructionStudio.css`, `ResumePreview.jsx`, `ResumePreview.css`, `ResumeEditor.jsx`, `src/api/index.js`.

Harnesses/evidence (Git-ignored where they contain personal data): `vresiq-frontend/tools/batch5/`, `vresiq-backend/test-fixtures/canada-tester-evidence/after-fix-v5/`.

## 6. Data-model impact

**None.** No schema, collection, migration or API contract change. New fields exist only on the reconstruction *response* DTO (`sourceAtsReport`) and the Studio's in-memory draft. The `Resume` document is unchanged; the portrait reuses the existing `profilePreviewUrl` field.
