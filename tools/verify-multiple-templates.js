const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');
const pdfParse = require('pdf-parse');

const cssPath = 'c:\\Users\\ACER\\Documents\\GitHub\\vresiq-frontend\\src\\components\\ResumePreview.css';
const cssContent = fs.readFileSync(cssPath, 'utf8');

const testCases = [
  { template: 'ats_lead', name: 'Rithik', designation: 'VFX SUPERVISOR' },
  { template: 'template1', name: 'Rithik Mettu', designation: 'Software Engineer' },
  { template: 'template2', name: 'Rithik Mettu', designation: 'Data Scientist' },
  { template: 'premium4', name: 'Rithik Mettu', designation: 'Product Manager' }
];

const generateHtml = (tc) => `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Resume - ${tc.template}</title>
  <style>
    ${cssContent}
  </style>
  <style>
    html, body {
      background: #ffffff !important;
      color: #000000 !important;
      margin: 0 !important;
      padding: 0 !important;
      overflow: visible !important;
    }
    
    @page {
      size: letter;
      margin-top: 40px !important;
      margin-bottom: 48px !important;
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

    body * {
      visibility: visible !important;
    }

    /* Global PDF Text Integrity Fixes */
    html, body, #resume-preview, #resume-preview * {
      font-variant-ligatures: none !important;
      font-feature-settings: "liga" 0, "clig" 0, "calt" 0 !important;
      text-rendering: auto !important;
      letter-spacing: 0 !important;
      letter-spacing: normal !important;
      word-spacing: normal !important;
      transition: none !important;
      transform: none !important;
    }

    /* Ensure specific template class overrides do not reinject letter-spacing */
    .rp-ats-name,
    .rp-ats-name-serif,
    .rp-sidebar-name,
    .rp-executive-name,
    .rp-sig-name,
    .rp-centered-name,
    .rp-role,
    .rp-executive-role,
    .rp-sig-role,
    .rp-centered-role,
    .rp-stitle {
      letter-spacing: 0 !important;
      letter-spacing: normal !important;
      word-spacing: normal !important;
    }
  </style>
</head>
<body>
  <article id="resume-preview" class="resume-preview rp-${tc.template}" data-template="${tc.template}" data-lstyle="standard">
    <header class="rp-header">
      <h1 class="rp-ats-name ${tc.template === 'premium4' ? 'rp-sig-name' : ''}">${tc.name}</h1>
      <p class="rp-role">${tc.designation}</p>
      <div class="rp-contact">
        <span><a href="mailto:rithik.mettu@gmail.com">rithik.mettu@gmail.com</a> | </span>
        <span><a href="tel:+911234567890">+91-1234 567 890</a> | </span>
        <span>Hyd, ind</span>
      </div>
    </header>
    <main class="rp-body">
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Experience</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>Senior VFX Supervisor</strong>
            <span>2024 - Present</span>
          </div>
          <div class="rp-item-sub">
            <span>Metaplex Studios</span>
          </div>
          <div class="rp-item-desc">
            <p>Led visual effects creation for multiple international feature films.</p>
          </div>
        </div>
      </section>
    </main>
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
    for (const tc of testCases) {
      console.log(`\nTesting template: ${tc.template}...`);
      const page = await browser.newPage();
      await page.setViewport({ width: 816, height: 1056 });
      await page.emulateMediaType('print');
      
      const html = generateHtml(tc);
      await page.setContent(html, { waitUntil: 'networkidle0' });
      await page.evaluateHandle('document.fonts.ready');
      
      const pdfPath = path.join(__dirname, `test-${tc.template}.pdf`);
      await page.pdf({
        path: pdfPath,
        format: 'letter',
        printBackground: true
      });
      
      const dataBuffer = fs.readFileSync(pdfPath);
      const data = await pdfParse(dataBuffer);
      
      console.log(`=== Extracted Text for ${tc.template} ===`);
      console.log(data.text.trim().split('\n').filter(Boolean).slice(0, 5).join('\n'));
      console.log('==========================================');
      
      const nameCorrect = data.text.includes(tc.name);
      if (nameCorrect) {
        console.log(`[PASS] ${tc.name} extracted successfully.`);
      } else {
        console.error(`[FAIL] ${tc.name} was split or missing. Full text includes:`);
        console.error(data.text);
      }
      
      await page.close();
      fs.unlinkSync(pdfPath);
    }
  } catch (err) {
    console.error(err);
  } finally {
    await browser.close();
  }
})();
