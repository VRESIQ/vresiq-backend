const puppeteer = require('puppeteer');
const fs = require('fs');

const resolveBrowserExecutable = async () => {
  const configuredExecutablePath = process.env.PUPPETEER_EXECUTABLE_PATH?.trim();

  if (configuredExecutablePath) {
    try {
      fs.accessSync(configuredExecutablePath, fs.constants.X_OK);
      console.warn(`Using PUPPETEER_EXECUTABLE_PATH override: ${configuredExecutablePath}`);
      return configuredExecutablePath;
    } catch {
      console.warn(`Ignoring non-executable PUPPETEER_EXECUTABLE_PATH override: ${configuredExecutablePath}`);
    }
  }

  const previousExecutablePath = process.env.PUPPETEER_EXECUTABLE_PATH;
  try {
    delete process.env.PUPPETEER_EXECUTABLE_PATH;
    const cacheExecutablePath = await puppeteer.executablePath();
    console.info(`Resolved Puppeteer browser from cache: ${cacheExecutablePath}`);
    return cacheExecutablePath;
  } finally {
    if (previousExecutablePath !== undefined) {
      process.env.PUPPETEER_EXECUTABLE_PATH = previousExecutablePath;
    }
  }
};

(async () => {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    console.error('Usage: node pdf-generator.js <inputHtmlPath> <outputPdfPath> [isFreePlan]');
    process.exit(1);
  }

  const inputHtmlPath = args[0];
  const outputPdfPath = args[1];
  const isFreePlan = args[2] !== 'false';
  
  console.log('═══════════════════════════════════════════════════════════════');
  console.log('PDF Generator Watermark Configuration');
  console.log('═══════════════════════════════════════════════════════════════');
  console.log(`Input HTML Path: ${inputHtmlPath}`);
  console.log(`Output PDF Path: ${outputPdfPath}`);
  console.log(`Raw isFreePlan arg[2]: "${args[2]}"`);
  console.log(`Parsed isFreePlan: ${isFreePlan}`);
  console.log(`Watermark Status: ${isFreePlan ? '✓ ENABLED (Free Plan) - Watermark will be added' : '✗ DISABLED (Pro Plan) - No watermark'}`);
  console.log('═══════════════════════════════════════════════════════════════');

  let browser;
  try {
    const htmlContent = fs.readFileSync(inputHtmlPath, 'utf8');
    const resolvedExecutablePath = await resolveBrowserExecutable();

    browser = await puppeteer.launch({
      executablePath: resolvedExecutablePath,
      headless: 'new',
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--font-render-hinting=none'
      ]
    });

    const page = await browser.newPage();
    
    // Set viewport matching standard Letter width (8.5 inches * 96 DPI = 816px)
    await page.setViewport({
      width: 816,
      height: 1056,
      deviceScaleFactor: 1
    });

    await page.setContent(htmlContent, {
      waitUntil: 'networkidle0'
    });

    // Switch to print media only after the DOM has been mounted so print rules
    // operate on the rendered resume instead of the initial empty document.
    await page.emulateMediaType('print');

    await page.waitForSelector('#resume-preview', { timeout: 10000 });

    // Wait for all images on the page to load completely to prevent layout shifts during PDF render
    await page.evaluate(async () => {
      const images = Array.from(document.querySelectorAll('img'));
      await Promise.all(images.map(img => {
        if (img.complete) return;
        return new Promise((resolve) => {
          img.onload = resolve;
          img.onerror = resolve;
        });
      }));
    });

    // Ensure all web fonts are loaded completely before printing
    await page.evaluateHandle('document.fonts.ready');

    console.log('Starting PDF generation with Puppeteer...');
    if (isFreePlan) {
      console.log('✓ Footer Template ENABLED - Watermark "Made with VRESIQ" will appear on every page');
      console.log('  Font: 8px, Color: #999999, Opacity: 0.35');
      console.log('  Position: Bottom center of each page');
    } else {
      console.log('✗ Footer Template DISABLED - No watermark for pro users');
    }

    await page.pdf({
      path: outputPdfPath,
      format: 'letter',
      printBackground: true,
      preferCSSPageSize: false,    // MUST be false: when true, Puppeteer ignores margin:{} and footerTemplate gets 0 space
      displayHeaderFooter: true,
      headerTemplate: '<span></span>',
      footerTemplate: isFreePlan ? `
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
          margin: 0;
          display: block;
          opacity: 0.35;
          -webkit-print-color-adjust: exact;
          print-color-adjust: exact;
        ">
          Made with VRESIQ
        </div>
      ` : '<div></div>',
      margin: {
        top: '40px',
        bottom: '45px',   // Increased from 36px to ensure footer template has adequate space
        left: '0px',
        right: '0px'
      }
    });

    // URGENT: Apply standardized PDF document metadata
    try {
      const { PDFDocument } = require('pdf-lib');
      const pdfBytes = fs.readFileSync(outputPdfPath);
      const pdfDoc = await PDFDocument.load(pdfBytes, { updateMetadata: false });
      
      const now = new Date();
      pdfDoc.setTitle('Resume');
      pdfDoc.setAuthor('VRESIQ');
      pdfDoc.setSubject('Professional Resume');
      pdfDoc.setCreator('VRESIQ');
      pdfDoc.setProducer('VRESIQ');
      pdfDoc.setKeywords(['Resume', 'CV', 'ATS']);
      pdfDoc.setCreationDate(now);
      pdfDoc.setModificationDate(now);

      // Save without incremental updates to prevent post-generation metadata changes (revision count remains zero)
      const modifiedPdfBytes = await pdfDoc.save();
      fs.writeFileSync(outputPdfPath, modifiedPdfBytes);
      console.log('✓ PDF metadata standardized successfully via pdf-lib');
    } catch (metaErr) {
      console.error('✗ Warning: Failed to set PDF metadata:', metaErr);
    }

    console.log('═══════════════════════════════════════════════════════════════');
    console.log('PDF Generated Successfully');
    console.log('═══════════════════════════════════════════════════════════════');
    if (isFreePlan) {
      console.log('✓ Free plan PDF generated with watermark footer enabled');
      console.log('  "Made with VRESIQ" will appear at the bottom of every page');
    } else {
      console.log('✓ Pro plan PDF generated without watermark');
    }
    console.log('═══════════════════════════════════════════════════════════════');

    console.log('PDF generated successfully');
    process.exit(0);
  } catch (err) {
    console.error('Error during PDF generation:', err);
    process.exit(1);
  } finally {
    if (browser) {
      await browser.close();
    }
  }
})();
