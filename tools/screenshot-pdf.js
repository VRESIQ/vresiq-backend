/**
 * screenshot-pdf.js
 * Takes a screenshot of the rendered PDF page to visually inspect the certifications line bug
 */
const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');
const { PDFDocument } = require('pdf-lib');

async function renderAndScreenshot(name, minHeightStyle) {
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
        ${minHeightStyle}
        margin: 0 auto !important;
        padding: 0 !important;
        box-shadow: none !important;
        border: none !important;
        overflow: visible !important;
        visibility: visible !important;
        box-sizing: border-box !important;
        background-color: #ffffff !important;
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

  // Screenshot of full page in print mode
  const ssPath = path.resolve(__dirname, `${name}-screenshot.png`);
  await page.screenshot({ path: ssPath, fullPage: true });
  console.log(`Saved ${name}-screenshot.png`);

  // Generate PDF
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
  console.log(`[${name}] Page count: ${doc.getPageCount()}`);

  // Inspect what's happening at the bottom of the resume
  const details = await page.evaluate(() => {
    const el = document.getElementById('resume-preview');
    const certsSection = [...document.querySelectorAll('.rp-section')].find(s => 
      s.querySelector('.rp-stitle') && s.querySelector('.rp-stitle').textContent.includes('Certifications')
    );
    const certsRect = certsSection ? certsSection.getBoundingClientRect() : null;
    const elRect = el.getBoundingClientRect();
    
    // Check border on bottom of certifications section
    const certsStyle = certsSection ? window.getComputedStyle(certsSection) : null;
    
    return {
      elHeight: el.offsetHeight,
      elOffsetTop: el.offsetTop,
      certsBottom: certsRect ? certsRect.bottom - elRect.top : null,
      certsBorderBottom: certsStyle ? certsStyle.borderBottom : null,
      elMinHeight: window.getComputedStyle(el).minHeight,
      elHeight2: window.getComputedStyle(el).height,
    };
  });
  console.log(`[${name}] Details:`, details);

  await browser.close();
}

(async () => {
  // Test 1: Current behavior (min-height: 1056px)
  await renderAndScreenshot('pdf-test-old', 'min-height: 1056px !important;');
  // Test 2: Fixed behavior (min-height: 0)
  await renderAndScreenshot('pdf-test-fixed', 'min-height: 0 !important;');
})();
