// extract-pdf-text.js
// Extracts and displays text content from PDF files to debug what's actually being rendered
// Usage: node extract-pdf-text.js <pdf-file>

const fs = require('fs');

const extractTextFromPdf = (pdfPath) => {
  try {
    let pdfContent = fs.readFileSync(pdfPath, 'utf8');
    
    // Remove null bytes and other binary data that might interfere with text extraction
    pdfContent = pdfContent.replace(/\0/g, '');
    
    // Search for text streams in the PDF
    // Text in PDFs is often stored in text streams like "BT...Tj...ET" or similar
    // or in font encoding tables
    
    // Extract sections between BT (begin text) and ET (end text) markers
    const btEtRegex = /BT([\s\S]*?)ET/g;
    const btEtMatches = pdfContent.match(btEtRegex) || [];
    
    // Extract strings (often enclosed in parentheses or angle brackets)
    // This is a simplified approach
    const stringRegex = /\((.*?)\)|<([0-9A-Fa-f]+)>/g;
    const stringMatches = [];
    let match;
    while ((match = stringRegex.exec(pdfContent)) !== null) {
      if (match[1]) stringMatches.push(match[1]);
      if (match[2]) stringMatches.push(match[2]);
    }
    
    // Also look for plain text that might be readable
    const plainTextRegex = /[\x20-\x7E]{4,}/g;
    const plainTextMatches = pdfContent.match(plainTextRegex) || [];
    
    return {
      fileSize: fs.statSync(pdfPath).size,
      btEtSections: btEtMatches.length,
      foundStrings: [...new Set(stringMatches)],
      plainText: [...new Set(plainTextMatches)].filter(t => t.length > 0 && !t.match(/^[0-9\s]+$/))
    };
  } catch (err) {
    return { error: err.message };
  }
};

console.log('════════════════════════════════════════════════════════════════');
console.log('PDF Text Extraction Tool - Debug Footer Rendering');
console.log('════════════════════════════════════════════════════════════════');
console.log('');

const pdfFile = process.argv[2];
if (!pdfFile) {
  console.log('Usage: node extract-pdf-text.js <pdf-file>');
  console.log('');
  process.exit(1);
}

const result = extractTextFromPdf(pdfFile);

if (result.error) {
  console.log(`Error: ${result.error}`);
  process.exit(1);
}

console.log(`File: ${pdfFile}`);
console.log(`Size: ${result.fileSize} bytes`);
console.log('');

console.log('Extracted Text Blocks:');
console.log('─────────────────────────────────────────────────────────────');
result.plainText.slice(0, 30).forEach(text => {
  console.log(`  ${text}`);
});

if (result.plainText.length > 30) {
  console.log(`  ... and ${result.plainText.length - 30} more text blocks`);
}

console.log('');
console.log('Analysis:');
console.log('─────────────────────────────────────────────────────────────');

if (result.plainText.join('').includes('Made with VRESIQ')) {
  console.log('✓ Found "Made with VRESIQ" watermark');
} else if (result.plainText.join('').includes('VRESIQ')) {
  console.log('~ Found "VRESIQ" (partial match)');
} else {
  console.log('✗ Watermark text NOT found');
}

if (result.plainText.join('').includes('Test Footer')) {
  console.log('✓ Found "Test Footer" (debug footer text)');
} else if (result.plainText.join('').includes('Footer')) {
  console.log('~ Found "Footer" (partial match)');
} else {
  console.log('✗ No footer text found');
}

console.log('');
console.log('BT/ET Text Sections Found:', result.btEtSections);
console.log('════════════════════════════════════════════════════════════════');
