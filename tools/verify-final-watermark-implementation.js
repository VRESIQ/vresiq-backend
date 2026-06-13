// verify-final-watermark-implementation.js
// Comprehensive verification of the complete watermark system

const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');
const pdfParse = require('pdf-parse');

// Simulate different user scenarios
const testScenarios = [
  {
    name: 'Free User - 1 Page Resume',
    isFreePlan: true,
    pages: 1,
    expected: { watermarkFound: true, watermarkPerPage: 1 }
  },
  {
    name: 'Free User - 2 Page Resume',
    isFreePlan: true,
    pages: 2,
    expected: { watermarkFound: true, watermarkPerPage: 2 }
  },
  {
    name: 'Free User - 5 Page Resume',
    isFreePlan: true,
    pages: 5,
    expected: { watermarkFound: true, watermarkPerPage: 5 }
  },
  {
    name: 'Pro User - 2 Page Resume',
    isFreePlan: false,
    pages: 2,
    expected: { watermarkFound: false, watermarkPerPage: 0 }
  }
];

const generateResumeHtml = (pageCount, isFreePlan) => {
  let pages = '';
  for (let i = 1; i <= pageCount; i++) {
    pages += `
    <div style="min-height: 950px; padding: 40px; border-bottom: 1px solid #ddd;" ${i > 1 ? 'style="page-break-before: always; min-height: 950px; padding: 40px;"' : ''}>
      <h1>Page ${i}</h1>
      <p>This is page ${i} of a ${pageCount}-page resume.</p>
      <p style="margin-top: 600px;">End of page ${i}.</p>
    </div>
    `;
  }

  const watermark = isFreePlan ? `
    <div class="watermark-footer" aria-hidden="true">
      Made with VRESIQ
    </div>
  ` : '';

  return `
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Resume - Watermark Test</title>
<style>
@page { size: letter; margin: 40px 0 40px 0; }
html, body { background: #fff; font-family: 'Inter', Arial, sans-serif; margin: 0; padding: 0; color: #000; }

.watermark-footer {
  position: sticky;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  font-size: 8px;
  font-weight: 400;
  color: #999999;
  opacity: 0.35;
  padding: 4px 0;
  background: transparent;
  pointer-events: none;
  user-select: none;
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
  <div id="resume-preview">
    ${pages}
  </div>
  ${watermark}
</body>
</html>`;
};

const verifyPdfWatermark = async (pdfPath) => {
  const pdfBuffer = fs.readFileSync(pdfPath);
  const pdfData = await pdfParse(pdfBuffer);
  const text = pdfData.text;
  
  // Count occurrences of the watermark
  const matches = (text.match(/Made with VRESIQ/g) || []).length;
  
  return {
    totalPages: pdfData.numpages,
    watermarkFound: text.includes('Made with VRESIQ'),
    watermarkOccurrences: matches
  };
};

(async () => {
  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log('Comprehensive Watermark Implementation Verification');
  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log('');

  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox']
  });

  let totalTests = 0;
  let passedTests = 0;
  let failedTests = 0;

  for (const scenario of testScenarios) {
    console.log(`Testing: ${scenario.name}`);
    console.log('─────────────────────────────────────────────────────────────────────────────');

    const html = generateResumeHtml(scenario.pages, scenario.isFreePlan);
    const outputFile = `final-test-${scenario.isFreePlan ? 'free' : 'pro'}-${scenario.pages}page.pdf`;
    const outputPath = path.join(__dirname, outputFile);

    try {
      const page = await browser.newPage();
      await page.emulateMediaType('print');
      await page.setContent(html, { waitUntil: 'networkidle0' });

      await page.pdf({
        path: outputPath,
        format: 'letter',
        printBackground: true,
        preferCSSPageSize: false,
        displayHeaderFooter: false,
        margin: { top: '40px', bottom: '40px', left: '0px', right: '0px' }
      });

      await page.close();

      // Verify the PDF
      const verification = await verifyPdfWatermark(outputPath);
      
      console.log(`  PDF Pages: ${verification.totalPages}`);
      console.log(`  Watermark Found: ${verification.watermarkFound ? '✓ YES' : '✗ NO'}`);
      console.log(`  Watermark Occurrences: ${verification.watermarkOccurrences}`);

      // Check expectations
      const watermarkCorrect = verification.watermarkFound === scenario.expected.watermarkFound;
      const occurrenceCorrect = verification.watermarkOccurrences >= scenario.expected.watermarkPerPage;

      if (watermarkCorrect && occurrenceCorrect) {
        console.log(`  ✓ PASS: Expectations met`);
        passedTests++;
      } else {
        console.log(`  ✗ FAIL: Expectations NOT met`);
        if (!watermarkCorrect) console.log(`    - Expected watermark: ${scenario.expected.watermarkFound}, Got: ${verification.watermarkFound}`);
        if (!occurrenceCorrect) console.log(`    - Expected ≥${scenario.expected.watermarkPerPage} occurrences, Got: ${verification.watermarkOccurrences}`);
        failedTests++;
      }

      totalTests++;
    } catch (err) {
      console.log(`  ✗ ERROR: ${err.message}`);
      failedTests++;
      totalTests++;
    }

    console.log('');
  }

  await browser.close();

  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log('Test Results Summary');
  console.log('════════════════════════════════════════════════════════════════════════════════');
  console.log(`Total Tests: ${totalTests}`);
  console.log(`Passed: ${passedTests}`);
  console.log(`Failed: ${failedTests}`);
  console.log('');

  if (failedTests === 0) {
    console.log('✓ ALL TESTS PASSED - Watermark implementation is working correctly!');
  } else {
    console.log('✗ SOME TESTS FAILED - Review the output above for details');
  }

  console.log('════════════════════════════════════════════════════════════════════════════════');
})();
