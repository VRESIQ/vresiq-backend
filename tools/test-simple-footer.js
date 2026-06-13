// test-simple-footer.js
// Test with simplified footer template HTML

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const html = `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Simple Footer Test</title>
<style>
@page { size: letter; margin: 40px 0 45px 0; }
html, body { background: #fff; font-family: Arial; margin: 0; padding: 0; }
.page { min-height: 950px; padding: 40px; }
h1 { color: #333; }
p { color: #666; }
</style>
</head>
<body>
  <div class="page">
    <h1>Page 1</h1>
    <p>Footer should appear below.</p>
  </div>
  <div class="page" style="page-break-before: always;">
    <h1>Page 2</h1>
    <p>Footer should appear here too.</p>
  </div>
</body>
</html>`;

(async () => {
  console.log('Testing Simple Footer Template...');
  console.log('');

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox']
  });

  const page = await browser.newPage();
  await page.emulateMediaType('print');
  await page.setContent(html, { waitUntil: 'networkidle0' });

  const outputPath = path.join(__dirname, 'test-simple-footer.pdf');

  await page.pdf({
    path: outputPath,
    format: 'letter',
    printBackground: true,
    preferCSSPageSize: false,
    displayHeaderFooter: true,
    headerTemplate: '<div></div>',
    footerTemplate: `
      <div style="
        width: 100%;
        text-align: center;
        font-size: 8px;
        color: #999999;
        padding-bottom: 8px;
      ">Made with VRESIQ</div>
    `,
    margin: { top: '40px', bottom: '45px', left: '0px', right: '0px' }
  });

  await page.close();
  await browser.close();

  const fileSize = fs.statSync(outputPath).size;
  console.log(`✓ Generated: ${path.basename(outputPath)} (${fileSize} bytes)`);
  console.log('');
  console.log('Checking for watermark text...');

  const pdfContent = fs.readFileSync(outputPath, 'utf8');
  if (pdfContent.includes('Made with VRESIQ')) {
    console.log('✓ Watermark text FOUND in PDF!');
  } else if (pdfContent.includes('VRESIQ')) {
    console.log('~ Partial match found');
  } else {
    console.log('✗ Watermark text NOT found in PDF');
  }
})();
