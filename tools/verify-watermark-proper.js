// verify-watermark-proper.js
// Use pdf-parse library to properly extract text from PDFs

const fs = require('fs');
const path = require('path');
const pdfParse = require('pdf-parse');

const verifyPdf = async (pdfPath) => {
  try {
    const pdfBuffer = fs.readFileSync(pdfPath);
    const pdfData = await pdfParse(pdfBuffer);

    const text = pdfData.text;
    const hasWatermark = text.includes('Made with VRESIQ') || text.includes('VRESIQ');

    return {
      success: true,
      hasWatermark,
      pageCount: pdfData.numpages,
      textLength: text.length,
      text: text.substring(0, 500)  // First 500 chars
    };
  } catch (err) {
    return {
      success: false,
      error: err.message
    };
  }
};

(async () => {
  console.log('════════════════════════════════════════════════════════════════');
  console.log('Proper PDF Text Verification using pdf-parse');
  console.log('════════════════════════════════════════════════════════════════');
  console.log('');

  const testFiles = [
    'test-free-1page.pdf',
    'test-free-2page.pdf',
    'test-css-watermark.pdf',
    'test-plain-text.pdf'
  ];

  for (const file of testFiles) {
    const filePath = path.join(__dirname, file);
    if (!fs.existsSync(filePath)) {
      console.log(`Skipping ${file} (not found)`);
      continue;
    }

    console.log(`Analyzing: ${file}`);
    const result = await verifyPdf(filePath);

    if (result.success) {
      console.log(`  Pages: ${result.pageCount}`);
      console.log(`  Text extracted: ${result.textLength} characters`);
      console.log(`  Has watermark: ${result.hasWatermark ? '✓ YES' : '✗ NO'}`);
      if (result.textLength > 0) {
        console.log(`  Sample text: ${result.text.replace(/\n/g, ' ').substring(0, 100)}...`);
      }
    } else {
      console.log(`  ✗ Error: ${result.error}`);
    }
    console.log('');
  }

  console.log('════════════════════════════════════════════════════════════════');
})();
