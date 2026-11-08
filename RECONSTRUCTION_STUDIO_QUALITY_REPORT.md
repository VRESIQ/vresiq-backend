# VResIQ Reconstruction Studio Quality Report

**Scope:** Batch 4 extraction, classification, preservation, preview pagination, PDF export, and Studio workflow.  
**Status:** The identified pipeline defects were corrected and regression checks pass on controlled fixtures. The Canada tester acceptance case remains unverified because `canada tester.pdf` was not present in the repository or test workspace.

## Summary

The reported layout symptom could not be reproduced against the Canada resume. Source review found that extraction and mapping could omit or misclassify content before rendering, while the PDF preview path could hide valid sections and compress imported resumes using a whole-document height estimate. The previous pipeline also rewrote source facts through an optional LLM call and reported an invented ATS score.

The reconstruction path now preserves source wording, maps the resume sections supported by the model, extracts PDF link annotations, flags repeated content, exposes uncertain lines for review, displays all parsed sections in the Reconstruction Studio preview, and uses browser page flow for its PDF output. A controlled five-page PDF fixture exported to one page with its checked content present. That result is evidence for the synthetic fixture only; it does not establish the Canada resume's final page count or layout quality.

## Pipeline trace and root causes

| Stage | Finding before changes | Change and current behavior |
|---|---|---|
| Upload | `POST /api/admin/reconstruct/upload` enforced PDF/DOCX MIME and a 10 MB size limit. Its log included the uploaded filename, which can contain personal information. | The request still validates type and size. Logs now contain only file size and MIME type. |
| PDF extraction | PDFBox extracted a text stream only. It did not sort text by position, read URI annotations, or detect image-only scans. Multi-column order and scanned documents could therefore be incomplete. | PDF extraction sorts text by position and appends unique URI annotation targets when they are not already in the text. Empty extraction produces an OCR-not-configured warning. OCR and reliable multi-column reconstruction remain unsupported. |
| Section identification | The parser used broad `contains` checks, so ordinary descriptions could be treated as headings. Repeated headings replaced earlier content. It mapped only a small set of sections. | Headings now match a conservative alias list. Repeated supported sections are combined and flagged. Experience, education, skills, projects, certifications, publications, languages, interests, awards, volunteering, and links are mapped to model fields. Uncertain lines remain visible for review. |
| Content mapping | Projects, certifications, languages, and most optional sections were dropped. Publication records received a fabricated `Nov 2019` date. Link lines could be mixed into the last active section. | Supported sections are mapped without invented dates or proficiency values. Standalone URLs are represented as contact/link data and are not also emitted as language or skill entries. Duplicate URLs and skills are retained once where appropriate and flagged. |
| Normalization | Dates could be rewritten and duplicate skills removed without approval. | Normalization is validation-only. Source dates and duplicate skills remain unchanged in the resume data. |
| Enrichment and scoring | When `AI_API_KEY` or `GEMINI_API_KEY` was set, the pipeline sent the full extracted resume text to OpenRouter and could replace summaries and experience descriptions. With no key, it used a fabricated “Software Engineer” role and score of 65, and marked ATS as completed. The template enum conversion also trusted the returned string. | Resume reconstruction no longer sends source text to an external LLM or rewrites facts. It does not invent a role or ATS score; the ATS check is explicitly marked “not run.” A valid neutral template is selected, and users can change it. Current process environment had neither AI key set; the production example names both variables. The deterministic reconstruction path no longer depends on them. |
| Preview and layout | The imported-resume check included a hard-coded candidate name. A whole-wrapper `offsetHeight / 1056px` estimate ignored printable margins, page fragmentation, and entry boundaries. Optional sections defaulted to hidden, which could omit publications even when parsed. Missing skill scores could render as proficiency bars. | The candidate-specific check is removed. Reconstruction mode uses natural print page flow, keeps headings with following content, allows long entries to continue across pages, shows parsed optional sections, omits placeholder role text, and renders skills/languages as plain labels when no measured proficiency exists. |
| PDF generation | The exporter uses Puppeteer. Print CSS hid the HTML watermark while `pdf-generator.js` supplied an empty footer template, so free-plan exported PDFs did not receive the intended per-page watermark. | The existing Puppeteer exporter now supplies “Made with VRESIQ” as the free-plan footer on every page and leaves premium exports unwatermarked. Both modes are covered by PDF content checks. |
| Logs and exceptions | Ingestion errors could put parser exception messages into traces/logs, upload logs included the filename, and bullet-enhancement errors returned provider exception text. | Reconstruction logs include safe metadata and exception type only. They do not log resume text, filenames, API keys, or provider error bodies. Bullet-enhancement failures now return a generic error. The PDF export controller still logs exporter diagnostics and temporary file paths, but not the HTML body. |

### Why the exact Canada page distribution is not attributed to one CSS rule

The Canada file was not found in either repository or the available testing workspace. The code review confirms multiple upstream losses and preview issues, but cannot establish whether its summary-only first page or isolated publication was caused by a particular source layout, text order, section heading, template, or page break. The report therefore treats the specific Canada page distribution as unverified.

## Reconstruction Studio workflow changes

- Upload progress reports actual bytes transferred. After upload completion, the Studio reports that server-side extraction is in progress; the API does not currently stream per-stage progress events.
- The uploaded PDF can be viewed in an **Original** tab beside the live reconstructed preview. DOCX files can be downloaded for comparison because browsers do not render DOCX natively.
- The editor includes contact details, profile, experience, education, projects, certifications, skills, publications, and other parsed sections.
- Unclassified source lines can be edited and assigned to a resume section before export.
- Users can select a VResIQ template and download the reconstructed PDF. The previous “Save to Database” button only displayed a success alert and did not persist a draft; it was replaced with an actual PDF download action.

## Regression evidence

All evidence below is generated from a **synthetic controlled resume**, not the Canada tester document.

| Check | Result |
|---|---|
| Five-page PDF extraction | Text markers from every page were extracted. A separate five-page PDF test recovered a URI annotation hyperlink. |
| Short PDF | A controlled 1-page PDF was extracted. |
| Section mapping | The synthetic resume mapped 1 experience, 1 education entry, 2 project lines, 1 certification, 1 publication, 3 skills, 1 language, and contact details. |
| Fact preservation | Unit checks verified the name, summary, company, role, dates, bullet text, education, and section text. Publication dates stayed unset where the source did not provide structured dates. |
| Reconstructed PDF | VResIQ’s Puppeteer generator produced a 1-page PDF from the controlled 5-page input. PDFBox confirmed required content in the final PDF. This fixture is intentionally short and does not represent the Canada resume's density. |
| Duplicate handling | Repeated headings are combined and flagged; duplicate skills and URLs are not silently deleted. |
| Watermarks | Generated free-plan PDF contains “Made with VRESIQ”; generated premium PDF does not. |
| Frontend build | `npm run build` completed successfully. |
| Backend tests | `mvnw.cmd -DforkCount=0 ... test` completed with **19 tests, 0 failures, 0 errors**. This includes existing email/subscription tests plus reconstruction parser, PDF extraction, final PDF content, and watermark checks. PDFBox emitted a non-fatal font-cache access warning; tests passed. |

Evidence artifacts:

- [Synthetic five-page original PDF](tools/reconstruction-quality/synthetic-five-page-original.pdf) — 5 pages.
- [Synthetic short original PDF](tools/reconstruction-quality/synthetic-short-original.pdf) — 1 page.
- [Extracted structured resume JSON](tools/reconstruction-quality/synthetic-extracted-resume.json).
- [Extracted source text](tools/reconstruction-quality/synthetic-extracted-text.txt).
- [Reconstructed premium PDF](tools/reconstruction-quality/synthetic-reconstructed.pdf) — 1 page, no watermark.
- [Free-plan PDF watermark check](tools/reconstruction-quality/synthetic-free-watermark.pdf).
- [Original first-page screenshot](tools/reconstruction-quality/synthetic-original-page-1.png).
- [Reconstructed preview screenshot](../vresiq-frontend/tools/reconstruction-quality/synthetic-reconstructed-preview.png).
- [Preview render HTML](../vresiq-frontend/tools/reconstruction-quality/synthetic-render-input.html).

The synthetic fixture places summary, experience, education/projects, certifications/publications, and skills/languages across five source pages. Its reconstructed output puts the parsed sections into one page and retains the checked phrases. The screenshots are visual evidence for this fixture only.

## Files changed for Batch 4

- Backend pipeline: `DeterministicParser.java`, `PdfReconstructionPlugin.java`, `ReconstructionNormalizer.java`, `ReconstructionPipeline.java`, `ReconstructionModels.java`, `Resume.java`, `ResumeReconstructionController.java`, and `tools/pdf-generator.js`.
- Backend tests: `DeterministicParserTest.java` and `PdfReconstructionPluginTest.java`.
- Frontend: `src/api/index.js`, `src/pages/ResumeReconstructionStudio.jsx`, `src/pages/ResumeReconstructionStudio.css`, `src/components/ResumePreview.jsx`, and `src/components/ResumePreview.css`.
- Synthetic render harness: `tools/reconstruction-quality/` in the frontend, with generated outputs in each repository’s corresponding evidence directory.

Existing uncommitted changes from earlier batches were preserved. No commit or push was made.

## Remaining limitations and follow-up

1. Run the same extraction, content-preservation, rendered-page, and visual checks against `canada tester.pdf` when that exact file is available. The Canada-specific definition of done cannot be confirmed without it.
2. Scanned/image-only PDFs need OCR. Multi-column PDFs can still have reading-order errors even with position sorting.
3. DOCX hyperlinks and hyperlinks embedded as non-URI PDF annotations are not fully extracted. Ambiguous date layouts and unknown section heading conventions remain review items.
4. The backend does not stream parser-stage progress to the browser; the UI shows upload progress and then a processing state until the response arrives.
5. The generated PDF regression used the existing Puppeteer script directly. A running authenticated admin session and deployed API were not available for an end-to-end browser/API transaction.

