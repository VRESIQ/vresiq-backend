// test-multipage-watermark.js
// Comprehensive test for watermark rendering on multi-page resumes
// Usage: node test-multipage-watermark.js
// Generates PDFs with 1, 2, 3, and 5 pages to verify watermark appears on ALL pages

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const generateTestHtml = (pageCount) => {
  let pages = '';
  for (let i = 1; i <= pageCount; i++) {
    pages += `
    <div class="page" ${i > 1 ? 'style="page-break-before: always;"' : ''}>
      <h1>Page ${i}</h1>
      <p>This is page ${i} of a ${pageCount}-page resume.</p>
      <p>The watermark "Made with VRESIQ" should appear at the bottom of <strong>EVERY PAGE</strong> including this one.</p>
      <p style="margin-top: 600px; border-top: 1px solid #ccc; padding-top: 12px;">
        End of page ${i}. Page break follows for page ${i + 1}.
      </p>
    </div>
    `;
  }

  return `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Multi-Page Watermark Test - ${pageCount} pages</title>
<style>
@page {
  size: letter;
  margin-top: 40px;
  margin-bottom: 36px;
  margin-left: 0;
  margin-right: 0;
}
html, body { background: #fff; font-family: 'Inter', Arial, sans-serif; margin: 0; padding: 0; }
.page { min-height: 1000px; padding: 40px; border-bottom: 1px solid #eee; }
h1 { color: #111; font-size: 28px; margin-bottom: 12px; }
p { color: #333; font-size: 14px; line-height: 1.6; margin-bottom: 12px; }
</style>
</head>
<body>
  ${pages}
</body>
</html>`;
};

const footerHtml = `
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
</div>`;

(async () => {
  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log('Multi-Page Watermark Rendering Test');
  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log('');

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const pageCounts = [1, 2, 3, 5];

  for (const pages of pageCounts) {
    console.log(`Generating ${pages}-page PDF with watermark...`);
    
    const page = await browser.newPage();
    await page.emulateMediaType('print');
    const html = generateTestHtml(pages);
    await page.setContent(html, { waitUntil: 'networkidle0' });

    const outputPath = path.join(__dirname, `test-free-${pages}page.pdf`);
    await page.pdf({
      path: outputPath,
      format: 'letter',
      printBackground: true,
      preferCSSPageSize: false,
      displayHeaderFooter: true,
      headerTemplate: '<span></span>',
      footerTemplate: footerHtml,
      margin: { top: '40px', bottom: '36px', left: '0px', right: '0px' }
    });

    await page.close();
    
    const fileSize = fs.statSync(outputPath).size;
    console.log(`  ✓ Generated: ${path.basename(outputPath)} (${fileSize} bytes)`);
    console.log(`  📄 Watermark should appear on pages: 1${pages > 1 ? ` through ${pages}` : ''}`);
    console.log('');
  }

  console.log('Pro User Test (No Watermark):');
  console.log('');
  
  const page = await browser.newPage();
  await page.emulateMediaType('print');
  const html = generateTestHtml(2);
  await page.setContent(html, { waitUntil: 'networkidle0' });

  const outputPath = path.join(__dirname, `test-pro-2page.pdf`);
  await page.pdf({
    path: outputPath,
    format: 'letter',
    printBackground: true,
    preferCSSPageSize: false,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate: '<span></span>',  // No watermark for pro users
    margin: { top: '40px', bottom: '36px', left: '0px', right: '0px' }
  });

  await page.close();
  
  const fileSize = fs.statSync(outputPath).size;
  console.log(`Generating 2-page PDF WITHOUT watermark (Pro user)...`);
  console.log(`  ✓ Generated: ${path.basename(outputPath)} (${fileSize} bytes)`);
  console.log(`  ✗ No watermark on any page (as expected for pro users)`);
  console.log('');

  await browser.close();

  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log('Test Summary');
  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log('Generated test PDFs:');
  console.log('  ✓ test-free-1page.pdf  - 1 page with watermark');
  console.log('  ✓ test-free-2page.pdf  - 2 pages with watermark on both');
  console.log('  ✓ test-free-3page.pdf  - 3 pages with watermark on all');
  console.log('  ✓ test-free-5page.pdf  - 5 pages with watermark on all');
  console.log('  ✓ test-pro-2page.pdf   - 2 pages WITHOUT watermark');
  console.log('');
  console.log('Verification:');
  console.log('  1. Open each PDF and verify:');
  console.log('     - Free PDFs: "Made with VRESIQ" appears at bottom center of EVERY page');
  console.log('     - Pro PDF: NO watermark text anywhere');
  console.log('  2. Confirm watermark is:');
  console.log('     - Visible but subtle (small, light gray, low opacity)');
  console.log('     - Centered at bottom of page');
  console.log('     - Does NOT interfere with resume content');
  console.log('     - Does NOT break text extraction/ATS compatibility');
  console.log('');
  console.log('════════════════════════════════════════════════════════════════════════════════');
})();
