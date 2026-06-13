// verify-watermark-in-pdf.js
// Inspects generated PDFs to verify watermark text is present
// Usage: node verify-watermark-in-pdf.js <pdf-file>

const fs = require('fs');
const path = require('path');

const verifySinglePdf = (pdfPath) => {
  try {
    const pdfContent = fs.readFileSync(pdfPath, 'utf8');

    // Search for the watermark text in the PDF
    if (pdfContent.includes('Made with VRESIQ')) {
      return { success: true, found: true, message: 'Watermark text "Made with VRESIQ" found in PDF' };
    } else {
      // Try searching for parts of it
      if (pdfContent.includes('VRESIQ')) {
        return { success: true, found: true, message: 'Watermark text pattern found in PDF (partial match)' };
      }
      return { success: true, found: false, message: 'Watermark text NOT found in PDF - verification failed' };
    }
  } catch (err) {
    return { success: false, message: `Error reading PDF: ${err.message}` };
  }
};

console.log('═════════════════════════════════════════════════════════════════');
console.log('PDF Watermark Verification Tool');
console.log('═════════════════════════════════════════════════════════════════');
console.log('');

const pdfDirectory = process.argv[2] || path.join(__dirname, '.');
const pattern = process.argv[3] || 'test-*.pdf';

console.log(`Scanning directory: ${pdfDirectory}`);
console.log(`Pattern: ${pattern}`);
console.log('');

// Find all PDF files matching the pattern
const files = fs.readdirSync(pdfDirectory).filter(f =>
  f.endsWith('.pdf') && (
    pattern === '*' ||
    f.match(pattern.replace(/\*/g, '.*'))
  )
);

if (files.length === 0) {
  console.log('❌ No PDF files found matching the pattern');
  process.exit(1);
}

console.log(`Found ${files.length} PDF file(s) to verify:`);
console.log('');

let successCount = 0;
let failureCount = 0;

for (const file of files) {
  const filePath = path.join(pdfDirectory, file);
  const result = verifySinglePdf(filePath);

  if (result.found) {
    console.log(`✓ ${file}`);
    console.log(`  ${result.message}`);
    successCount++;
  } else {
    console.log(`✗ ${file}`);
    console.log(`  ${result.message}`);
    failureCount++;
  }
  console.log('');
}

console.log('═════════════════════════════════════════════════════════════════');
console.log(`Results: ${successCount} with watermark, ${failureCount} without`);
console.log('═════════════════════════════════════════════════════════════════');

if (failureCount > 0) {
  process.exit(1);
}
