const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');
const { PDFDocument } = require('pdf-lib');

async function testScenario(name, customPreviewStyle, customPrintPageStyle) {
  const browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
  const page = await browser.newPage();
  await page.setViewport({ width: 816, height: 1056 });

  const cssPath = path.resolve(__dirname, '../../vresiq-frontend/src/components/ResumePreview.css');
  const css = fs.readFileSync(cssPath, 'utf8');

  const html = `
  <!DOCTYPE html>
  <html>
  <head>
    <meta charset="utf-8">
    <title>${name}</title>
    <style>
      ${css}
    </style>
    <style>
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
        ${customPrintPageStyle || ''}
      }

      body * {
        visibility: visible !important;
      }

      #resume-preview, .resume-preview {
        position: static !important;
        display: flow-root !important;
        width: 816px !important;
        max-width: 816px !important;
        height: auto !important;
        margin: 0 auto !important;
        padding: 0 !important;
        box-shadow: none !important;
        border: none !important;
        overflow: visible !important;
        visibility: visible !important;
        box-sizing: border-box !important;
        background-color: #ffffff !important;
        ${customPreviewStyle || ''}
      }
    </style>
  </head>
  <body>
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
  await page.emulateMediaType('print');

  const outPdf = path.resolve(__dirname, `${name}.pdf`);
  await page.pdf({
    path: outPdf,
    format: 'letter',
    printBackground: true,
    preferCSSPageSize: false,
    displayHeaderFooter: true,
    footerTemplate: '<div style="font-family: Arial; font-size: 8px; width: 100%; text-align: center; opacity: 0.35;">Made with VRESIQ</div>',
    margin: {
      top: '0px',
      bottom: '45px',
      left: '0px',
      right: '0px'
    }
  });

  const pdfBytes = fs.readFileSync(outPdf);
  const doc = await PDFDocument.load(pdfBytes);
  console.log(`[${name}] Page count:`, doc.getPageCount());

  await browser.close();
}

(async () => {
  console.log('--- Testing variations ---');
  // Scenario 1: min-height 1056px (original)
  await testScenario('scenario-1-original-minheight-1056', 'min-height: 1056px !important;', '');
  // Scenario 2: min-height: 0
  await testScenario('scenario-2-minheight-0', 'min-height: 0 !important;', '');
  // Scenario 3: min-height: auto
  await testScenario('scenario-3-minheight-auto', 'min-height: auto !important;', '');
  // Scenario 4: min-height: none
  await testScenario('scenario-4-minheight-none', 'min-height: none !important;', '');
})();
