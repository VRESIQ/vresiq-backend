/**
 * verify-split-print.js
 * Generates print-mode screenshots of a synthetic split-layout resume
 * using the compiled frontend CSS. No login required.
 */
const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

// Path to the compiled CSS bundle from vite build
const FRONTEND_DIST = path.resolve(__dirname, '../../vresiq-frontend/dist');
const ARTIFACTS = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\32478b21-4525-46c8-bf90-a0d7f9eda66a';

function getCSSContent() {
  // Read the compiled ResumeEditor CSS from dist
  const assetsDir = path.join(FRONTEND_DIST, 'assets');
  const files = fs.readdirSync(assetsDir);
  const resumeEditorCSS = files.find(f => f.startsWith('ResumeEditor') && f.endsWith('.css'));
  const indexCSS = files.find(f => f.startsWith('index') && f.endsWith('.css'));
  
  let css = '';
  if (indexCSS) css += fs.readFileSync(path.join(assetsDir, indexCSS), 'utf8') + '\n';
  if (resumeEditorCSS) css += fs.readFileSync(path.join(assetsDir, resumeEditorCSS), 'utf8') + '\n';
  
  console.log('Loaded CSS files:', indexCSS, resumeEditorCSS);
  return css;
}

function buildSplitHTML(hstyle, css) {
  // Synthetic split-layout resume that mimics the real app's DOM structure
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>${css}</style>
  <style>
    html, body { margin: 0; padding: 0; background: #fff; }
    #resume-preview { margin: 0; }
  </style>
</head>
<body>
<div id="resume-preview"
     class="resume-preview"
     data-template="split"
     data-hstyle="${hstyle}"
     style="--accent:#2563eb;--accent-readable:#1d4ed8;font-family:Inter,sans-serif;">

  <div class="rp-layout-sidebar">

    <!-- SIDEBAR -->
    <aside class="rp-sidebar rp-sidebar-dark">
      <div class="rp-sidebar-header">
        <div class="rp-sidebar-name">Jane Smith</div>
        <div class="rp-sidebar-role">Senior Software Engineer</div>
        <div class="rp-contact" style="margin-top:8px">
          <span class="rp-contact-item">jane@example.com</span>
          <span class="rp-contact-item">+1 555-0100</span>
          <span class="rp-contact-item">linkedin.com/in/janesmith</span>
          <span class="rp-contact-item">github.com/janesmith</span>
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
        <div class="rp-skill-row"><span class="rp-skill-name">Docker</span></div>
        <div class="rp-skill-row"><span class="rp-skill-name">AWS</span></div>
        <div class="rp-skill-row"><span class="rp-skill-name">GraphQL</span></div>
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

    <!-- MAIN COLUMN -->
    <main class="rp-main-col">
      <div class="rp-section">
        <div class="rp-stitle">Summary</div>
        <div class="rp-summary">
          Experienced software engineer with 7+ years building scalable distributed systems 
          and full-stack web applications. Passionate about clean architecture, developer 
          experience, and delivering high-quality software in collaborative environments.
        </div>
      </div>

      <div class="rp-section">
        <div class="rp-stitle">Experience</div>

        <div class="rp-item">
          <div class="rp-item-head">
            <strong>Senior Software Engineer</strong>
            <span class="rp-item-date">Jan 2022 – Present</span>
          </div>
          <div class="rp-item-sub">Stripe · San Francisco, CA</div>
          <div class="rp-item-desc">
            <ul>
              <li>Led migration of monolithic payment processor to microservices, reducing latency by 40%.</li>
              <li>Designed and implemented real-time fraud detection pipeline processing 50K events/sec.</li>
              <li>Mentored 4 junior engineers and established team code review standards.</li>
              <li>Built internal developer tooling that cut CI/CD pipeline time from 18 to 7 minutes.</li>
            </ul>
          </div>
        </div>

        <div class="rp-item">
          <div class="rp-item-head">
            <strong>Software Engineer</strong>
            <span class="rp-item-date">Jun 2019 – Dec 2021</span>
          </div>
          <div class="rp-item-sub">Airbnb · San Francisco, CA</div>
          <div class="rp-item-desc">
            <ul>
              <li>Developed core booking flow APIs serving 2M+ daily active users.</li>
              <li>Implemented A/B testing framework that increased conversion rate by 12%.</li>
              <li>Re-architected search ranking system improving relevance scores by 25%.</li>
              <li>Collaborated cross-functionally with design and product to ship 3 major features.</li>
            </ul>
          </div>
        </div>

        <div class="rp-item">
          <div class="rp-item-head">
            <strong>Junior Software Engineer</strong>
            <span class="rp-item-date">Aug 2018 – May 2019</span>
          </div>
          <div class="rp-item-sub">Palantir · New York, NY</div>
          <div class="rp-item-desc">
            <ul>
              <li>Built data ingestion pipelines processing 500GB+ daily from disparate sources.</li>
              <li>Optimized SQL queries reducing average report generation time by 60%.</li>
              <li>Maintained and extended internal analytics dashboard used by 200+ analysts.</li>
            </ul>
          </div>
        </div>
      </div>

      <div class="rp-section">
        <div class="rp-stitle">Projects</div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Open-Source GraphQL Gateway</strong></div>
          <div class="rp-item-sub">github.com/janesmith/gql-gateway · 2023</div>
          <div class="rp-item-desc">
            <ul>
              <li>Built a high-performance GraphQL federation gateway in Rust with 2.3k GitHub stars.</li>
              <li>Supports schema stitching, rate limiting, and distributed tracing out of the box.</li>
            </ul>
          </div>
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
    { hstyle: 'minimal', label: 'Minimal' },
    { hstyle: 'card',    label: 'Card' },
    { hstyle: 'full-bleed', label: 'Full Bleed' },
  ];

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
  });

  try {
    for (const { hstyle, label } of styles) {
      console.log(`\n=== Split ${label} (data-hstyle="${hstyle}") ===`);
      const html = buildSplitHTML(hstyle, css);

      // ─── SCREEN (preview) screenshot ───
      const screenPage = await browser.newPage();
      await screenPage.setViewport({ width: 816, height: 1056 });
      await screenPage.setContent(html, { waitUntil: 'networkidle0' });
      const previewOut = path.join(__dirname, `preview-split-${hstyle}.png`);
      const previewEl = await screenPage.$('#resume-preview');
      await previewEl.screenshot({ path: previewOut });
      fs.copyFileSync(previewOut, path.join(ARTIFACTS, `preview-split-${hstyle}.png`));
      console.log(`  Screen screenshot: ${previewOut}`);
      await screenPage.close();

      // ─── PRINT screenshot ───
      const printPage = await browser.newPage();
      await printPage.setViewport({ width: 816, height: 1056 });
      await printPage.setContent(html, { waitUntil: 'networkidle0' });
      await printPage.emulateMediaType('print');
      await printPage.evaluateHandle('document.fonts.ready');
      const pdfOut = path.join(__dirname, `pdf-split-${hstyle}.png`);
      const printEl = await printPage.$('#resume-preview');
      await printEl.screenshot({ path: pdfOut });
      fs.copyFileSync(pdfOut, path.join(ARTIFACTS, `pdf-split-${hstyle}.png`));
      console.log(`  Print screenshot:  ${pdfOut}`);
      await printPage.close();
    }
  } finally {
    await browser.close();
    console.log('\nDone.');
  }
})();
