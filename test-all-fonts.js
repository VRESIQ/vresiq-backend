const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');
const pdfParse = require('pdf-parse');

const cssPath = 'c:\\Users\\ACER\\Desktop\\PROJECTS\\ResumeBuilder\\resume-builder-frontend\\src\\components\\ResumePreview.css';
const cssContent = fs.readFileSync(cssPath, 'utf8');

const fontsToTest = [
  { id: 'sora', name: 'Sora', url: 'https://fonts.googleapis.com/css2?family=Sora:wght@300;400;600;700&display=swap', fontHeading: "'Sora', sans-serif" },
  { id: 'playfair', name: 'Playfair Display', url: 'https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;700;800&family=Lato:wght@300;400;700&display=swap', fontHeading: "'Playfair Display', serif" },
  { id: 'outfit', name: 'Outfit', url: 'https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&family=DM+Sans:wght@300;400;500&display=swap', fontHeading: "'Outfit', sans-serif" },
  { id: 'cormorant', name: 'Cormorant Garamond', url: 'https://fonts.googleapis.com/css2?family=Cormorant+Garamond:ital,wght@0,400;0,600;0,700;1,400&family=Jost:wght@300;400;500&display=swap', fontHeading: "'Cormorant Garamond', serif" },
  { id: 'spacegrotesk', name: 'Space Grotesk', url: 'https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@300;400;500;600;700&display=swap', fontHeading: "'Space Grotesk', sans-serif" },
  { id: 'raleway', name: 'Raleway', url: 'https://fonts.googleapis.com/css2?family=Raleway:wght@300;400;600;700;800&family=Nunito+Sans:wght@300;400;600&display=swap', fontHeading: "'Raleway', sans-serif" },
  { id: 'jakarta', name: 'Plus Jakarta Sans', url: 'https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700&family=Figtree:wght@300;400;500&display=swap', fontHeading: "'Plus Jakarta Sans', sans-serif" },
  { id: 'ibmplex', name: 'IBM Plex Sans', url: 'https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@300;400;500;600&display=swap', fontHeading: "'IBM Plex Sans', sans-serif" },
  { id: 'bodoni', name: 'Bodoni Moda', url: 'https://fonts.googleapis.com/css2?family=Bodoni+Moda:ital,wght@0,400;0,600;0,700;1,400&family=Mulish:wght@300;400;500;600&display=swap', fontHeading: "'Bodoni Moda', serif" },
  { id: 'nunito', name: 'Nunito', url: 'https://fonts.googleapis.com/css2?family=Nunito:wght@300;400;600;700;800&family=Open+Sans:wght@300;400;500&display=swap', fontHeading: "'Nunito', sans-serif" },
  { id: 'fraunces', name: 'Fraunces', url: 'https://fonts.googleapis.com/css2?family=Fraunces:ital,wght@0,300;0,400;0,700;1,400&family=Manrope:wght@300;400;500;600&display=swap', fontHeading: "'Fraunces', serif" },
  { id: 'sourcecode', name: 'Source Code Pro', url: 'https://fonts.googleapis.com/css2?family=Source+Code+Pro:wght@400;500;600&family=Source+Sans+3:wght@300;400;600&display=swap', fontHeading: "'Source Code Pro', monospace" },
  { id: 'tinos', name: 'Tinos', url: 'https://fonts.googleapis.com/css2?family=Tinos:ital,wght@0,400;0,700;1,400&display=swap', fontHeading: "'Tinos', serif" },
  { id: 'lora', name: 'Lora', url: 'https://fonts.googleapis.com/css2?family=Lora:ital,wght@0,400;0,500;0,600;0,700;1,400&display=swap', fontHeading: "'Lora', serif" }
];

const generateHtml = (font) => `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Resume - ${font.name}</title>
  <link rel="stylesheet" href="${font.url}">
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

    /* Global PDF Text Integrity Fixes */
    html, body, #resume-preview, #resume-preview * {
      font-variant-ligatures: none !important;
      font-feature-settings: "liga" 0, "clig" 0, "calt" 0, "dlig" 0, "hlig" 0, "kern" 0 !important;
      font-kerning: none !important;
      text-rendering: optimizeSpeed !important;
      letter-spacing: normal !important;
      word-spacing: normal !important;
      transition: none !important;
      transform: none !important;
    }

    .rp-ats-name {
      letter-spacing: normal !important;
      word-spacing: normal !important;
    }
  </style>
</head>
<body>
  <article id="resume-preview" class="resume-preview rp-ats_lead" data-template="ats_lead" data-lstyle="standard" style="--rp-font-heading: ${font.fontHeading};">
    <header class="rp-ats-header-left">
      <h1 class="rp-ats-name">Rithik</h1>
      <p class="rp-ats-role-left">VFX SUPERVISOR</p>
    </header>
  </article>
</body>
</html>
`;

(async () => {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  try {
    console.log('--- Testing All Fonts for Word Splitting ("Rithik") ---');
    for (const font of fontsToTest) {
      const page = await browser.newPage();
      await page.setViewport({ width: 816, height: 1056 });
      await page.emulateMediaType('print');
      
      const html = generateHtml(font);
      await page.setContent(html, { waitUntil: 'networkidle0' });
      await page.evaluateHandle('document.fonts.ready');
      
      const tempPdf = path.join(__dirname, `temp-${font.id}.pdf`);
      await page.pdf({
        path: tempPdf,
        format: 'letter',
        printBackground: true
      });
      
      const dataBuffer = fs.readFileSync(tempPdf);
      const data = await pdfParse(dataBuffer);
      const cleanText = data.text.trim().replace(/\s+/g, ' ');
      
      const pass = cleanText.includes('Rithik');
      console.log(`Font: ${font.name} (${font.id}) -> ${pass ? 'PASS' : 'FAIL'} (Extracted: "${cleanText.substring(0, 30)}")`);
      
      await page.close();
      if (fs.existsSync(tempPdf)) fs.unlinkSync(tempPdf);
    }
  } catch (err) {
    console.error(err);
  } finally {
    await browser.close();
  }
})();
