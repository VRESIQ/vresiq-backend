const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

(async () => {
  const browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 816, height: 1056 });

  const cssPath = path.resolve(__dirname, '../../vresiq-frontend/src/components/ResumePreview.css');
  const css = fs.readFileSync(cssPath, 'utf8');

  // Let's create an exact HTML with both screen mode and print mode testing
  const html = `
  <!DOCTYPE html>
  <html>
  <head>
    <meta charset="utf-8">
    <title>Visual Check</title>
    <style>
      ${css}
    </style>
  </head>
  <body style="background: #222; padding: 20px;">
    <article id="resume-preview" class="resume-preview rp-ats_classic" data-template="ats_classic" data-lstyle="standard">
      <div class="rp-ats-container rp-ats-classic">
        <header class="rp-ats-header">
          <h1 class="rp-ats-name">Jane Doe</h1>
          <p class="rp-ats-role">Healthcare Data Analyst</p>
          <div class="rp-ats-contact">
            <span class="rp-ats-contact-item"><a href="mailto:jane@example.com">jane@example.com</a></span>
            <span class="rp-ats-bullet"> | </span>
            <span class="rp-ats-contact-item"><a href="tel:+15550199">+1 (555) 0199</a></span>
            <span class="rp-ats-bullet"> | </span>
            <span class="rp-ats-contact-item">Boston, MA</span>
          </div>
        </header>
        <main class="rp-ats-body">
          <section class="rp-section">
            <h3 class="rp-stitle" data-divider="line">Summary</h3>
            <p>Healthcare Data Analyst with 5+ years of experience optimizing EHR workflows and clinical reporting.</p>
          </section>
          <section class="rp-section">
            <h3 class="rp-stitle" data-divider="line">Experience</h3>
            <div class="rp-item">
              <div class="rp-item-head">
                <strong>Senior Clinical Data Analyst</strong>
                <span>Jan 2021 - Present</span>
              </div>
              <div class="rp-item-sub">
                <span>Mass General Brigham</span>
                <span>Boston, MA</span>
              </div>
              <div class="rp-item-desc">
                <ul class="rp-desc-list">
                  <li>Engineered automated SQL pipelines for clinical quality measure reporting across 14 hospital units.</li>
                  <li>Reduced EHR reporting discrepancies by 34% using predictive validation models.</li>
                </ul>
              </div>
            </div>
          </section>
          <section class="rp-section">
            <h3 class="rp-stitle" data-divider="line">Education</h3>
            <div class="rp-item">
              <div class="rp-item-head">
                <strong>B.S. in Health Informatics</strong>
                <span>2016 - 2020</span>
              </div>
              <div class="rp-item-sub">
                <span>Boston University</span>
                <span>Boston, MA</span>
              </div>
            </div>
          </section>
          <section class="rp-section">
            <h3 class="rp-stitle" data-divider="line">Skills</h3>
            <div class="rp-item-desc">
              <ul class="rp-desc-list">
                <li><strong>Technical:</strong> SQL, Python, R, Tableau, Epic Clarity, Cerner, HIPAA Compliance</li>
              </ul>
            </div>
          </section>
          <section class="rp-section">
            <h3 class="rp-stitle" data-divider="line">Certifications</h3>
            <div class="rp-certs-list">
              <div class="rp-compact" style="display: flex; justify-content: space-between; align-items: baseline;">
                <a href="https://ahima.org" class="resume-link rp-cert-link rp-compact-url">Certified Health Data Analyst (CHDA)</a>
                <span class="rp-compact-title">AHIMA, 2023</span>
              </div>
            </div>
          </section>
        </main>
      </div>
    </article>
  </body>
  </html>
  `;

  await page.setContent(html, { waitUntil: 'networkidle0' });

  // 1. Screenshot in SCREEN mode (shows what user sees on screen)
  const previewEl = await page.$('#resume-preview');
  await previewEl.screenshot({ path: path.resolve(__dirname, 'screen_with_line.png') });
  console.log('Saved screen_with_line.png');

  // Let's inspect all pseudo-elements and lines
  const lineDetails = await page.evaluate(() => {
    const el = document.getElementById('resume-preview');
    const after = window.getComputedStyle(el, '::after');
    const certs = document.querySelector('.rp-certs-list');
    const certsRect = certs.getBoundingClientRect();
    const elRect = el.getBoundingClientRect();
    return {
      elHeight: el.offsetHeight,
      certsBottomRelative: certsRect.bottom - elRect.top,
      afterBg: after.backgroundImage
    };
  });
  console.log('Line details on screen:', lineDetails);

  await browser.close();
})();
