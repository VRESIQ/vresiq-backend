// debug-footer-template.js
// Debug why footerTemplate isn't rendering
// Tests multiple footer template approaches

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const html = `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Footer Template Debug Test</title>
<style>
@page { size: letter; margin: 40px 0 36px 0; }
html, body { background: #fff; font-family: Arial; margin: 0; padding: 0; }
.page { min-height: 950px; padding: 40px; background: #f5f5f5; border-bottom: 1px solid #999; }
h1 { color: #333; }
p { color: #666; }
</style>
</head>
<body>
  <div class="page">
    <h1>Page 1: Footer Template Debug</h1>
    <p>This page tests whether the Puppeteer footer template is rendering correctly.</p>
    <p>Check the bottom of this page and the next for the watermark text.</p>
  </div>
  <div class="page" style="page-break-before: always;">
    <h1>Page 2</h1>
    <p>Footer should appear below this text.</p>
  </div>
</body>
</html>`;

const tests = [
  {
    name: 'Approach A: Inline styles with !important',
    footer: `
      <div style="
        font-family: Arial, sans-serif;
        font-size: 12px !important;
        color: #000000 !important;
        text-align: center !important;
        width: 100% !important;
        padding: 8px 0 !important;
      ">
        Test Footer: Approach A
      </div>`
  },
  {
    name: 'Approach B: Explicit display and visibility',
    footer: `
      <div style="
        display: block !important;
        visibility: visible !important;
        font-size: 12px;
        color: #000000;
        text-align: center;
        width: 100%;
        padding: 8px 0;
      ">
        Test Footer: Approach B
      </div>`
  },
  {
    name: 'Approach C: Minimal styling',
    footer: `<div>Test Footer: Approach C</div>`
  },
  {
    name: 'Approach D: With background for visibility',
    footer: `
      <div style="
        background: #ffffcc;
        font-size: 12px;
        color: #000000;
        text-align: center;
        width: 100%;
        padding: 8px 0;
      ">
        Test Footer: Approach D
      </div>`
  },
  {
    name: 'Approach E: Production watermark styling',
    footer: `
      <div style="
        font-family: 'Inter', 'Manrope', 'Plus Jakarta Sans', 'Helvetica Neue', Arial, sans-serif;
        font-size: 8px;
        font-weight: 400;
        color: #999999;
        width: 100%;
        box-sizing: border-box;
        text-align: center;
        letter-spacing: 0px;
        line-height: 1;
        padding: 0 0 8px 0;
        margin: 0;
        display: block;
        opacity: 0.35;
        -webkit-print-color-adjust: exact;
        print-color-adjust: exact;
      ">
        Made with VRESIQ
      </div>`
  }
];

(async () => {
  console.log('════════════════════════════════════════════════════════════════════');
  console.log('Footer Template Debug Test - Puppeteer 22.6.0');
  console.log('════════════════════════════════════════════════════════════════════');
  console.log('');
  console.log('Testing different footer template approaches to debug rendering...');
  console.log('');

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  for (const test of tests) {
    console.log(`Testing: ${test.name}`);

    const page = await browser.newPage();
    await page.emulateMediaType('print');
    await page.setContent(html, { waitUntil: 'networkidle0' });

    const filename = `debug-footer-${tests.indexOf(test) + 1}.pdf`;
    const outputPath = path.join(__dirname, filename);

    try {
      await page.pdf({
        path: outputPath,
        format: 'letter',
        printBackground: true,
        preferCSSPageSize: false,
        displayHeaderFooter: true,
        headerTemplate: '<span></span>',
        footerTemplate: test.footer,
        margin: { top: '40px', bottom: '36px', left: '0px', right: '0px' }
      });

      const fileSize = fs.statSync(outputPath).size;
      console.log(`  ✓ Generated: ${filename} (${fileSize} bytes)`);

      // Verify watermark presence
      const pdfContent = fs.readFileSync(outputPath, 'utf8');
      const hasPart1 = pdfContent.includes('Approach') || pdfContent.includes('Footer');
      const hasPart2 = pdfContent.includes('Test') || pdfContent.includes('VRESIQ');
      console.log(`  Text extraction: ${hasPart1 && hasPart2 ? '✓ Found' : '✗ Not found'}`);
    } catch (err) {
      console.log(`  ✗ Error: ${err.message}`);
    }

    await page.close();
    console.log('');
  }

  await browser.close();

  console.log('════════════════════════════════════════════════════════════════════');
  console.log('Results Summary');
  console.log('════════════════════════════════════════════════════════════════════');
  console.log('Open the generated PDFs and check:');
  console.log('  1. Is the footer text visible at the bottom?');
  console.log('  2. Which approaches work and which don\'t?');
  console.log('  3. Can the footer be extracted with PDF text extraction tools?');
  console.log('');
  console.log('Common issues:');
  console.log('  - Footer might be clipped if margin-bottom is too small');
  console.log('  - Footer might be invisible if opacity: 0');
  console.log('  - Footer might not render if displayHeaderFooter is false');
  console.log('  - Footer might not render if preferCSSPageSize is true');
  console.log('════════════════════════════════════════════════════════════════════');
})();
