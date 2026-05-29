const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');
const pdfParse = require('pdf-parse');

// Load base CSS styles from frontend ResumePreview.css to mimic live layout styles
const cssPath = path.join(__dirname, '..', 'resume-builder-frontend', 'src', 'components', 'ResumePreview.css');
let cssContent = '';
try {
  cssContent = fs.readFileSync(cssPath, 'utf8');
} catch (e) {
  console.warn('WARNING: Could not load frontend ResumePreview.css. Proceeding with fallback core styling.');
}

// ─────────────────────────────────────────────────────────────────────────────
// UTILITIES
// ─────────────────────────────────────────────────────────────────────────────

function escapeRegex(str) {
  return str.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
}

function detectWordSplit(extractedText, word) {
  if (extractedText.toLowerCase().includes(word.toLowerCase())) return null;
  const pattern = word.split('').map(c => escapeRegex(c)).join('\\s*');
  const matches = extractedText.match(new RegExp(pattern, 'gi'));
  if (matches) {
    for (const m of matches) {
      if (/\s/.test(m)) return m;
    }
  }
  return 'not_found';
}

function compareCharacterCounts(canonical, extracted) {
  const orig = canonical.toLowerCase().replace(/[^a-z0-9]/g, '');
  const extr = extracted.toLowerCase().replace(/[^a-z0-9]/g, '');
  const oFreq = {}, eFreq = {};
  for (const c of orig) oFreq[c] = (oFreq[c] || 0) + 1;
  for (const c of extr) eFreq[c] = (eFreq[c] || 0) + 1;
  let missing = 0, extra = 0;
  for (const c in oFreq) { const d = oFreq[c] - (eFreq[c] || 0); if (d > 0) missing += d; }
  for (const c in eFreq) { const d = eFreq[c] - (oFreq[c] || 0); if (d > 0) extra += d; }
  return { missing, extra };
}

/**
 * Verify that all section titles appear in the correct sequential order
 * within the extracted text. Returns null on success, or an object
 * describing which title was out-of-order / missing.
 */
function verifySectionOrder(extractedText, orderedTitles) {
  const lower = extractedText.toLowerCase();
  let lastIdx = -1;
  for (const title of orderedTitles) {
    const idx = lower.indexOf(title.toLowerCase());
    if (idx === -1) {
      return { type: 'missing_title', title };
    }
    if (idx < lastIdx) {
      return { type: 'wrong_order', title, message: `"${title}" appeared before expected position` };
    }
    lastIdx = idx;
  }
  return null;
}

/**
 * Check for section leakage: content of sectionB appearing before the title of sectionB.
 * content items should only appear after their own section header.
 */
function verifySectionIsolation(extractedText, sections) {
  const lower = extractedText.toLowerCase();
  const issues = [];
  for (const { title, uniqueContent } of sections) {
    const titleIdx = lower.indexOf(title.toLowerCase());
    if (titleIdx === -1) {
      issues.push(`Section title "${title}" not found — cannot verify isolation`);
      continue;
    }
    for (const item of uniqueContent) {
      const itemIdx = lower.indexOf(item.toLowerCase());
      if (itemIdx !== -1 && itemIdx < titleIdx) {
        issues.push(`Content "${item}" appeared before its section title "${title}" — possible leakage`);
      }
    }
  }
  return issues.length > 0 ? issues : null;
}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED HTML SCAFFOLDING
// ─────────────────────────────────────────────────────────────────────────────

const ALL_GOOGLE_FONTS = `https://fonts.googleapis.com/css2?family=Inter:wght@300;400;600;700&family=Sora:wght@300;400;600;700&family=Playfair+Display:ital,wght@0,400;0,700;1,400&family=Lato:wght@300;400;700&family=Outfit:wght@300;400;600;700&family=DM+Sans:wght@300;400;500&family=Cormorant+Garamond:ital,wght@0,400;0,600;0,700;1,400&family=Jost:wght@300;400;500&family=Space+Grotesk:wght@300;400;500;600;700&family=Raleway:wght@300;400;600;700;800&family=Nunito+Sans:wght@300;400;600&family=Plus+Jakarta+Sans:wght@300;400;500;600;700&family=Figtree:wght@300;400;500&family=IBM+Plex+Sans:wght@300;400;500;600&family=Bodoni+Moda:ital,wght@0,400;0,600;0,700;1,400&family=Mulish:wght@300;400;500;600&family=Nunito:wght@300;400;600;700;800&family=Open+Sans:wght@300;400;500&family=Fraunces:ital,wght@0,300;0,400;0,700;1,400&family=Manrope:wght@300;400;500;600&family=Source+Code+Pro:wght@400;500;600&family=Source+Sans+3:wght@300;400;600&family=Tinos:ital,wght@0,400;0,700;1,400&family=Lora:ital,wght@0,400;0,500;0,600;0,700;1,400&display=swap`;

const BASE_PRINT_CSS = (borderStyle = 'none') => `
html, body {
  background: #ffffff !important; color: #000000 !important;
  margin: 0 !important; padding: 0 !important; overflow: hidden !important;
}
@page { size: letter; margin-top: 40px !important; margin-bottom: 40px !important; margin-left: 0 !important; margin-right: 0 !important; }
@page :first { margin-top: 0 !important; }
#resume-preview, .resume-preview {
  position: static !important; display: block !important;
  width: 816px !important; max-width: 816px !important; height: auto !important;
  margin: 0 auto !important; padding: 0 !important; box-shadow: none !important;
  border: ${borderStyle} !important; overflow: visible !important;
  visibility: visible !important; box-sizing: border-box !important;
}
body * { visibility: visible !important; }
html, body, #resume-preview, #resume-preview * {
  font-variant-ligatures: none !important;
  font-feature-settings: "liga" 0, "clig" 0, "calt" 0, "dlig" 0, "hlig" 0, "kern" 0 !important;
  font-kerning: none !important; text-rendering: optimizeSpeed !important;
  letter-spacing: normal !important; word-spacing: normal !important;
}
.rp-ats-name,.rp-ats-name-serif,.rp-sidebar-name,.rp-executive-name,
.rp-sig-name,.rp-centered-name,.rp-role,.rp-executive-role,
.rp-sig-role,.rp-centered-role,.rp-stitle {
  letter-spacing: normal !important; word-spacing: normal !important;
}`;

function wrapHtml(templateId, fontHeading, fontBody, bodyContent, options = {}) {
  const densityClass = options.highDensity ? 'rp-high-density' : '';
  const bulletClass  = `rp-bullets-${options.bulletStyle || 'circle'}`;
  const borderStyle  = options.pageBorder ? '1px solid var(--accent)' : 'none';
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>QA Fixture — ${templateId}</title>
  <link rel="stylesheet" href="${ALL_GOOGLE_FONTS}">
  <style>${cssContent}</style>
  <style>${BASE_PRINT_CSS(borderStyle)}</style>
</head>
<body>
  <article id="resume-preview"
    class="resume-preview rp-${templateId} ${densityClass} ${bulletClass}"
    data-template="${templateId}" data-lstyle="standard"
    style="--rp-font-heading:${fontHeading};--rp-font-body:${fontBody};--accent:#111111;--accent-readable:#111111;--on-accent:#ffffff;">
    ${bodyContent}
  </article>
</body>
</html>`;
}

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 1: SYNTHETIC MATRIX
// ─────────────────────────────────────────────────────────────────────────────

function generateSyntheticHtml(templateId, fontPairing, options = {}) {
  const HEADING = {
    fraunces:'\'Fraunces\', serif', playfair:'\'Playfair Display\', serif',
    lora:'\'Lora\', Georgia, serif', sora:'\'Sora\', sans-serif',
    outfit:'\'Outfit\', sans-serif', cormorant:'\'Cormorant Garamond\', serif',
    spacegrotesk:'\'Space Grotesk\', sans-serif', raleway:'\'Raleway\', sans-serif',
    jakarta:'\'Plus Jakarta Sans\', sans-serif', ibmplex:'\'IBM Plex Sans\', sans-serif',
    bodoni:'\'Bodoni Moda\', serif', nunito:'\'Nunito\', sans-serif',
    sourcecode:'\'Source Code Pro\', monospace', tinos:'\'Tinos\', \'Times New Roman\', Times, serif',
    inter:'\'Inter\', sans-serif',
  };
  const BODY = {
    lora:'\'Lora\', Georgia, serif', cormorant:'\'Jost\', sans-serif',
    playfair:'\'Lato\', sans-serif', outfit:'\'DM Sans\', sans-serif',
    raleway:'\'Nunito Sans\', sans-serif', jakarta:'\'Figtree\', sans-serif',
    bodoni:'\'Mulish\', sans-serif', nunito:'\'Open Sans\', sans-serif',
    sourcecode:'\'Source Sans 3\', sans-serif', tinos:'\'Tinos\', \'Times New Roman\', Times, serif',
    inter:'\'Inter\', sans-serif', sora:'\'Inter\', sans-serif',
    spacegrotesk:'\'Inter\', sans-serif', ibmplex:'\'IBM Plex Sans\', sans-serif',
  };
  const headingFont = HEADING[fontPairing] || '\'Inter\', sans-serif';
  const bodyFont    = BODY[fontPairing]    || '\'Manrope\', sans-serif';

  const body = `
    <header class="rp-header rp-ats-header rp-ats-header-left rp-academic-header">
      <h1 class="rp-ats-name rp-ats-name-serif rp-sidebar-name rp-executive-name rp-sig-name rp-centered-name rp-academic-name">Rithik Mettu</h1>
      <p class="rp-role rp-ats-role-left rp-executive-role rp-sig-role rp-centered-role rp-academic-role">VFX SUPERVISOR</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:qa@vr.co">qa@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="tel:+911234567890">+911234567890</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item">Hyd, ind<span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://linkedin.com/in/rithik-qa">https://linkedin.com/in/rithik-qa</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://github.com/vr/proj">https://github.com/vr/proj</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://vr.co/audit">https://vr.co/audit</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">
      <section class="rp-section"><h3 class="rp-stitle">TECHNICAL TERMS</h3>
        <ul class="rp-desc-list">
          <li><strong>Kubernetes</strong>: Container orchestration.</li>
          <li><strong>TypeScript</strong>: Statically typed JS superset.</li>
          <li><strong>Skia-engine</strong>: Chromium graphics library.</li>
          <li><strong>GPOS-ligature</strong>: Advanced glyph positioning.</li>
          <li><strong>Establishmentarianism</strong>: Long academic word.</li>
          <li><strong>Incomprehensibility</strong>: Audit verification word.</li>
          <li><strong>Supercalifragilisticexpialidocious</strong>: Extra-long word.</li>
        </ul>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">PROJECTS</h3>
        <div class="rp-item"><strong>Twitter edits</strong>
          <div class="rp-item-desc"><span class="rp-desc-text">Demo: <a class="rp-project-link" href="https://tw.vr.co">https://tw.vr.co</a></span></div>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">SPECIAL CHARACTERS AND MULTILINGUAL</h3>
        <div class="rp-item">
          <div class="rp-item-desc"><span>Cyrillic: Привет | German: Grüße | Spanish: Canción | Japanese: こんにちは</span></div>
        </div>
      </section>
      <div style="height:600px;display:block;"></div>
      <section class="rp-section"><h3 class="rp-stitle">EDUCATION</h3>
        <div class="rp-item"><div class="rp-item-head"><strong>SMU (Page Two Verification Section)</strong></div>
          <div class="rp-item-sub"><span>Btech in VFX</span></div>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">CERTIFICATIONS</h3>
        <ul class="rp-desc-list"><li><strong>X telugu editor certification</strong></li></ul>
      </section>
    </main>`;

  return wrapHtml(templateId, headingFont, bodyFont, body, options);
}

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 2: REAL RESUME FIXTURE DEFINITIONS
// ─────────────────────────────────────────────────────────────────────────────

const FIXTURES = [

  // ── 1. STUDENT RESUME ─────────────────────────────────────────────────────
  {
    id: 'student',
    label: 'Student Resume',
    template: 'ats_entry',
    font: 'inter',
    headingFont: "'Inter', sans-serif",
    bodyFont: "'Inter', sans-serif",
    expectedLinks: [
      { type: 'email',   term: 'mailto:student@vr.co' },
      { type: 'phone',   term: 'tel:+15550100' },
      { type: 'GitHub',  term: 'https://github.com/student-dev' },
      { type: 'website', term: 'https://studentportfolio.vr.co' },
    ],
    expectedTokens: [
      'Jane Student', 'EDUCATION', 'B.S. in Computer Science', 'GPA',
      'Data Structures', 'EXPERIENCE', 'Intern Software Engineer', 'Acme Software',
      'PROJECTS', 'Portfolio Website', 'CERTIFICATIONS', 'AWS Cloud Practitioner',
      'AWARDS', "Dean's List",
    ],
    orderedSections: ['EDUCATION', 'EXPERIENCE', 'PROJECTS', 'CERTIFICATIONS', 'AWARDS'],
    sectionIsolation: [
      { title: 'EDUCATION',     uniqueContent: ['B.S. in Computer Science', 'GPA', 'Data Structures'] },
      { title: 'EXPERIENCE',    uniqueContent: ['Intern Software Engineer', 'Acme Software'] },
      { title: 'CERTIFICATIONS',uniqueContent: ['AWS Cloud Practitioner'] },
      { title: 'AWARDS',        uniqueContent: ["Dean's List"] },
    ],
    requireMultiPage: false,
    canonical: 'Jane Student EDUCATION B.S. Computer Science GPA Data Structures EXPERIENCE Intern Software Engineer Acme Software PROJECTS Portfolio Website CERTIFICATIONS AWS Cloud Practitioner AWARDS Deans List student@vr.co +15550100 github.com/student-dev studentportfolio.vr.co',
    body: () => `
    <header class="rp-header rp-ats-header rp-ats-header-left">
      <h1 class="rp-ats-name">Jane Student</h1>
      <p class="rp-role">Software Engineering Student</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:student@vr.co">student@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="tel:+15550100">+1-555-0100</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://github.com/student-dev">github.com/student-dev</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://studentportfolio.vr.co">studentportfolio.vr.co</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">
      <section class="rp-section"><h3 class="rp-stitle">EDUCATION</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>State University</strong></div>
          <div class="rp-item-sub"><span>B.S. in Computer Science</span><span> | GPA: 3.9/4.0</span></div>
          <ul class="rp-desc-list"><li>Relevant Coursework: Data Structures, Algorithms, Operating Systems</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">EXPERIENCE</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Intern Software Engineer</strong><span> — Acme Software</span></div>
          <div class="rp-item-sub"><span>June 2024 – August 2024</span></div>
          <ul class="rp-desc-list"><li>Built REST APIs using Spring Boot and reduced response latency by 30%.</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">PROJECTS</h3>
        <div class="rp-item">
          <strong>Portfolio Website</strong>
          <ul class="rp-desc-list"><li>Built a responsive portfolio using React and deployed to Vercel.</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">CERTIFICATIONS</h3>
        <ul class="rp-desc-list"><li>AWS Cloud Practitioner — Amazon Web Services (2024)</li></ul>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">AWARDS</h3>
        <ul class="rp-desc-list"><li>Dean's List — Fall 2023, Spring 2024</li></ul>
      </section>
    </main>`,
  },

  // ── 2. EXPERIENCED RESUME ─────────────────────────────────────────────────
  {
    id: 'experienced',
    label: 'Experienced Resume',
    template: 'premium2',
    font: 'sora',
    headingFont: "'Sora', sans-serif",
    bodyFont: "'Inter', sans-serif",
    expectedLinks: [
      { type: 'email',    term: 'mailto:architect@vr.co' },
      { type: 'phone',    term: 'tel:+15550200' },
      { type: 'LinkedIn', term: 'https://linkedin.com/in/architect-john' },
    ],
    expectedTokens: [
      'John Architect', 'SUMMARY', 'Senior Software Architect',
      'EXPERIENCE', 'Principal Architect', 'CloudTech', 'Kubernetes',
      'Senior Developer', 'FinTech', 'PROJECTS', 'Microservice Platform',
      'SKILLS', 'Go', 'TypeScript',
    ],
    orderedSections: ['SUMMARY', 'EXPERIENCE', 'PROJECTS', 'SKILLS'],
    sectionIsolation: [
      { title: 'SUMMARY',    uniqueContent: ['Senior Software Architect'] },
      { title: 'EXPERIENCE', uniqueContent: ['Principal Architect', 'CloudTech', 'Senior Developer', 'FinTech', 'Kubernetes'] },
      { title: 'PROJECTS',   uniqueContent: ['Microservice Platform'] },
      { title: 'SKILLS',     uniqueContent: ['Go', 'TypeScript'] },
    ],
    requireMultiPage: false,
    canonical: 'John Architect SUMMARY Senior Software Architect EXPERIENCE Principal Architect CloudTech Kubernetes Senior Developer FinTech PROJECTS Microservice Platform SKILLS Go TypeScript architect@vr.co +15550200 linkedin.com/in/architect-john',
    body: () => `
    <header class="rp-header rp-ats-header rp-ats-header-left">
      <h1 class="rp-ats-name">John Architect</h1>
      <p class="rp-role">Senior Software Architect</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:architect@vr.co">architect@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="tel:+15550200">+1-555-0200</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://linkedin.com/in/architect-john">linkedin.com/in/architect-john</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">
      <section class="rp-section"><h3 class="rp-stitle">SUMMARY</h3>
        <div class="rp-item"><div class="rp-item-desc">Senior Software Architect with 12+ years designing distributed systems and cloud-native infrastructure at scale.</div></div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">EXPERIENCE</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Principal Architect</strong><span> — CloudTech Inc.</span></div>
          <div class="rp-item-sub"><span>2020 – Present</span></div>
          <ul class="rp-desc-list">
            <li>Architected microservices platform using Kubernetes, reducing deployment lead time by 60%.</li>
            <li>Led migration of monolith to event-driven architecture serving 50M users.</li>
          </ul>
        </div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Senior Developer</strong><span> — FinTech Solutions</span></div>
          <div class="rp-item-sub"><span>2016 – 2020</span></div>
          <ul class="rp-desc-list"><li>Built high-frequency trading backends processing 1M transactions/sec.</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">PROJECTS</h3>
        <div class="rp-item"><strong>Microservice Platform</strong>
          <ul class="rp-desc-list"><li>Open-sourced platform for containerized microservices, adopted by 200+ companies.</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">SKILLS</h3>
        <ul class="rp-desc-list"><li>Go, TypeScript, Rust, Kubernetes, Kafka, PostgreSQL, Redis, Terraform</li></ul>
      </section>
    </main>`,
  },

  // ── 3. ACADEMIC CV ────────────────────────────────────────────────────────
  {
    id: 'academic',
    label: 'Academic CV',
    template: 'academic_cv',
    font: 'lora',
    headingFont: "'Lora', Georgia, serif",
    bodyFont: "'Lora', Georgia, serif",
    expectedLinks: [
      { type: 'email',   term: 'mailto:scholar@vr.co' },
      { type: 'website', term: 'https://vr.co/scholar-profile' },
    ],
    expectedTokens: [
      'Dr. Sarah Scholar', 'RESEARCH', 'Postdoctoral Fellow', 'AI Research Lab',
      'PUBLICATIONS', 'Deep Learning for PDF Parsing', 'Skia Layout Engine Analysis',
      'AWARDS', 'Outstanding Research Award',
      'TEACHING', 'Teaching Assistant',
    ],
    orderedSections: ['RESEARCH', 'PUBLICATIONS', 'AWARDS', 'TEACHING'],
    sectionIsolation: [
      { title: 'RESEARCH',     uniqueContent: ['Postdoctoral Fellow', 'AI Research Lab'] },
      { title: 'PUBLICATIONS', uniqueContent: ['Deep Learning for PDF Parsing', 'Skia Layout Engine Analysis'] },
      { title: 'AWARDS',       uniqueContent: ['Outstanding Research Award'] },
      { title: 'TEACHING',     uniqueContent: ['Teaching Assistant'] },
    ],
    requireMultiPage: false,
    canonical: 'Dr Sarah Scholar RESEARCH Postdoctoral Fellow AI Research Lab PUBLICATIONS Deep Learning for PDF Parsing Skia Layout Engine Analysis AWARDS Outstanding Research Award TEACHING Teaching Assistant scholar@vr.co vr.co/scholar-profile',
    body: () => `
    <header class="rp-header rp-ats-header rp-academic-header">
      <h1 class="rp-ats-name rp-academic-name">Dr. Sarah Scholar</h1>
      <p class="rp-role rp-academic-role">Computational Linguistics Researcher</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:scholar@vr.co">scholar@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://vr.co/scholar-profile">vr.co/scholar-profile</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">
      <section class="rp-section"><h3 class="rp-stitle">RESEARCH</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Postdoctoral Fellow</strong><span> — AI Research Lab, MIT</span></div>
          <div class="rp-item-sub"><span>2022 – Present</span></div>
          <ul class="rp-desc-list"><li>Investigating neural architectures for information extraction from scanned academic PDFs.</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">PUBLICATIONS</h3>
        <ul class="rp-desc-list rp-publications-list">
          <li class="rp-citation-item">
            <strong class="rp-citation-title">Deep Learning for PDF Parsing</strong>
            <span class="rp-citation-date"> (2023)</span>
            <div class="rp-citation-authors">IEEE NLP Journal. Co-author: Dr. Alan Turing.</div>
          </li>
          <li class="rp-citation-item">
            <strong class="rp-citation-title">Skia Layout Engine Analysis</strong>
            <span class="rp-citation-date"> (2022)</span>
            <div class="rp-citation-authors">ACM SIGCHI. Solo authored.</div>
          </li>
        </ul>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">AWARDS</h3>
        <ul class="rp-desc-list rp-awards-list">
          <li><strong>Outstanding Research Award</strong> (MIT, 2023)</li>
          <li><strong>Best Paper — ACM SIGCHI</strong> (2022)</li>
        </ul>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">TEACHING</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Teaching Assistant</strong><span> — NLP Graduate Course</span></div>
          <div class="rp-item-sub"><span>Fall 2021</span></div>
        </div>
      </section>
    </main>`,
  },

  // ── 4. MULTI-PAGE RESUME ──────────────────────────────────────────────────
  {
    id: 'multipage',
    label: 'Multi-page Resume',
    template: 'premium4',
    font: 'playfair',
    headingFont: "'Playfair Display', serif",
    bodyFont: "'Lato', sans-serif",
    expectedLinks: [
      { type: 'email',   term: 'mailto:exec@vr.co' },
      { type: 'phone',   term: 'tel:+15550400' },
      { type: 'website', term: 'https://vr.co/exec-demo' },
    ],
    expectedTokens: [
      'Robert Executive', 'SUMMARY', 'Executive VP',
      'EXPERIENCE', 'PROJECTS', 'Global Supply Chain',
      'EDUCATION', 'MBA', 'Harvard Business School',
      'Page Two Continuity Marker',
    ],
    orderedSections: ['SUMMARY', 'EXPERIENCE', 'PROJECTS', 'EDUCATION'],
    sectionIsolation: [
      { title: 'SUMMARY',    uniqueContent: ['Executive VP'] },
      { title: 'EXPERIENCE', uniqueContent: ['Global Operations Director'] },
      { title: 'PROJECTS',   uniqueContent: ['Global Supply Chain'] },
      { title: 'EDUCATION',  uniqueContent: ['MBA', 'Harvard Business School', 'Page Two Continuity Marker'] },
    ],
    requireMultiPage: true,
    canonical: 'Robert Executive SUMMARY Executive VP EXPERIENCE Global Operations Director PROJECTS Global Supply Chain EDUCATION MBA Harvard Business School Page Two Continuity Marker exec@vr.co +15550400 vr.co/exec-demo',
    body: () => `
    <header class="rp-header rp-ats-header rp-ats-header-left">
      <h1 class="rp-ats-name">Robert Executive</h1>
      <p class="rp-role">Executive Vice President, Operations</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:exec@vr.co">exec@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="tel:+15550400">+1-555-0400</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://vr.co/exec-demo">vr.co/exec-demo</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">
      <section class="rp-section"><h3 class="rp-stitle">SUMMARY</h3>
        <div class="rp-item"><div class="rp-item-desc">Executive VP with 20 years of operational leadership across Fortune 500 companies, specialising in digital transformation and supply chain optimisation at global scale.</div></div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">EXPERIENCE</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Global Operations Director</strong><span> — MegaCorp International</span></div>
          <div class="rp-item-sub"><span>2015 – Present</span></div>
          <ul class="rp-desc-list">
            <li>Oversaw 10,000-person global operations spanning 40 countries.</li>
            <li>Drove $2B in operational savings through process reengineering.</li>
            <li>Launched AI-powered forecasting platform reducing inventory waste by 35%.</li>
            <li>Negotiated strategic partnerships with top-tier suppliers across Asia Pacific.</li>
            <li>Championed ESG initiatives resulting in 25% reduction in carbon footprint.</li>
          </ul>
        </div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Regional VP, Americas</strong><span> — Globalink Corp</span></div>
          <div class="rp-item-sub"><span>2010 – 2015</span></div>
          <ul class="rp-desc-list">
            <li>Led regional P&amp;L of $800M across 12 business units.</li>
            <li>Grew regional revenue by 42% through market expansion strategy.</li>
          </ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">PROJECTS</h3>
        <div class="rp-item"><strong>Global Supply Chain</strong>
          <ul class="rp-desc-list"><li>Led end-to-end transformation of global supply chain, saving $500M annually.</li></ul>
        </div>
      </section>
      <!-- spacer to push EDUCATION to page 2 -->
      <div style="height:700px;display:block;"></div>
      <section class="rp-section"><h3 class="rp-stitle">EDUCATION</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Harvard Business School</strong></div>
          <div class="rp-item-sub"><span>MBA, General Management</span></div>
          <div class="rp-item-desc">Page Two Continuity Marker</div>
        </div>
      </section>
    </main>`,
  },

  // ── 5. SIDEBAR RESUME ─────────────────────────────────────────────────────
  {
    id: 'sidebar',
    label: 'Sidebar Resume',
    template: 'premium6',
    font: 'outfit',
    headingFont: "'Outfit', sans-serif",
    bodyFont: "'DM Sans', sans-serif",
    expectedLinks: [
      { type: 'email',  term: 'mailto:designer@vr.co' },
      { type: 'GitHub', term: 'https://github.com/designer-art' },
    ],
    expectedTokens: [
      'Jane Designer', 'SUMMARY', 'Lead Visual Designer', 'Studio Design', 'Design System',
      'SKILLS', 'Figma', 'LANGUAGES', 'Spanish', 'INTERESTS', 'Photography',
    ],
    orderedSections: ['SUMMARY', 'SKILLS', 'LANGUAGES', 'INTERESTS'],
    sectionIsolation: [
      { title: 'SKILLS',     uniqueContent: ['Figma', 'Illustrator'] },
      { title: 'LANGUAGES',  uniqueContent: ['Spanish', 'French'] },
      { title: 'INTERESTS',  uniqueContent: ['Photography', 'Typography'] },
    ],
    requireMultiPage: false,
    canonical: 'Jane Designer SUMMARY Lead Visual Designer Studio Design Design System SKILLS Figma Illustrator LANGUAGES Spanish French INTERESTS Photography Typography designer@vr.co github.com/designer-art',
    body: () => `
    <header class="rp-header rp-ats-header rp-ats-header-left">
      <h1 class="rp-ats-name">Jane Designer</h1>
      <p class="rp-role">Lead Visual Designer</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:designer@vr.co">designer@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://github.com/designer-art">github.com/designer-art</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">
      <section class="rp-section"><h3 class="rp-stitle">SUMMARY</h3>
        <div class="rp-item"><div class="rp-item-desc">Lead Visual Designer at Studio Design with 8 years crafting brand identities, design systems, and interactive product experiences.</div></div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">EXPERIENCE</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Lead Designer</strong><span> — Studio Design</span></div>
          <div class="rp-item-sub"><span>2019 – Present</span></div>
          <ul class="rp-desc-list"><li>Directed creation of the company Design System used by 50+ product teams.</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">SKILLS</h3>
        <ul class="rp-desc-list"><li>Figma, Sketch, Illustrator, Photoshop, After Effects, Principle, Zeplin</li></ul>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">LANGUAGES</h3>
        <ul class="rp-desc-list"><li>English (Native)</li><li>Spanish (Professional)</li><li>French (Conversational)</li></ul>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">INTERESTS</h3>
        <ul class="rp-desc-list"><li>Photography</li><li>Typography</li><li>Open-source design tooling</li></ul>
      </section>
    </main>`,
  },

  // ── 6. HIGH-DENSITY RESUME ────────────────────────────────────────────────
  {
    id: 'highdensity',
    label: 'High-Density Resume',
    template: 'ats_classic',
    font: 'spacegrotesk',
    headingFont: "'Space Grotesk', sans-serif",
    bodyFont: "'Inter', sans-serif",
    options: { highDensity: true },
    expectedLinks: [
      { type: 'email',    term: 'mailto:dense@vr.co' },
      { type: 'phone',    term: 'tel:+15550600' },
      { type: 'LinkedIn', term: 'https://linkedin.com/in/dave-dev' },
    ],
    expectedTokens: [
      'Dave Developer', 'EXPERIENCE', 'Senior Software Engineer', 'Systems Corp',
      'PROJECTS', 'Distributed Database',
      'SKILLS', 'Docker', 'Rust',
    ],
    orderedSections: ['EXPERIENCE', 'PROJECTS', 'SKILLS'],
    sectionIsolation: [
      { title: 'EXPERIENCE', uniqueContent: ['Senior Software Engineer', 'Systems Corp'] },
      { title: 'PROJECTS',   uniqueContent: ['Distributed Database'] },
      // Use terms that appear ONLY in the SKILLS line, not in EXPERIENCE bullets
      { title: 'SKILLS',     uniqueContent: ['gRPC', 'Terraform'] },
    ],
    requireMultiPage: false,
    canonical: 'Dave Developer EXPERIENCE Senior Software Engineer Systems Corp PROJECTS Distributed Database SKILLS Docker Rust dense@vr.co +15550600 linkedin.com/in/dave-dev',
    body: () => `
    <header class="rp-header rp-ats-header rp-ats-header-left">
      <h1 class="rp-ats-name">Dave Developer</h1>
      <p class="rp-role">Senior Software Engineer</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:dense@vr.co">dense@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="tel:+15550600">+1-555-0600</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://linkedin.com/in/dave-dev">linkedin.com/in/dave-dev</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">
      <section class="rp-section"><h3 class="rp-stitle">EXPERIENCE</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Senior Software Engineer</strong><span> — Systems Corp</span></div>
          <div class="rp-item-sub"><span>2020 – Present</span></div>
          <ul class="rp-desc-list">
            <li>Built distributed consensus module handling 500k ops/sec.</li>
            <li>Rewrote storage engine in Rust, cutting memory usage by 40%.</li>
            <li>Automated CI/CD pipeline using Docker and GitHub Actions.</li>
            <li>Optimised SQL query planner improving analytics latency by 55%.</li>
            <li>Mentored 8 junior engineers on systems design and code review practices.</li>
          </ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">PROJECTS</h3>
        <div class="rp-item"><strong>Distributed Database</strong>
          <ul class="rp-desc-list"><li>Open-source Raft-based database written in Rust with 2k GitHub stars.</li></ul>
        </div>
      </section>
      <section class="rp-section"><h3 class="rp-stitle">SKILLS</h3>
        <ul class="rp-desc-list"><li>Rust, Go, C++, Docker, Kubernetes, PostgreSQL, Redis, gRPC, Terraform, Linux</li></ul>
      </section>
    </main>`,
  },

  // ── 7. CUSTOM SECTIONS RESUME ─────────────────────────────────────────────
  {
    id: 'customsections',
    label: 'Custom Sections Resume',
    template: 'ats_lead',
    font: 'inter',
    headingFont: "'Inter', sans-serif",
    bodyFont: "'Inter', sans-serif",
    expectedLinks: [
      { type: 'email',   term: 'mailto:custom@vr.co' },
      { type: 'phone',   term: 'tel:+15550700' },
      { type: 'website', term: 'https://vr.co/custom-profile' },
      { type: 'GitHub',  term: 'https://github.com/jc-oss' },
    ],
    expectedTokens: [
      'Jane Custom',
      'HACKATHONS',     'DappFlow Hackathon',
      'OPEN SOURCE',    'facebook/react',
      'VOLUNTEERING',   'Girls Who Code',
      'LEADERSHIP',     'Founder / Organizer',
      'RESEARCH',       'Neural Networks',
      'PATENTS',        'Decentralized Consensus',
      'TECHNICAL PROFILES', 'LeetCode',
      'PUBLICATIONS',   'Deep Learning Methods',
    ],
    orderedSections: [
      'HACKATHONS', 'OPEN SOURCE', 'VOLUNTEERING',
      'LEADERSHIP', 'RESEARCH', 'PATENTS',
      'TECHNICAL PROFILES', 'PUBLICATIONS',
    ],
    sectionIsolation: [
      { title: 'HACKATHONS',        uniqueContent: ['DappFlow Hackathon', 'ETHDenver'] },
      { title: 'OPEN SOURCE',       uniqueContent: ['facebook/react', 'Pull Request Author'] },
      { title: 'VOLUNTEERING',      uniqueContent: ['Girls Who Code', 'Volunteer Coding'] },
      { title: 'LEADERSHIP',        uniqueContent: ['Founder / Organizer', 'Open Source Group'] },
      { title: 'RESEARCH',          uniqueContent: ['Neural Networks', 'MIT AI Lab'] },
      { title: 'PATENTS',           uniqueContent: ['Decentralized Consensus', 'US Patent'] },
      { title: 'TECHNICAL PROFILES',uniqueContent: ['LeetCode', 'Top 1.5%'] },
      { title: 'PUBLICATIONS',      uniqueContent: ['Deep Learning Methods', 'IEEE'] },
    ],
    requireMultiPage: false,
    canonical: 'Jane Custom HACKATHONS DappFlow Hackathon ETHDenver OPEN SOURCE facebook react Pull Request Author VOLUNTEERING Girls Who Code Volunteer Coding LEADERSHIP Founder Organizer Open Source Group RESEARCH Neural Networks MIT AI Lab PATENTS Decentralized Consensus US Patent TECHNICAL PROFILES LeetCode Top 15 PUBLICATIONS Deep Learning Methods IEEE custom@vr.co +15550700 vr.co/custom-profile github.com/jc-oss',
    body: () => `
    <header class="rp-header rp-ats-header rp-ats-header-left">
      <h1 class="rp-ats-name">Jane Custom</h1>
      <p class="rp-role">Full-Stack Engineer &amp; Open-Source Contributor</p>
      <div class="rp-contact rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="mailto:custom@vr.co">custom@vr.co</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="tel:+15550700">+1-555-0700</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://vr.co/custom-profile">vr.co/custom-profile</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://github.com/jc-oss">github.com/jc-oss</a></span>
      </div>
    </header>
    <main class="rp-ats-body rp-main-col">

      <section class="rp-section"><h3 class="rp-stitle">HACKATHONS</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>DappFlow Hackathon</strong><span> — ETHDenver 2026</span></div>
          <div class="rp-item-sub"><span>February 2026</span></div>
          <ul class="rp-desc-list"><li>Built a decentralized app using Solidity and React. Placed Top 10 of 500 projects.</li></ul>
        </div>
      </section>

      <section class="rp-section"><h3 class="rp-stitle">OPEN SOURCE</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>facebook/react</strong><span> — Pull Request Author</span></div>
          <ul class="rp-desc-list"><li>Optimized reconciliation logic reducing memory allocations by 8%. Merged 2024.</li></ul>
        </div>
      </section>

      <section class="rp-section"><h3 class="rp-stitle">VOLUNTEERING</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Volunteer Coding Instructor</strong><span> — Girls Who Code</span></div>
          <div class="rp-item-sub"><span>September 2024 – Present</span></div>
          <ul class="rp-desc-list"><li>Mentored 30 high school students in Python and web development fundamentals.</li></ul>
        </div>
      </section>

      <section class="rp-section"><h3 class="rp-stitle">LEADERSHIP</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Founder / Organizer</strong><span> — Open Source Group</span></div>
          <div class="rp-item-sub"><span>January 2025 – Present</span></div>
          <ul class="rp-desc-list"><li>Grew community to 400 members. Organised bi-monthly open-source hackathons.</li></ul>
        </div>
      </section>

      <section class="rp-section"><h3 class="rp-stitle">RESEARCH</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Neural Networks for Edge Anomaly Detection</strong><span> — MIT AI Lab</span></div>
          <div class="rp-item-sub"><span>January 2025 – Present</span></div>
          <ul class="rp-desc-list"><li>Designed LSTM-based anomaly detection models improving detection accuracy by 14%.</li></ul>
        </div>
      </section>

      <section class="rp-section"><h3 class="rp-stitle">PATENTS</h3>
        <ul class="rp-desc-list rp-publications-list">
          <li class="rp-citation-item">
            <strong class="rp-citation-title">Decentralized Consensus Protocol for High-Throughput Networks</strong>
            <span class="rp-citation-date"> (2025)</span>
            <div class="rp-citation-authors">US Patent App 12/345,678 — Pending</div>
          </li>
        </ul>
      </section>

      <section class="rp-section"><h3 class="rp-stitle">TECHNICAL PROFILES</h3>
        <div class="rp-item">
          <div class="rp-item-head"><strong>LeetCode</strong><span> — jc-oss</span></div>
          <ul class="rp-desc-list"><li>Top 1.5% worldwide rating. 800+ problems solved across all difficulty levels.</li></ul>
        </div>
      </section>

      <section class="rp-section"><h3 class="rp-stitle">PUBLICATIONS</h3>
        <ul class="rp-desc-list rp-publications-list">
          <li class="rp-citation-item">
            <strong class="rp-citation-title">Deep Learning Methods for Document Parsing</strong>
            <span class="rp-citation-date"> (2024)</span>
            <div class="rp-citation-authors">IEEE Journal of AI Research. Co-author: Dr. Alan Turing.</div>
          </li>
        </ul>
      </section>

    </main>`,
  },
];

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 2: RUN A SINGLE FIXTURE
// ─────────────────────────────────────────────────────────────────────────────

async function runFixture(browser, fixture, tempDir) {
  const { id, label, template, font, headingFont, bodyFont, options = {}, body,
          expectedLinks, expectedTokens, orderedSections, sectionIsolation,
          requireMultiPage, canonical } = fixture;

  const html      = wrapHtml(template, headingFont, bodyFont, body(), options);
  const htmlPath  = path.join(tempDir, `fixture-${id}.html`);
  const pdfPath   = path.join(tempDir, `fixture-${id}.pdf`);
  fs.writeFileSync(htmlPath, html);

  const page = await browser.newPage();
  await page.setViewport({ width: 816, height: 1056 });
  await page.emulateMediaType('print');
  await page.setContent(html, { waitUntil: 'networkidle0' });
  await page.evaluateHandle('document.fonts.ready');
  await page.pdf({ path: pdfPath, format: 'letter', printBackground: true });
  await page.close();

  const pdfBuffer    = fs.readFileSync(pdfPath);
  const pdfData      = await pdfParse(pdfBuffer);
  const extractedText = pdfData.text;
  const pdfBinary    = pdfBuffer.toString('binary');

  const failures = [];

  // 1. HYPERLINK ANNOTATIONS
  const linkStatuses = {};
  for (const link of expectedLinks) {
    let textPresent;
    if (link.type === 'phone') {
      const digits = link.term.replace(/\D/g, '');
      textPresent   = extractedText.replace(/\D/g, '').includes(digits);
    } else {
      // PDF visible text never contains mailto:/https:/ — strip all protocol prefixes
      const searchTerm = link.term.replace(/^(mailto:|tel:|https?:\/\/)/i, '');
      textPresent = extractedText.toLowerCase().includes(searchTerm.toLowerCase());
    }
    const escaped    = escapeRegex(link.term);
    const re         = link.term.endsWith('/')
      ? new RegExp(`\\/URI\\s*\\(${escaped}\\)`, 'i')
      : new RegExp(`\\/URI\\s*\\(${escaped}\\/?\\)`, 'i');
    const hasAnnot   = re.test(pdfBinary);
    linkStatuses[link.type] = { textPresent, hasAnnotation: hasAnnot };
    if (!textPresent) failures.push(`Missing link text: ${link.term}`);
    if (!hasAnnot)    failures.push(`Missing /URI annotation: ${link.term}`);
  }

  // 2. TOKEN PRESENCE & WORD-SPLIT CHECK
  const tokenResults = {};
  for (const token of expectedTokens) {
    const split = detectWordSplit(extractedText, token);
    tokenResults[token] = split;
    if (split === 'not_found') failures.push(`Missing token: "${token}"`);
    else if (split)           failures.push(`Word split: "${token}" → "${split}"`);
  }

  // 3. SECTION ORDER
  const orderError = verifySectionOrder(extractedText, orderedSections);
  if (orderError) {
    if (orderError.type === 'missing_title') failures.push(`Missing section title: "${orderError.title}"`);
    else failures.push(`Wrong section order: ${orderError.message}`);
  }

  // 4. SECTION ISOLATION (leakage check)
  const leakageErrors = verifySectionIsolation(extractedText, sectionIsolation);
  if (leakageErrors) {
    for (const err of leakageErrors) failures.push(`Section leakage: ${err}`);
  }

  // 5. CHARACTER INTEGRITY
  const { missing: charMissing } = compareCharacterCounts(canonical, extractedText);
  if (charMissing > 0) failures.push(`Character loss: ${charMissing} alphanumeric chars missing`);

  // 6. MULTI-PAGE CONTINUITY
  const pages = pdfData.numpages;
  if (requireMultiPage && pages < 2) failures.push(`Multi-page failure: expected ≥2 pages, got ${pages}`);

  // cleanup
  fs.unlinkSync(htmlPath);
  fs.unlinkSync(pdfPath);

  return {
    id, label, template, font,
    passed: failures.length === 0,
    failures,
    pages,
    linkStatuses,
    tokenResults,
    charMissing,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// PHASE 1: SYNTHETIC MATRIX (run single combination)
// ─────────────────────────────────────────────────────────────────────────────

const SYNTHETIC_TOKENS = [
  'Rithik','Mettu','VFX','SUPERVISOR','qa@vr.co',
  'rithik-qa','proj','tw.vr.co',
  'Kubernetes','TypeScript','Skia-engine','GPOS-ligature',
  'Establishmentarianism','Supercalifragilisticexpialidocious','Incomprehensibility',
  'Привет','Grüße','Canción','こんにちは',
  'Page','Verification','TECHNICAL','TERMS','PROJECTS',
  'SPECIAL','CHARACTERS','MULTILINGUAL','EDUCATION','CERTIFICATIONS',
];

const SYNTHETIC_LINKS = [
  { type: 'email',        term: 'mailto:qa@vr.co' },
  { type: 'phone',        term: 'tel:+911234567890' },
  { type: 'LinkedIn',     term: 'https://linkedin.com/in/rithik-qa' },
  { type: 'GitHub',       term: 'https://github.com/vr/proj' },
  { type: 'website',      term: 'https://vr.co/audit' },
  { type: 'project links',term: 'https://tw.vr.co' },
];

const SYNTHETIC_CANONICAL = `
  Rithik Mettu VFX SUPERVISOR qa@vr.co +911234567890 Hyd ind
  linkedin.com/in/rithik-qa github.com/vr/proj vr.co/audit
  Technical Terms Kubernetes Container TypeScript Statically Skia-engine GPOS-ligature
  Establishmentarianism Incomprehensibility Supercalifragilisticexpialidocious Twitter tw.vr.co
  Cyrillic Привет German Grüße Spanish Canción Japanese こんにちは
  SMU Page Two Verification Section Btech VFX CERTIFICATIONS X telugu editor
`;

async function runSyntheticTest(browser, test, tempDir) {
  const { template, font, options } = test;
  const testId    = `${template}_${font}`;
  const html      = generateSyntheticHtml(template, font, options);
  const htmlPath  = path.join(tempDir, `${testId}.html`);
  const pdfPath   = path.join(tempDir, `${testId}.pdf`);
  fs.writeFileSync(htmlPath, html);

  const page = await browser.newPage();
  await page.setViewport({ width: 816, height: 1056 });
  await page.emulateMediaType('print');
  await page.setContent(html, { waitUntil: 'networkidle0' });
  await page.evaluateHandle('document.fonts.ready');
  await page.pdf({ path: pdfPath, format: 'letter', printBackground: true });
  await page.close();

  const pdfBuffer     = fs.readFileSync(pdfPath);
  const pdfData       = await pdfParse(pdfBuffer);
  const extractedText = pdfData.text;
  const pdfBinary     = pdfBuffer.toString('binary');

  const wordSplits = [], missingTokens = [];
  for (const token of SYNTHETIC_TOKENS) {
    const s = detectWordSplit(extractedText, token);
    if (s === 'not_found') missingTokens.push(token);
    else if (s)            wordSplits.push({ original: token, splitAs: s });
  }

  const linkStatuses = {};
  let   linkFail = false;
  for (const link of SYNTHETIC_LINKS) {
    let textPresent;
    if (link.type === 'phone') {
      const digits = link.term.replace(/\D/g, '');
      textPresent   = extractedText.replace(/\D/g, '').includes(digits);
    } else {
      const searchTerm = link.term.replace(/^(mailto:|tel:|https?:\/\/)/i, '');
      textPresent = extractedText.toLowerCase().includes(searchTerm.toLowerCase());
    }
    const escaped = escapeRegex(link.term);
    const re      = link.term.endsWith('/')
      ? new RegExp(`\\/URI\\s*\\(${escaped}\\)`, 'i')
      : new RegExp(`\\/URI\\s*\\(${escaped}\\/?\\)`, 'i');
    const hasAnnot = re.test(pdfBinary);
    linkStatuses[link.type] = { textPresent, hasAnnotation: hasAnnot };
    if (!textPresent || !hasAnnot) linkFail = true;
  }

  const multiPageOk = pdfData.numpages >= 2;
  const { missing: charMissing } = compareCharacterCounts(SYNTHETIC_CANONICAL, extractedText);
  const extractionFailed = wordSplits.length > 0 || missingTokens.length > 0 || charMissing > 0;
  const passed = !extractionFailed && !linkFail && multiPageOk;

  fs.unlinkSync(htmlPath);
  fs.unlinkSync(pdfPath);

  return {
    template, font, passed, options,
    wordSplits, missingTokens,
    characterLoss: charMissing, pages: pdfData.numpages, linkStatuses,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN ENTRY
// ─────────────────────────────────────────────────────────────────────────────

(async () => {
  console.log('====================================================');
  console.log('🛡️  SaaS DEPLOYMENT GATE: FULL PDF EXTRACTION AUDIT');
  console.log('====================================================\n');

  const templates = [
    'template1','template2','template3',
    'premium1','premium2','premium3','premium4','premium5',
    'premium6','premium7','premium8','premium9','premium10',
    'ats_classic','ats_entry','ats_senior','ats_lead','ats_intern',
    'ats_experienced','academic_cv',
  ];
  const fonts = [
    'inter','sora','playfair','outfit','cormorant',
    'spacegrotesk','raleway','jakarta','ibmplex','bodoni',
    'nunito','fraunces','sourcecode','tinos','lora',
  ];

  const syntheticTests = [];
  let idx = 0;
  for (const template of templates) {
    for (const font of fonts) {
      const opts = {
        pageBorder:  idx % 2 === 0 ? 'true' : 'false',
        highDensity: idx % 3 === 0 ? 'true' : 'false',
        bulletStyle: ['circle','square','lower-alpha'][idx % 3],
        fresherMode: idx % 4 === 0 ? 'true' : 'false',
      };
      syntheticTests.push({ template, font, options: opts, index: ++idx });
    }
  }

  console.log(`Phase 1: Synthetic matrix — ${syntheticTests.length} combinations (Concurrency: 8)`);
  console.log(`Phase 2: Real resume fixture suite — ${FIXTURES.length} fixtures\n`);

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage','--font-render-hinting=none'],
  });

  const tempDir = path.join(__dirname, 'temp_deploy_audit');
  if (!fs.existsSync(tempDir)) fs.mkdirSync(tempDir);

  // ── PHASE 1 ───────────────────────────────────────────────────────────────
  const phase1Results  = { passed: true, failures: 0, splitWordCount: 0, missingCharacterCount: 0, missingLinkCount: 0, affectedList: [], details: [] };
  const CONCURRENCY    = 8;
  let   activeIndex    = 0;

  async function syntheticWorker() {
    while (activeIndex < syntheticTests.length) {
      const ci   = activeIndex++;
      const test = syntheticTests[ci];
      const testId = `${test.template}_${test.font}`;
      try {
        const result = await runSyntheticTest(browser, test, tempDir);
        phase1Results.details.push(result);
        if (!result.passed) {
          phase1Results.failures++;
          phase1Results.passed = false;
          phase1Results.splitWordCount       += result.wordSplits.length;
          phase1Results.missingCharacterCount += result.characterLoss;
          phase1Results.missingLinkCount      += Object.values(result.linkStatuses).filter(l => !l.textPresent || !l.hasAnnotation).length;
          phase1Results.affectedList.push({
            template: test.template, font: test.font,
            splits: result.wordSplits, missing: result.missingTokens,
            pageCount: result.pages, charLoss: result.characterLoss,
            linkIssues: Object.keys(result.linkStatuses).filter(k => !result.linkStatuses[k].textPresent || !result.linkStatuses[k].hasAnnotation),
          });
          console.error(`  ❌ FAIL [${ci+1}/${syntheticTests.length}] ${testId}`);
        } else {
          console.log(`  ✔️ PASS [${ci+1}/${syntheticTests.length}] ${testId}`);
        }
      } catch (err) {
        phase1Results.failures++;
        phase1Results.passed = false;
        phase1Results.details.push({ template: test.template, font: test.font, passed: false, error: err.message });
        console.error(`  💥 CRASH [${ci+1}/${syntheticTests.length}] ${testId} — ${err.message}`);
      }
    }
  }

  const workers = [];
  for (let i = 0; i < CONCURRENCY; i++) workers.push(syntheticWorker());
  await Promise.all(workers);

  // ── PHASE 2 ───────────────────────────────────────────────────────────────
  console.log('\n────────────────────────────────────────────────────');
  console.log('Phase 2: Real Resume Fixture Suite');
  console.log('────────────────────────────────────────────────────');

  const phase2Results = { passed: true, failures: 0, details: [] };

  for (const fixture of FIXTURES) {
    try {
      const result = await runFixture(browser, fixture, tempDir);
      phase2Results.details.push(result);
      if (!result.passed) {
        phase2Results.failures++;
        phase2Results.passed = false;
        console.error(`  ❌ FAIL  Fixture [${fixture.id}] "${fixture.label}" — ${result.failures.length} issue(s)`);
        for (const f of result.failures) console.error(`         → ${f}`);
      } else {
        console.log(`  ✔️ PASS  Fixture [${fixture.id}] "${fixture.label}"`);
      }
    } catch (err) {
      phase2Results.failures++;
      phase2Results.passed = false;
      phase2Results.details.push({ id: fixture.id, label: fixture.label, passed: false, failures: [err.message] });
      console.error(`  💥 CRASH Fixture [${fixture.id}] "${fixture.label}" — ${err.message}`);
    }
  }

  await browser.close();
  try { fs.rmdirSync(tempDir); } catch (_) {}

  // ── OVERALL GATE STATUS ───────────────────────────────────────────────────
  const gatePass = phase1Results.passed && phase2Results.passed;

  // ── JSON REPORT ───────────────────────────────────────────────────────────
  const report = {
    timestamp:    new Date().toISOString(),
    passed:       gatePass,
    phase1: {
      totalTests:            syntheticTests.length,
      failures:              phase1Results.failures,
      splitWordCount:        phase1Results.splitWordCount,
      missingCharacterCount: phase1Results.missingCharacterCount,
      missingLinkCount:      phase1Results.missingLinkCount,
      affectedList:          phase1Results.affectedList,
      details:               phase1Results.details,
    },
    phase2: {
      totalFixtures: FIXTURES.length,
      failures:      phase2Results.failures,
      details:       phase2Results.details,
    },
  };
  const jsonPath = path.join(__dirname, 'pdf-audit-report.json');
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2));

  // ── MARKDOWN REPORT ───────────────────────────────────────────────────────
  let md = `# SaaS Deployment Gate: PDF Extraction Quality Audit Report\n\n`;
  md += `* **Timestamp**: ${report.timestamp}\n`;
  md += `* **Gate Status**: ${gatePass ? '🟩 **PASS — READY FOR PRODUCTION**' : '🟥 **FAIL — DEPLOYMENT BLOCKED**'}\n\n`;

  md += `---\n\n## Phase 1: Synthetic Template × Font Matrix\n\n`;
  md += `* **Combinations Run**: ${report.phase1.totalTests}\n`;
  md += `* **Failures**: ${report.phase1.failures}\n`;
  md += `* **Word Splits**: ${report.phase1.splitWordCount}\n`;
  md += `* **Character Loss**: ${report.phase1.missingCharacterCount}\n`;
  md += `* **Missing Link Actions**: ${report.phase1.missingLinkCount}\n\n`;

  md += `| Template | Font | Status | Pages | Splits | Missing Tokens | Links OK | Char Loss |\n`;
  md += `| :--- | :--- | :--- | :---: | :--- | :--- | :---: | :---: |\n`;
  for (const r of phase1Results.details) {
    const status    = r.passed ? '🟩 PASS' : '🟥 FAIL';
    const splits    = r.wordSplits    ? (r.wordSplits.map(s => `${s.original}→"${s.splitAs}"`).join(', ') || 'None') : 'Error';
    const missing   = r.missingTokens ? (r.missingTokens.join(', ') || 'None') : 'Error';
    const linksOk   = r.linkStatuses  ? (Object.values(r.linkStatuses).every(l => l.textPresent && l.hasAnnotation) ? 'Yes' : 'No') : 'Error';
    md += `| \`${r.template}\` | \`${r.font}\` | ${status} | ${r.pages || 0} | ${splits} | ${missing} | ${linksOk} | -${r.characterLoss || 0} |\n`;
  }

  if (phase1Results.affectedList.length) {
    md += `\n### Phase 1 Regression Details\n\n`;
    for (const a of phase1Results.affectedList) {
      md += `#### 🟥 ${a.template} + ${a.font}\n`;
      for (const s of a.splits)  md += `* Word Split: \`${s.original}\` → \`"${s.splitAs}"\`\n`;
      for (const t of a.missing) md += `* Missing Token: \`${t}\`\n`;
      if (a.pageCount < 2)       md += `* Multi-page failure: ${a.pageCount} page(s)\n`;
      if (a.charLoss > 0)        md += `* Character loss: ${a.charLoss} chars\n`;
      if (a.linkIssues.length)   md += `* Link issues: ${a.linkIssues.join(', ')}\n`;
      md += '\n';
    }
  }

  md += `---\n\n## Phase 2: Real Resume Fixture Regression Suite\n\n`;
  md += `* **Fixtures Run**: ${report.phase2.totalFixtures}\n`;
  md += `* **Failures**: ${report.phase2.failures}\n\n`;

  md += `| Fixture | Template | Font | Status | Pages | Issues |\n`;
  md += `| :--- | :--- | :--- | :--- | :---: | :--- |\n`;
  for (const r of phase2Results.details) {
    const status = r.passed ? '🟩 PASS' : '🟥 FAIL';
    const issues = r.failures && r.failures.length ? r.failures.join('; ') : 'None';
    md += `| **${r.label}** | \`${r.template}\` | \`${r.font}\` | ${status} | ${r.pages || 0} | ${issues} |\n`;
  }

  if (!phase2Results.passed) {
    md += `\n### Phase 2 Failure Details\n\n`;
    for (const r of phase2Results.details.filter(d => !d.passed)) {
      md += `#### 🟥 ${r.label} (\`${r.template}\` + \`${r.font}\`)\n`;
      for (const f of r.failures) md += `* ${f}\n`;
      md += '\n';
    }
  }

  const mdPath = path.join(__dirname, 'pdf-audit-report.md');
  fs.writeFileSync(mdPath, md);

  // Copy to artifact dir
  try {
    fs.copyFileSync(mdPath, 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\pdf-audit-report.md');
  } catch (_) {}

  // ── CONSOLE SUMMARY ───────────────────────────────────────────────────────
  console.log('\n====================================================');
  console.log('🛡️  QA DEPLOYMENT GATE: FINAL SUMMARY');
  console.log('====================================================');
  console.log(`Overall Gate Status:         ${gatePass ? '🟩 PASS' : '🟥 FAIL'}`);
  console.log(`Phase 1 — Combinations:      ${report.phase1.totalTests}  Failures: ${report.phase1.failures}`);
  console.log(`Phase 1 — Word Splits:        ${report.phase1.splitWordCount}`);
  console.log(`Phase 1 — Character Loss:     ${report.phase1.missingCharacterCount}`);
  console.log(`Phase 1 — Missing Link Acts:  ${report.phase1.missingLinkCount}`);
  console.log(`Phase 2 — Fixtures:           ${report.phase2.totalFixtures}  Failures: ${report.phase2.failures}`);
  console.log(`JSON Report:                  pdf-audit-report.json`);
  console.log(`Markdown Report:              pdf-audit-report.md`);
  console.log('====================================================\n');

  if (gatePass) {
    console.log('🟩 DEPLOYMENT GATE PASSED. PLATFORM READY FOR PRODUCTION.');
    process.exit(0);
  } else {
    console.error('🟥 DEPLOYMENT GATE BLOCKED. REGRESSIONS DETECTED.');
    process.exit(1);
  }
})();
