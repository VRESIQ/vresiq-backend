// test-watermark.js
// Runs locally to prove the preferCSSPageSize root cause
// Usage: node test-watermark.js

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const html = `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
@page {
  size: letter;
  margin-top: 40px;
  margin-bottom: 48px;
  margin-left: 0;
  margin-right: 0;
}
body { background: #fff; font-family: Arial; }
.page { min-height: 900px; padding: 40px; border-bottom: 1px solid #eee; }
h1 { color: #111; }
p { color: #444; font-size: 14px; }
</style>
</head>
<body>
  <div class="page">
    <h1>Page 1 Content</h1>
    <p>This is resume content on page 1. The watermark should appear at the bottom of every page.</p>
  </div>
  <div class="page" style="page-break-before: always;">
    <h1>Page 2 Content</h1>
    <p>This is page 2. The watermark "Made with VRESIQ" should appear here too.</p>
  </div>
  <div class="page" style="page-break-before: always;">
    <h1>Page 3 Content</h1>
    <p>This is page 3. Watermark required on all pages.</p>
  </div>
</body>
</html>`;

const footerHtml = `
<div style="
  font-family: 'Inter', Arial, sans-serif;
  font-size: 9px;
  font-weight: 500;
  color: #6B7280;
  width: 100%;
  box-sizing: border-box;
  text-align: center;
  letter-spacing: 0.4px;
  line-height: 1;
  padding: 0 0 10px 0;
  margin: 0;
  display: block;
  -webkit-print-color-adjust: exact;
  print-color-adjust: exact;
">
  Made with VRESIQ
</div>`;

(async () => {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const page = await browser.newPage();
  await page.emulateMediaType('print');
  await page.setContent(html, { waitUntil: 'networkidle0' });

  // TEST A: preferCSSPageSize: true (current broken config)
  await page.pdf({
    path: path.join(__dirname, 'test-with-preferCSS.pdf'),
    format: 'letter',
    printBackground: true,
    preferCSSPageSize: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: footerHtml,
    margin: { top: '40px', bottom: '48px', left: '0px', right: '0px' }
  });
  console.log('✅ Generated: test-with-preferCSS.pdf');

  // TEST B: preferCSSPageSize: false (fixed config — Puppeteer margin controls)
  await page.pdf({
    path: path.join(__dirname, 'test-without-preferCSS.pdf'),
    format: 'letter',
    printBackground: true,
    preferCSSPageSize: false,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: footerHtml,
    margin: { top: '40px', bottom: '36px', left: '0px', right: '0px' }
  });
  console.log('✅ Generated: test-without-preferCSS.pdf');

  await browser.close();
  console.log('\nOpen both PDFs and compare:');
  console.log('  test-with-preferCSS.pdf    = WATERMARK MISSING (broken)');
  console.log('  test-without-preferCSS.pdf = WATERMARK VISIBLE on all pages (fixed)');
})();
