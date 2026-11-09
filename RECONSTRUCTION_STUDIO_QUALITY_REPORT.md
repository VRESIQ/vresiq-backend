# VResIQ Reconstruction Studio Quality Report

**Scope:** Batch 4 extraction, classification, preservation, preview pagination, PDF export, and Studio workflow; Batch 5 defects, portrait preservation and ATS intelligence.  
**Status (after Batch 5):** The two Batch 4 acceptance defects are resolved. The Canada fixture now exports **2 pages in both free and premium** (previously 3, the last one blank), and the embedded portrait is extracted and rendered in the reconstructed resume. Draft-side ATS analysis, source-file analysis, field-linked diagnostics and approval-based job tailoring are implemented. See [Batch 5 update](#batch-5-update--canada-acceptance-defects-and-ats-intelligence) at the top of this document; the Batch 4 narrative below is retained unchanged as the historical record.

---

## Batch 5 update — Canada acceptance defects and ATS intelligence

### Defect 1: unnecessary blank third page (fixed)

**Cause.** Two independent sources of empty overhang, both in the export path:

1. `.resume-preview` carries `min-height: 1056px` and `padding-bottom: 48px` for the on-screen sheet, plus per-section `margin-bottom: 22px`. Those values were serialized into the export document, where the PDF page margin already provides the bottom edge. Measured on the Canada draft, resume text ended at **1 913 px** while the laid-out box ended several dozen pixels later. Chromium paginates boxes, not text, so the remainder became a full sheet containing no resume text.
2. The exporter derived nothing about content extent — it simply accepted however many pages Chromium produced.

**Fix.**

- `ResumeEditor.jsx` export stylesheet: `#resume-preview { min-height: 0; padding-bottom: 0; box-shadow: none }` plus `:last-child` margin/padding resets for the last section, item and direct child.
- `ResumeReconstructionStudio.jsx` export stylesheet: the same reset (this was the exact path used by the Canada run).
- `pdf-generator.js`: measures the lowest element that *directly owns text* (`textBottom`), computes `ceil((textBottom − 2) / 1011)` for Letter with the generator's own margins, and removes only trailing pages beyond that count, logging `✓ Removed N trailing page(s) with no resume content`. The 2 px tolerance absorbs rounding; a page whose last line genuinely spills is retained because its text is measured.

**Evidence.**

| Artifact | Page count | Page text |
|---|---|---|
| `after-fix-v5/before-free.pdf` (previous generator, same HTML) | **3** | pages 1–2 content, page 3 only the watermark footer |
| `after-fix-v5/final-free.pdf` | **2** | 5 800 / 4 326 characters, no blank page |
| `after-fix-v5/final-premium.pdf` | **2** | content only, 0 watermark occurrences |
| `after-fix-v4/blank-before.pdf` → `blank-guard.pdf` (controlled fixture with 420 px empty overflow) | **2 → 1** | isolates the guard from resume content |

### Defect 2: missing source portrait (fixed behind a documented limit)

**Investigation.** `PDFBox` was used for text only. A direct resource inspection of `canada-tester.pdf` found exactly one embedded image: page 1, `96×96`, JPEG, ICCBased — the source portrait. Extraction was therefore technically possible; the Batch 4 claim that the format was unsupported was wrong.

**Fix.** `ReconstructionPlugin.extractPortrait` (default `null`), implemented for PDF (largest page-1 bitmap between 48–3000 px with a 0.55–1.75 aspect ratio, re-encoded losslessly to PNG) and DOCX (first body picture meeting the same bounds). The pipeline reads the upload once, embeds the result as a `data:image/png` URI in `profileInfo.profilePreviewUrl`, and adds a review warning. `harvard_ats` — the reconstruction default — now renders a photo beside the name block **only when one exists**; text-only resumes are byte-identical to before.

**Manual alternative.** The Studio shows the detected portrait with *Replace photo*, *Upload photo* (PNG/JPEG/WebP ≤ 3 MB) and *Remove*, so a vector or unsupported portrait can still be supplied.

**Limit, stated plainly.** A photo drawn as vectors, split across tiles, or rasterised into the page background is not extractable; those return `null` and the UI falls back to manual upload. A `null` result means "not extractable", never "candidate has no photo".

### ATS and tailoring

`AtsIntelligenceService` produces three deterministic products, with no external model call and no writes:

| Product | Checks |
|---|---|
| **Source report** (uploaded file, computed at upload) | email/phone/link presence, recognised canonical headings, >240-character lines (merged columns), page-number/footer furniture, duplicated lines, control-character extraction artifacts, thin text (scan/OCR), reversed parsed date ranges |
| **Draft report** (structured resume) | the existing `RefineService` rules engine — missing fields, dates, description length, action verbs, metrics, passive voice, filler wording, keyword gap, template/photo/decorative parse risk — re-exposed for the unsaved draft with a `fieldPath` on every finding |
| **Tailoring** (optional job description) | fixed skill lexicon + sentence-scoped required/preferred classification, evidence lookup in the candidate's own fields, coverage counts, and proposals of four kinds: surface an evidenced keyword, append an evidenced strengths sentence, strip a weak opener from a bullet, reorder the most relevant role first |

Truthfulness rules, enforced in code and in tests: a keyword with no candidate evidence appears only in `unsupported` and can never reach a proposal; proposals quote the candidate's own text as `evidence`; nothing is applied until the user clicks accept; summary and reorder proposals are flagged `requiresConfirmation`; every accepted change can be undone and the whole draft can be reset to the original extraction.

### Regression state after Batch 5

| Check | Result |
|---|---|
| Backend tests (`mvnw.cmd -o -DforkCount=0 test`) | **27 run, 0 failures, 0 errors, 3 skipped** (the 3 skips are the pre-existing environment-dependent PDF tests) |
| New ATS tests | 7/7 — missing-contact detection, empty-extraction handling, section→field mapping, draft field linking, no invented skills, evidence on keyword proposals, no-JD behaviour |
| Frontend production build | `vite build` exit 0 |
| Studio smoke test (`tools/batch5/studio-smoke.mjs`, stubbed API) | portrait rendered, draft score shown, 4 findings, field jump focuses `contactInfo.email`, keyword chips 1 present/2 missing, unsupported list shown, accept→undo round trip, JSON export contains `schemaVersion`/`resume` and no account data, **0 page errors** |
| Canada free/premium watermark | premium 0 occurrences, free 2 (one per page — the blank page no longer receives one) |
| Preview/export consistency | preview content order equals page 1→2 order; 0 horizontal overflow, 0 overlapping sections, duplicate link destinations 0 |
| Performance (3 runs each, this machine) | pipeline incl. JVM start 2.50–2.64 s; Puppeteer export incl. browser launch 2.90–3.18 s |

### Unresolved in this batch

- TXT/paste import, DOCX export, typography micro-controls and LaTeX remain unimplemented (see `VRESIQ_RESUMEFOLD_FEATURE_GAP_ANALYSIS.md` §4 for feasibility).
- The authenticated browser → controller transaction was not exercised: the Studio was driven with stubbed HTTP responses and the pipeline/Puppeteer were invoked directly on the same classes.
- Performance is reported for three runs, not p50/p95 over a fixture corpus.
- PDF source text still loses original bullet glyphs and merges organisation/location columns; the Batch 4 mitigation (ordered paragraph text, no manufactured bullets) is unchanged.

## Summary

The supplied PDF was present and opened as a five-page document. The first real reconstruction reproduced the reported symptoms and exposed additional field mapping defects: the header role and location were unclassified, education degree/institution values were mixed, project and publication sections were fragmented, page footer text and link annotations leaked into resume sections, and the visible LinkedIn path repeated its host segment.

The targeted changes now map the observed role, map-linked location, education, single project, certifications, and publication; remove exact VResIQ footer lines and standalone `mailto:`/`tel:` annotations from section bodies; consolidate the repeated LinkedIn destination; join the two verified collapsed word boundaries; and render extracted wrapped text as paragraphs in reconstruction mode. The final PDF has two content pages and one blank trailing page. Page 1 contains the header, summary, and experience; page 2 contains the remaining experience and all mapped sections; page 3 is blank. This fails the no-unnecessary-blank-pages acceptance criterion.

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

### Canada layout findings

The first validation attempt occurred before the fixture was available and could not attribute the reported page distribution. The resumed acceptance run inspected the exact file and reproduced the summary-heavy first page, isolated publication page, and large unused areas in the original. The first generated reconstruction also reproduced section mapping and URL defects. After targeted fixes, the final export still has a blank third page; the report records this as an unresolved exporter/page-fragmentation issue rather than attributing it to one CSS rule.

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

## Canada Tester Acceptance Validation

**Result: Executed; acceptance criteria remain unmet.** The exact file `vresiq-backend/test-fixtures/canada-tester.pdf` existed, opened successfully, and contained **5 pages**. The page-by-page text extraction produced content on all 5 pages (nonblank extracted character counts: 1,298; 2,575; 2,491; 3,036; 203). The production `ReconstructionPipeline` was executed locally with the PDF plugin, deterministic parser, and normalizer. The pipeline skipped AI enrichment. Resume text, contact values, and annotation targets were not printed to terminal or application logs. Private diagnostic JSON, source-page text, screenshots, preview HTML, and PDFs are stored under Git-ignored evidence directories.

| Acceptance check | Finding |
|---|---|
| Original page-by-page inspection | Page 1: header/contact/portrait and summary with large unused lower area. Page 2: first role; two visible word joins in the text. Page 3: second role. Page 4: third role, one project, two education entries, and two certifications. Page 5: one publication with substantial unused space. |
| Extraction page count and mapping | **5 source pages extracted.** Parsed: contact email/phone/LinkedIn and location; profile name/designation/summary; 3 experience entries; 2 education entries; 1 project; 2 certifications; 1 publication; 2 other link destinations. The source has no separate Skills or Languages section, and none were fabricated. There are 0 unclassified lines after the targeted fixes. |
| First reconstruction findings | The preview had one project split into 5 records, 2 education records with misassigned degree/institution data, 3 certification records including a footer, and 6 publication records including footer and `mailto:`/`tel:` annotations. LinkedIn displayed a repeated path prefix. Map and other links were duplicated in the Links section. Summary and experience wrapped lines appeared as independent bullets. |
| Missing/duplicated/altered content after fixes | The two observed collapsed boundaries were separated without changing the words. The LinkedIn path was normalized to the matching annotation destination. Education and publication metadata are associated with their correct records. Exact VResIQ footer text and standalone mail/tel annotation strings are excluded from sections. No ATS score, proficiency, job, date, or qualification was invented. Experience text is preserved in order but now displays as a continuous paragraph because the PDF text stream did not retain the source bullet glyphs. Institution/city and company/location columns remain combined where position extraction did not provide a reliable separator. The project “Website” label remains, but no associated URI target was extracted. The embedded portrait is not extracted. |
| Final preview and generated PDF | The VResIQ React preview renders 7 sections at 816 px wide; measured font range 10.5–24 px, 0 horizontal overflow elements, 0 overlapping section pairs, and 0 duplicate anchor destinations. Premium and free Puppeteer exports each contain **3 PDF pages**. PDFBox found page text counts **5,655 / 3,929 / 2** for premium and **5,673 / 3,947 / 18** for free; page 3 contains no resume text and is visually blank. |
| Final page-by-page PDF inspection | Page 1: header, summary, and first experience plus part of the next role; no clipping or overlap observed. Page 2: remaining experience, project, education, certifications, publication, and links; text is readable and sections are correctly labeled. Page 3: blank except for the free-plan footer. This is an unnecessary blank page and fails a mandatory criterion. The preview contains the same content and order but is a continuous-flow view rather than visible paper sheets; the blank export page is therefore a preview/export mismatch. |
| Watermark behavior | PDFBox found **0** “Made with VRESIQ” occurrences in premium output and **3** in free output (one per generated page, including the blank page). Premium watermark behavior is correct; free watermark is present on each generated page, including the unwanted blank page. |
| Before/after evidence | Original page images: `test-fixtures/canada-tester-evidence/original-page-1.png` through `original-page-5.png`. First reconstruction: `test-fixtures/canada-tester-evidence/reconstructed-premium.pdf` and `reconstructed-page-1.png` through `reconstructed-page-4.png`. Final extraction: `test-fixtures/canada-tester-evidence/after-fix-v3/canada-extracted-resume.json`. Final preview: `../vresiq-frontend/tools/reconstruction-quality/canada-tester-evidence/after-fix-v3/reconstructed-preview.png` and `reconstructed-export-input.html`. Final exports: `test-fixtures/canada-tester-evidence/after-fix-v3/reconstructed-premium.pdf`, `reconstructed-free.pdf`, `reconstructed-page-1.png` through `reconstructed-page-3.png`, and `pdf-audit.json`. |
| Regression checks | `mvnw.cmd -DforkCount=0 test`: **20 tests, 0 failures, 0 errors, 3 skipped**. `npm run build`: passed. PDFBox emitted a nonfatal font-cache permission warning. The fixture-specific extraction, preview, PDF export, page counts, and watermark status were also checked directly. |
| Remaining limitations / acceptance | **Not accepted as complete.** The blank third page must be eliminated and rechecked. The source portrait is not carried through the current text-only PDF extractor. Source bullet markers were vector/text-extraction artifacts, so experience wording is retained as paragraphs rather than reconstructed bullet-for-bullet. The project label has no extracted destination URI. The authenticated browser-to-controller transaction was not run; direct execution called the same backend pipeline and existing React/Puppeteer paths. |

The original PDF and all Canada-derived diagnostic artifacts are covered by Git ignore rules. No source resume text was sent to an external service, and no commit or push was made.

## Files changed for Batch 4

- Backend pipeline: `DeterministicParser.java`, `PdfReconstructionPlugin.java`, `ReconstructionNormalizer.java`, `ReconstructionPipeline.java`, `ReconstructionModels.java`, `Resume.java`, `ResumeReconstructionController.java`, and `tools/pdf-generator.js`.
- Backend tests: `DeterministicParserTest.java` and `PdfReconstructionPluginTest.java`.
- Frontend: `src/api/index.js`, `src/pages/ResumeReconstructionStudio.jsx`, `src/pages/ResumeReconstructionStudio.css`, `src/components/ResumePreview.jsx`, and `src/components/ResumePreview.css`.
- Synthetic render harness: `tools/reconstruction-quality/` in the frontend, with generated outputs in each repository’s corresponding evidence directory.

Existing uncommitted changes from earlier batches were preserved. No commit or push was made.

## Remaining limitations and follow-up

1. The Canada output still has one visually blank trailing page. The browser preview shows a 2,105 px continuous flow while the PDF exporter paginates it into two content pages plus a blank page; resolve this in the reconstruction export path and rerun the page-count check.
2. The source portrait was not extracted. Scanned/image-only PDFs need OCR; multi-column PDFs can still have reading-order errors even with position sorting.
3. DOCX hyperlinks and hyperlinks embedded as non-URI PDF annotations are not fully extracted. The project Website label had no target URI in the extracted annotation set. Ambiguous date layouts and unknown section heading conventions remain review items.
4. PDF text extraction discarded the original work bullet glyphs and merged organization/location columns. The current correction keeps all description text in order and avoids manufacturing bullet boundaries, but does not reproduce those visual relationships exactly.
5. The backend does not stream parser-stage progress to the browser; the UI shows upload progress and then a processing state until the response arrives. The export was run through the existing Puppeteer script directly; an authenticated browser-to-controller transaction and deployed API were not available.

