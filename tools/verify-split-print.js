/**
 * verify-split-print.js
 * Generates screen vs print comparison screenshots for split layouts.
 * Tests the exact inline styles injected by getPDFBlob() in ResumeEditor.jsx.
 */
const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const FRONTEND_DIST = path.resolve(__dirname, '../../vresiq-frontend/dist');
const ARTIFACTS = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\32478b21-4525-46c8-bf90-a0d7f9eda66a';

function getCSSContent() {
  const assetsDir = path.join(FRONTEND_DIST, 'assets');
  const files = fs.readdirSync(assetsDir);
  const resumeEditorCSS = files.find(f => f.startsWith('ResumeEditor') && f.endsWith('.css'));
  const indexCSS = files.find(f => f.startsWith('index') && f.endsWith('.css'));
  let css = '';
  if (indexCSS) css += fs.readFileSync(path.join(assetsDir, indexCSS), 'utf8') + '\n';
  if (resumeEditorCSS) css += fs.readFileSync(path.join(assetsDir, resumeEditorCSS), 'utf8') + '\n';
  console.log('CSS loaded:', indexCSS, resumeEditorCSS);
  return css;
}

// Inline styles from getPDFBlob() — the EXACT block that gets injected into
// the exported HTML. Must stay in sync with ResumeEditor.jsx lines 765-1212.
const EXPORT_INLINE_STYLES = `
  html, body {
    background: #ffffff !important;
    color: #000000 !important;
    margin: 0 !important;
    padding: 0 !important;
    overflow: hidden !important;
    visibility: visible !important;
  }
  @page {
    size: letter;
    margin-top: 0px;
    margin-bottom: 36px;
    margin-left: 0;
    margin-right: 0;
  }
  body * { visibility: visible !important; }

  #resume-preview, .resume-preview {
    position: static !important;
    display: flow-root !important;
    width: 816px !important;
    max-width: 816px !important;
    height: auto !important;
    min-height: 1056px !important;
    margin: 0 auto !important;
    padding: 0 !important;
    box-shadow: none !important;
    border: none !important;
    overflow: visible !important;
    visibility: visible !important;
    box-sizing: border-box !important;
    background-color: #ffffff !important;
  }

  /* Split layout: flex row so sidebar and main col stretch to equal height */
  .rp-layout-sidebar {
    display: flex !important;
    flex-direction: row !important;
    align-items: stretch !important;
    width: 100% !important;
    height: auto !important;
    box-sizing: border-box !important;
  }
  .rp-layout-sidebar::after { display: none !important; }
  .rp-layout-sidebar > .rp-sidebar {
    width: 210px !important;
    flex-shrink: 0 !important;
    float: none !important;
    display: flex !important;
    flex-direction: column !important;
    align-self: stretch !important;
    min-height: 100% !important;
    margin: 0 !important;
    box-sizing: border-box !important;
  }
  .rp-layout-sidebar > .rp-main-col {
    flex-grow: 1 !important;
    width: calc(100% - 210px) !important;
    float: none !important;
    display: block !important;
    margin: 0 !important;
    box-sizing: border-box !important;
  }

  .rp-sidebar-dark .rp-sidebar-name,
  .rp-sidebar-dark .rp-sidebar-role,
  .rp-sidebar-dark .rp-contact,
  .rp-sidebar-dark .rp-contact a,
  .rp-sidebar-dark .rp-stitle,
  .rp-sidebar-dark .rp-skill-name,
  .rp-sidebar-dark .rp-item-head,
  .rp-sidebar-dark .rp-item-head strong,
  .rp-sidebar-dark .rp-item-sub,
  .rp-sidebar-dark .rp-item-desc,
  .rp-sidebar-dark .rp-item-desc li,
  .rp-sidebar-dark .rp-summary {
    color: #ffffff !important;
  }
`;

function buildHTML(hstyle, sharedCSS, inlineStyles) {
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>${sharedCSS}</style>
  <style>${inlineStyles}</style>
</head>
<body>
<div id="resume-preview"
     class="resume-preview"
     data-template="split"
     data-hstyle="${hstyle}"
     style="--accent:#2563eb;--accent-readable:#1d4ed8;font-family:Inter,sans-serif;">
  <div class="rp-layout-sidebar">
    <aside class="rp-sidebar rp-sidebar-dark">
      <div class="rp-sidebar-header">
        <div class="rp-sidebar-name">Jane Smith</div>
        <div class="rp-sidebar-role">Senior Software Engineer</div>
        <div class="rp-contact" style="margin-top:8px">
          <span class="rp-contact-item">jane@example.com</span>
          <span class="rp-contact-item">+1 555-0100</span>
          <span class="rp-contact-item">linkedin.com/in/janesmith</span>
          <span class="rp-contact-item">New York, NY</span>
        </div>
      </div>
      <div class="rp-section">
        <div class="rp-stitle">Skills</div>
        <div class="rp-skill-row"><span class="rp-skill-name">React</span></div>
        <div class="rp-skill-row"><span class="rp-skill-name">TypeScript</span></div>
        <div class="rp-skill-row"><span class="rp-skill-name">Node.js</span></div>
        <div class="rp-skill-row"><span class="rp-skill-name">Python</span></div>
        <div class="rp-skill-row"><span class="rp-skill-name">PostgreSQL</span></div>
        <div class="rp-skill-row"><span class="rp-skill-name">Docker / AWS</span></div>
      </div>
      <div class="rp-section">
        <div class="rp-stitle">Education</div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>BS Computer Science</strong></div>
          <div class="rp-item-sub">MIT &mdash; 2018</div>
        </div>
      </div>
      <div class="rp-section">
        <div class="rp-stitle">Certifications</div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>AWS Solutions Architect</strong></div>
          <div class="rp-item-sub">Amazon &mdash; 2022</div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Google Cloud Professional</strong></div>
          <div class="rp-item-sub">Google &mdash; 2023</div>
        </div>
      </div>
    </aside>
    <main class="rp-main-col">
      <div class="rp-section">
        <div class="rp-stitle">Summary</div>
        <div class="rp-summary">Experienced software engineer with 7+ years building scalable distributed systems and full-stack web applications. Passionate about clean architecture, developer experience, and delivering high-quality software in collaborative environments.</div>
      </div>
      <div class="rp-section">
        <div class="rp-stitle">Experience</div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Senior Software Engineer</strong><span class="rp-item-date">Jan 2022 – Present</span></div>
          <div class="rp-item-sub">Stripe · San Francisco, CA</div>
          <div class="rp-item-desc"><ul>
            <li>Led migration of monolithic payment processor to microservices, reducing latency by 40%.</li>
            <li>Designed and implemented real-time fraud detection pipeline processing 50K events/sec.</li>
            <li>Mentored 4 junior engineers and established team code review standards.</li>
            <li>Built internal developer tooling that cut CI/CD pipeline time from 18 to 7 minutes.</li>
          </ul></div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Software Engineer</strong><span class="rp-item-date">Jun 2019 – Dec 2021</span></div>
          <div class="rp-item-sub">Airbnb · San Francisco, CA</div>
          <div class="rp-item-desc"><ul>
            <li>Developed core booking flow APIs serving 2M+ daily active users.</li>
            <li>Implemented A/B testing framework that increased conversion rate by 12%.</li>
            <li>Re-architected search ranking system improving relevance scores by 25%.</li>
            <li>Collaborated cross-functionally with design and product to ship 3 major features.</li>
          </ul></div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Junior Software Engineer</strong><span class="rp-item-date">Aug 2018 – May 2019</span></div>
          <div class="rp-item-sub">Palantir · New York, NY</div>
          <div class="rp-item-desc"><ul>
            <li>Built data ingestion pipelines processing 500GB+ daily from disparate sources.</li>
            <li>Optimized SQL queries reducing average report generation time by 60%.</li>
          </ul></div>
        </div>
      </div>
      <div class="rp-section">
        <div class="rp-stitle">Projects</div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Open-Source GraphQL Gateway</strong></div>
          <div class="rp-item-sub">github.com/janesmith/gql-gateway · 2023</div>
          <div class="rp-item-desc"><ul>
            <li>Built a high-performance GraphQL federation gateway in Rust with 2.3k GitHub stars.</li>
            <li>Supports schema stitching, rate limiting, and distributed tracing out of the box.</li>
          </ul></div>
        </div>
      </div>
    </main>
  </div>
</div>
</body>
</html>`;
}

(async () => {
  const css = getCSSContent();
  const styles = [
    { hstyle: 'minimal',    label: 'Minimal' },
    { hstyle: 'card',       label: 'Card' },
    { hstyle: 'full-bleed', label: 'Full Bleed' },
  ];

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
  });

  try {
    for (const { hstyle, label } of styles) {
      console.log(`\n=== Split ${label} ===`);
      // Screen HTML uses shared CSS only (no inline export styles)
      const screenHTML = buildHTML(hstyle, css, '');
      // Print HTML uses shared CSS + the exact inline styles getPDFBlob() injects
      const printHTML  = buildHTML(hstyle, css, EXPORT_INLINE_STYLES);

      // ─── SCREEN screenshot (preview) ───
      const sp = await browser.newPage();
      await sp.setViewport({ width: 816, height: 1056 });
      await sp.setContent(screenHTML, { waitUntil: 'networkidle0' });
      const el = await sp.$('#resume-preview');
      const prevOut = path.join(__dirname, `preview-split-${hstyle}.png`);
      await el.screenshot({ path: prevOut });
      fs.copyFileSync(prevOut, path.join(ARTIFACTS, `preview-split-${hstyle}.png`));
      console.log(`  Preview: ${prevOut}`);
      await sp.close();

      // ─── PRINT screenshot (raw DOM passed to Puppeteer, print emulated) ───
      const pp = await browser.newPage();
      await pp.setViewport({ width: 816, height: 1056 });
      await pp.setContent(printHTML, { waitUntil: 'networkidle0' });
      await pp.emulateMediaType('print');
      await pp.evaluateHandle('document.fonts.ready');
      const printEl = await pp.$('#resume-preview');
      const pdfOut = path.join(__dirname, `pdf-split-${hstyle}.png`);
      await printEl.screenshot({ path: pdfOut });
      fs.copyFileSync(pdfOut, path.join(ARTIFACTS, `pdf-split-${hstyle}.png`));
      console.log(`  Print:   ${pdfOut}`);
      await pp.close();
    }
  } finally {
    await browser.close();
    console.log('\nDone.');
  }
})();
