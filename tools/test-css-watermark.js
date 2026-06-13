// test-css-watermark.js
// Test CSS-embedded watermark (not footer template)

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const html = `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>CSS-Embedded Watermark Test</title>
<style>
@page { size: letter; margin: 40px 0 40px 0; }
html, body { background: #fff; font-family: Arial; margin: 0; padding: 0; }
.page { min-height: 950px; padding: 40px; border-bottom: 1px solid #ccc; }
h1 { color: #333; }
p { color: #666; }

/* Watermark footer - embedded in HTML and visible in print */
.watermark-footer {
  position: sticky;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  font-family: Arial, sans-serif;
  font-size: 8px;
  color: #999999;
  opacity: 0.35;
  padding: 4px 0;
  background: transparent;
  width: 100%;
}

@media print {
  .watermark-footer {
    position: fixed !important;
    bottom: 8px !important;
    left: 0 !important;
    right: 0 !important;
    z-index: 9999 !important;
    width: 100% !important;
    display: block !important;
    opacity: 0.35 !important;
  }
}
</style>
</head>
<body>
  <div class="page">
    <h1>Page 1</h1>
    <p>Content for page 1</p>
    <div class="watermark-footer">Made with VRESIQ</div>
  </div>
  <div class="page" style="page-break-before: always;">
    <h1>Page 2</h1>
    <p>Content for page 2</p>
    <div class="watermark-footer">Made with VRESIQ</div>
  </div>
  <div class="page" style="page-break-before: always;">
    <h1>Page 3</h1>
    <p>Content for page 3</p>
    <div class="watermark-footer">Made with VRESIQ</div>
  </div>
</body>
</html>`;

(async () => {
  console.log('Testing CSS-Embedded Watermark Approach...');
  console.log('');

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox']
  });

  const page = await browser.newPage();
  await page.emulateMediaType('print');
  await page.setContent(html, { waitUntil: 'networkidle0' });

  const outputPath = path.join(__dirname, 'test-css-watermark.pdf');

  await page.pdf({
    path: outputPath,
    format: 'letter',
    printBackground: true,
    preferCSSPageSize: false,
    displayHeaderFooter: false,  // Not using footer template
    margin: { top: '40px', bottom: '40px', left: '0px', right: '0px' }
  });

  await page.close();
  await browser.close();

  const fileSize = fs.statSync(outputPath).size;
  console.log(`✓ Generated: ${path.basename(outputPath)} (${fileSize} bytes)`);
  console.log('');
  console.log('Verifying watermark text...');

  const pdfContent = fs.readFileSync(outputPath, 'utf8');
  let count = 0;
  for (let i = 0; i < pdfContent.length; i++) {
    if (pdfContent.substring(i, i + 15) === 'Made with VRESIQ') {
      count++;
    }
  }

  if (count > 0) {
    console.log(`✓ Watermark text found ${count} time(s) in PDF`);
    console.log('✓ SUCCESS: Watermark approach works!');
  } else {
    console.log('✗ Watermark text NOT found in PDF');
  }
})();
