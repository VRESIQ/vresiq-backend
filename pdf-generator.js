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
    console.error('Usage: node pdf-generator.js <inputHtmlPath> <outputPdfPath>');
    process.exit(1);
  }

  const inputHtmlPath = args[0];
  const outputPdfPath = args[1];

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
      printBackground: true,
      margin: {
        top: '0px',
        bottom: '0px',
        left: '0px',
        right: '0px'
      }
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
