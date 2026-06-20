/**
 * verify-split-print.js — full visual check for 3 UI fixes:
 * 1. Target role badge visible in all sidebar themes
 * 2. Tech profile platform name inherits theme colour (not hardcoded blue)
 * 3. Card header badge "Seeking:" not clipped
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

function buildHTML(hstyle, css) {
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>${css}</style>
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
        <!-- Target role badge in dark sidebar -->
        <div class="rp-target-role-badge">
          <span class="rp-role-seeking-label">Seeking: </span>Software Engineer
        </div>
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
      </div>
      <!-- Technical Profiles in dark sidebar — platform name must be white, not blue -->
      <div class="rp-section">
        <div class="rp-stitle">Technical Profiles</div>
        <div class="rp-compact-item rp-tech-profile-item">
          <div class="rp-compact-head rp-tech-profile-head">
            <span class="rp-compact-title rp-tech-profile-title">
              <!-- with URL (anchor) -->
              <a href="https://leetcode.com/janesmith" class="resume-link rp-compact-url rp-tech-profile-url">
                Leetcode <span class="external-link-icon">↗</span>
              </a>
            </span>
          </div>
          <div class="rp-item-desc rp-tech-profile-desc">Top 1.5% globally</div>
        </div>
        <div class="rp-compact-item rp-tech-profile-item">
          <div class="rp-compact-head rp-tech-profile-head">
            <span class="rp-compact-title rp-tech-profile-title">
              <!-- without URL (strong) -->
              <strong class="rp-tech-profile-name">GitHub</strong>
            </span>
          </div>
          <div class="rp-item-desc rp-tech-profile-desc">50+ repos, 200 stars</div>
        </div>
      </div>
      <div class="rp-section">
        <div class="rp-stitle">Education</div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>BS Computer Science</strong></div>
          <div class="rp-item-sub">MIT &mdash; 2018</div>
        </div>
      </div>
    </aside>
    <main class="rp-main-col">
      <div class="rp-section">
        <div class="rp-stitle">Summary</div>
        <div class="rp-summary">Experienced software engineer with 7+ years building scalable distributed systems.</div>
      </div>
      <div class="rp-section">
        <div class="rp-stitle">Experience</div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Senior Software Engineer</strong><span class="rp-item-date">Jan 2022 – Present</span></div>
          <div class="rp-item-sub">Stripe · San Francisco, CA</div>
          <div class="rp-item-desc"><ul>
            <li>Led migration of monolithic payment processor to microservices, reducing latency by 40%.</li>
            <li>Designed and implemented real-time fraud detection pipeline processing 50K events/sec.</li>
          </ul></div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head"><strong>Software Engineer</strong><span class="rp-item-date">Jun 2019 – Dec 2021</span></div>
          <div class="rp-item-sub">Airbnb · San Francisco, CA</div>
          <div class="rp-item-desc"><ul>
            <li>Developed core booking flow APIs serving 2M+ daily active users.</li>
            <li>Re-architected search ranking system improving relevance by 25%.</li>
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
      const html = buildHTML(hstyle, css);

      // Screen screenshot
      const sp = await browser.newPage();
      await sp.setViewport({ width: 816, height: 1056 });
      await sp.setContent(html, { waitUntil: 'networkidle0' });
      const prevOut = path.join(__dirname, `preview-split-${hstyle}.png`);
      const prevEl = await sp.$('#resume-preview');
      await prevEl.screenshot({ path: prevOut });
      fs.copyFileSync(prevOut, path.join(ARTIFACTS, `preview-split-${hstyle}.png`));
      console.log(`  Preview: ${prevOut}`);
      await sp.close();

      // Print screenshot
      const pp = await browser.newPage();
      await pp.setViewport({ width: 816, height: 1056 });
      await pp.setContent(html, { waitUntil: 'networkidle0' });
      await pp.emulateMediaType('print');
      await pp.evaluateHandle('document.fonts.ready');
      const pdfOut = path.join(__dirname, `pdf-split-${hstyle}.png`);
      const pdfEl = await pp.$('#resume-preview');
      await pdfEl.screenshot({ path: pdfOut });
      fs.copyFileSync(pdfOut, path.join(ARTIFACTS, `pdf-split-${hstyle}.png`));
      console.log(`  Print:   ${pdfOut}`);
      await pp.close();
    }
  } finally {
    await browser.close();
    console.log('\nDone.');
  }
})();
