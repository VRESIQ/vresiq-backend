const puppeteer = require('puppeteer');
const fs = require('fs');

const resolveBrowserExecutable = () => {
  const candidates = [
    process.env.PUPPETEER_EXECUTABLE_PATH,
    '/usr/bin/chromium',
    '/usr/bin/chromium-browser',
    '/usr/bin/google-chrome',
    '/opt/google/chrome/chrome'
  ].filter(Boolean);

  for (const candidate of candidates) {
    try {
      fs.accessSync(candidate, fs.constants.X_OK);
      return candidate;
    } catch {
      // continue
    }
  }
  return null;
};

(async () => {
  const args = process.argv.slice(2);
  if (args.length < 2) {
    console.error('Usage: node pdf-generator.js <inputHtmlPath> <outputPdfPath>');
    process.exit(1);
  }

  const inputHtmlPath = args[0];
  const outputPdfPath = args[1];

  let browser;
  try {
    const htmlContent = fs.readFileSync(inputHtmlPath, 'utf8');
    let resolvedExecutablePath = resolveBrowserExecutable();
    if (!resolvedExecutablePath) {
      // Prevent Puppeteer from reusing an invalid configured path.
      delete process.env.PUPPETEER_EXECUTABLE_PATH;
      resolvedExecutablePath = await puppeteer.executablePath();
    }

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

    // Emulate print media to use print-specific stylesheets and render clean layouts
    await page.emulateMediaType('print');

    await page.setContent(htmlContent, {
      waitUntil: 'networkidle0'
    });

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

    await page.pdf({
      path: outputPdfPath,
      format: 'letter',
      printBackground: true
    });

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
