// test-plain-text.js
// Super simple test - just plain text with no special positioning

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const html = `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Plain Text Test</title>
<style>
html, body { margin: 0; padding: 20px; font-family: Arial; background: white; }
h1 { color: #000; }
p { color: #000; margin-bottom: 40px; }
.footer { font-size: 10px; color: #000; border-top: 1px solid black; padding-top: 10px; }
</style>
</head>
<body>
  <h1>Test Page 1</h1>
  <p>This is the main content.</p>
  <p style="margin-top: 500px;">More content below.</p>
  <div class="footer">
    Made with VRESIQ - Plain Text Footer
  </div>
  <div style="page-break-before: always; margin-top: 100px;">
    <h1>Test Page 2</h1>
    <p>Second page content</p>
    <div class="footer">
      Made with VRESIQ - Plain Text Footer
    </div>
  </div>
</body>
</html>`;

(async () => {
  console.log('Testing Plain Text Rendering in PDF...');
  console.log('');

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox']
  });

  const page = await browser.newPage();
  await page.emulateMediaType('print');
  await page.setContent(html, { waitUntil: 'networkidle0' });

  const outputPath = path.join(__dirname, 'test-plain-text.pdf');

  await page.pdf({
    path: outputPath,
    format: 'letter',
    printBackground: true,
    margin: { top: '20px', bottom: '20px', left: '0px', right: '0px' }
  });

  await page.close();
  await browser.close();

  const fileSize = fs.statSync(outputPath).size;
  console.log(`✓ Generated: ${path.basename(outputPath)} (${fileSize} bytes)`);
  console.log('');

  const pdfContent = fs.readFileSync(outputPath, 'utf8');

  console.log('Checking for text content...');
  if (pdfContent.includes('Test Page 1')) {
    console.log('✓ Found "Test Page 1"');
  } else {
    console.log('✗ "Test Page 1" not found');
  }

  if (pdfContent.includes('main content')) {
    console.log('✓ Found "main content"');
  } else {
    console.log('✗ "main content" not found');
  }

  if (pdfContent.includes('Made with VRESIQ')) {
    console.log('✓ Found "Made with VRESIQ"');
  } else if (pdfContent.includes('VRESIQ')) {
    console.log('~ Found "VRESIQ" (partial)');
  } else {
    console.log('✗ "Made with VRESIQ" not found');
  }

  console.log('');
  console.log('If text is not being found in the PDF, the issue is with PDF content encoding/extraction.');
})();
