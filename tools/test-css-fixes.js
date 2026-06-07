const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');
const pdfParse = require('pdf-parse');

const cssPath = 'c:\\Users\\ACER\\Desktop\\PROJECTS\\ResumeBuilder\\resume-builder-frontend\\src\\components\\ResumePreview.css';
const cssContent = fs.readFileSync(cssPath, 'utf8');

const baseHtml = (styleOverrides) => `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Resume</title>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css?family=Fraunces:300,400,700,400i&family=Manrope:300,400,500,600">
  <style>
    ${cssContent}
  </style>
  <style>
    html, body {
      background: #ffffff !important;
      color: #000000 !important;
      margin: 0 !important;
      padding: 0 !important;
    }
    
    @page {
      size: letter;
      margin-top: 40px !important;
      margin-bottom: 40px !important;
      margin-left: 0 !important;
      margin-right: 0 !important;
    }

    #resume-preview, .resume-preview {
      position: static !important;
      display: block !important;
      width: 816px !important;
      max-width: 816px !important;
      height: auto !important;
      min-height: 1056px !important;
      margin: 0 auto !important;
      padding: 0 !important;
      box-shadow: none !important;
      border: none !important;
      overflow: visible !important;
      visibility: visible !important;
      box-sizing: border-box !important;
    }

    /* Target Element Style Overrides */
    .rp-ats-name {
      ${styleOverrides}
    }
  </style>
</head>
<body>
  <article id="resume-preview" class="resume-preview rp-ats_lead" data-template="ats_lead" data-lstyle="standard" style="--rp-font-heading: 'Fraunces', serif; --rp-font-body: 'Manrope', sans-serif;">
    <header class="rp-ats-header-left">
      <h1 class="rp-ats-name">Rithik</h1>
      <p class="rp-ats-role-left">VFX SUPERVISOR</p>
    </header>
  </article>
</body>
</html>
`;

const variations = [
  {
    name: 'Default font-variant-ligatures/font-feature-settings',
    style: `
      font-variant-ligatures: none !important;
      font-feature-settings: "liga" 0, "clig" 0, "calt" 0, "kern" 0 !important;
      font-kerning: none !important;
      text-rendering: optimizeSpeed !important;
    `
  },
  {
    name: 'OptimizeSpeed and LetterSpacingNormal',
    style: `
      text-rendering: optimizeSpeed !important;
      letter-spacing: normal !important;
    `
  },
  {
    name: 'OptimizeSpeed and LetterSpacingTinyNegative',
    style: `
      text-rendering: optimizeSpeed !important;
      letter-spacing: -0.01em !important;
    `
  },
  {
    name: 'GeometricPrecision',
    style: `
      text-rendering: geometricPrecision !important;
      font-kerning: none !important;
    `
  },
  {
    name: 'LetterSpacingNormal Only',
    style: `
      letter-spacing: normal !important;
    `
  },
  {
    name: 'FontFeatureSettings all off',
    style: `
      font-feature-settings: "liga" 0, "clig" 0, "calt" 0, "kern" 0, "rlig" 0, "dlig" 0, "hlig" 0 !important;
    `
  },
  {
    name: 'No custom style overrides at all (Raw template behavior)',
    style: ``
  },
  {
    name: 'Using system serif font fallback for print',
    style: `
      font-family: Georgia, serif !important;
    `
  }
];

(async () => {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  try {
    for (const v of variations) {
      console.log(`Testing variation: "${v.name}"...`);
      const page = await browser.newPage();
      await page.setViewport({ width: 816, height: 1056 });
      await page.emulateMediaType('print');
      
      const html = baseHtml(v.style);
      await page.setContent(html, { waitUntil: 'networkidle0' });
      await page.evaluateHandle('document.fonts.ready');
      
      const tempPdf = path.join(__dirname, 'temp-test.pdf');
      await page.pdf({
        path: tempPdf,
        format: 'letter',
        printBackground: true
      });
      
      const dataBuffer = fs.readFileSync(tempPdf);
      const data = await pdfParse(dataBuffer);
      const cleanText = data.text.trim().replace(/\s+/g, ' ');
      console.log(`- Extracted: "${cleanText}"`);
      
      const pass = cleanText.includes('Rithik');
      console.log(`- Result: ${pass ? 'PASS' : 'FAIL'}\n`);
      
      await page.close();
      if (fs.existsSync(tempPdf)) fs.unlinkSync(tempPdf);
    }
  } catch (err) {
    console.error(err);
  } finally {
    await browser.close();
  }
})();
