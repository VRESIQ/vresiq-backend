/**
 * check-after-element.js  
 * Checks if the ::after pseudo-element (page-break guide line) is visible in print mode
 */
const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

(async () => {
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
    <style>
      ${css}
    </style>
    <style>
      html, body {
        background: #ffffff !important;
        margin: 0 !important;
        padding: 0 !important;
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
    </style>
  </head>
  <body>
    <article id="resume-preview" class="resume-preview rp-ats_classic" data-template="ats_classic" data-lstyle="standard">
      <div class="rp-ats-container rp-ats-classic">
        <header class="rp-ats-header">
          <h1 class="rp-ats-name">Jane Doe</h1>
          <p class="rp-ats-role">Healthcare Data Analyst</p>
        </header>
        <main class="rp-ats-body">
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
  
  // First check SCREEN mode (before print emulation)
  const screenResult = await page.evaluate(() => {
    const el = document.getElementById('resume-preview');
    const afterStyles = window.getComputedStyle(el, '::after');
    return {
      afterDisplay: afterStyles.display,
      afterBg: afterStyles.backgroundImage ? afterStyles.backgroundImage.slice(0, 80) + '...' : 'none',
      afterContent: afterStyles.content,
      afterHeight: afterStyles.height,
      afterWidth: afterStyles.width,
      elHeight: el.offsetHeight
    };
  });
  console.log('SCREEN MODE ::after:', screenResult);
  
  // Screenshot in screen mode
  await page.screenshot({ path: path.resolve(__dirname, 'check-screen.png'), fullPage: true });
  console.log('Saved check-screen.png');

  // Switch to print mode
  await page.emulateMediaType('print');
  
  // Check PRINT mode
  const printResult = await page.evaluate(() => {
    const el = document.getElementById('resume-preview');
    const afterStyles = window.getComputedStyle(el, '::after');
    return {
      afterDisplay: afterStyles.display,
      afterBg: afterStyles.backgroundImage ? afterStyles.backgroundImage.slice(0, 80) + '...' : 'none',
      afterContent: afterStyles.content,
      afterHeight: afterStyles.height,
      afterWidth: afterStyles.width,
      elHeight: el.offsetHeight
    };
  });
  console.log('PRINT MODE ::after:', printResult);
  
  // Screenshot in print mode
  await page.screenshot({ path: path.resolve(__dirname, 'check-print.png'), fullPage: true });
  console.log('Saved check-print.png');

  // Generate PDF and screenshot it
  await page.pdf({
    path: path.resolve(__dirname, 'check-after.pdf'),
    format: 'letter',
    printBackground: true,
    preferCSSPageSize: false,
    displayHeaderFooter: false,
    margin: { top: '0px', bottom: '0px', left: '0px', right: '0px' }
  });
  console.log('Saved check-after.pdf');

  await browser.close();
})();
