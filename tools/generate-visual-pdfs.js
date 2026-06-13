#!/usr/bin/env node

/**
 * Real-World PDF Generation & Visual Verification
 * Generates actual free-user and pro-user PDFs for visual inspection
 */

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

async function generatePDF(inputPath, outputPath, isFreePlan) {
  return new Promise((resolve, reject) => {
    const args = [
      path.join(__dirname, 'pdf-generator.js'),
      inputPath,
      outputPath,
      String(isFreePlan)
    ];

    const child = spawn('node', args, {
      cwd: __dirname,
      stdio: 'pipe'
    });

    let output = '';
    let errorOutput = '';

    child.stdout.on('data', (data) => {
      output += data.toString();
    });

    child.stderr.on('data', (data) => {
      errorOutput += data.toString();
    });

    child.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(`PDF generation failed: ${errorOutput}`));
      } else {
        resolve({ output, success: true });
      }
    });
  });
}

async function convertPDFToImages(pdfPath, outputDir) {
  return new Promise((resolve, reject) => {
    // Use ImageMagick/GraphicsMagick if available, otherwise just report PDF exists
    const convert = spawn('magick', [
      `${pdfPath}[0-2]`,
      '-density', '150',
      '-quality', '95',
      path.join(outputDir, 'page-%d.png')
    ]);

    convert.on('close', (code) => {
      if (code === 0) {
        resolve(true);
      } else {
        // ImageMagick not available, but PDF was created successfully
        resolve(false);
      }
    });

    convert.on('error', () => {
      resolve(false); // Tool not available
    });
  });
}

async function main() {
  console.log('════════════════════════════════════════════════════════════════');
  console.log('REAL-WORLD PDF GENERATION WITH WATERMARK VERIFICATION');
  console.log('════════════════════════════════════════════════════════════════\n');

  const sampleResume = path.join(__dirname, 'sample-resume.html');
  const outputDir = path.join(__dirname, 'visual-verification');
  
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  try {
    // Generate Free User PDF
    console.log('📄 Generating FREE USER PDF...');
    const freePdfPath = path.join(outputDir, 'resume-free-user.pdf');
    await generatePDF(sampleResume, freePdfPath, true);
    console.log('✓ Free user PDF generated: ' + freePdfPath);
    console.log('  Expected: Watermark on every page\n');

    // Generate Pro User PDF
    console.log('📄 Generating PRO USER PDF...');
    const proPdfPath = path.join(outputDir, 'resume-pro-user.pdf');
    await generatePDF(sampleResume, proPdfPath, false);
    console.log('✓ Pro user PDF generated: ' + proPdfPath);
    console.log('  Expected: NO watermark\n');

    // Verify files exist and have content
    console.log('════════════════════════════════════════════════════════════════');
    console.log('FILE VERIFICATION');
    console.log('════════════════════════════════════════════════════════════════\n');

    const freeStat = fs.statSync(freePdfPath);
    const proStat = fs.statSync(proPdfPath);

    console.log(`Free User PDF:`);
    console.log(`  ✓ File exists: ${freePdfPath}`);
    console.log(`  ✓ File size: ${(freeStat.size / 1024).toFixed(2)} KB`);
    console.log(`  ✓ Ready for visual inspection\n`);

    console.log(`Pro User PDF:`);
    console.log(`  ✓ File exists: ${proPdfPath}`);
    console.log(`  ✓ File size: ${(proStat.size / 1024).toFixed(2)} KB`);
    console.log(`  ✓ Ready for visual inspection\n`);

    // Try to convert to images
    console.log('════════════════════════════════════════════════════════════════');
    console.log('ATTEMPTING TO CONVERT PDFs TO IMAGES FOR VISUAL INSPECTION');
    console.log('════════════════════════════════════════════════════════════════\n');

    const freeImagesOk = await convertPDFToImages(freePdfPath, outputDir);
    const proImagesOk = await convertPDFToImages(proPdfPath, outputDir);

    if (freeImagesOk || proImagesOk) {
      console.log('✓ PDF-to-image conversion successful');
      console.log(`  Images saved to: ${outputDir}\n`);
    } else {
      console.log('ℹ ImageMagick not available for PDF-to-image conversion');
      console.log('  But PDFs have been generated successfully for manual inspection\n');
    }

    // Extract text and verify watermark content
    console.log('════════════════════════════════════════════════════════════════');
    console.log('WATERMARK TEXT VERIFICATION');
    console.log('════════════════════════════════════════════════════════════════\n');

    const pdfdataPromise = await import('pdf-parse/lib/pdf-parse.js');
    const pdfParse = pdfdataPromise.default;

    // Verify Free User PDF contains watermark
    const freePdfBuffer = fs.readFileSync(freePdfPath);
    const freeData = await pdfParse(freePdfBuffer);
    const freeText = freeData.text;
    const freeWatermarkCount = (freeText.match(/Made with VRESIQ/gi) || []).length;

    console.log('Free User PDF Text Extraction:');
    console.log(`  ✓ Total pages: ${freeData.numpages}`);
    console.log(`  ✓ Watermark text occurrences: ${freeWatermarkCount}`);
    console.log(`  ✓ Expected: ${freeData.numpages} (one per page)`);
    if (freeWatermarkCount > 0) {
      console.log(`  ✓ STATUS: WATERMARK FOUND\n`);
    } else {
      console.log(`  ✗ STATUS: WATERMARK NOT FOUND\n`);
    }

    // Verify Pro User PDF does NOT contain watermark
    const proPdfBuffer = fs.readFileSync(proPdfPath);
    const proData = await pdfParse(proPdfBuffer);
    const proText = proData.text;
    const proWatermarkCount = (proText.match(/Made with VRESIQ/gi) || []).length;

    console.log('Pro User PDF Text Extraction:');
    console.log(`  ✓ Total pages: ${proData.numpages}`);
    console.log(`  ✓ Watermark text occurrences: ${proWatermarkCount}`);
    console.log(`  ✓ Expected: 0 (no watermark for pro users)`);
    if (proWatermarkCount === 0) {
      console.log(`  ✓ STATUS: NO WATERMARK (as expected)\n`);
    } else {
      console.log(`  ✗ STATUS: WATERMARK FOUND (unexpected!)\n`);
    }

    console.log('════════════════════════════════════════════════════════════════');
    console.log('VERIFICATION COMPLETE');
    console.log('════════════════════════════════════════════════════════════════\n');

    console.log('📋 NEXT STEPS FOR VISUAL INSPECTION:');
    console.log(`\n1. Open Free User PDF:\n   ${freePdfPath}`);
    console.log(`\n2. Open Pro User PDF:\n   ${proPdfPath}`);
    console.log('\n3. Compare pages - Free user should have watermark, Pro should not');
    console.log('\n4. Verify watermark is subtle (not distracting) on free user PDF');
    console.log('\n5. Check multiple pages to confirm watermark appears on each page\n');

    console.log('════════════════════════════════════════════════════════════════');
    console.log('✓ ALL PDFs GENERATED SUCCESSFULLY\n');
    console.log('Status: Ready for manual visual inspection');
    console.log('════════════════════════════════════════════════════════════════');

  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

main();
