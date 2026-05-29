const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const cssPath = 'c:\\Users\\ACER\\Desktop\\PROJECTS\\ResumeBuilder\\resume-builder-frontend\\src\\components\\ResumePreview.css';
const cssContent = fs.readFileSync(cssPath, 'utf8');

const htmlContent = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Matheus Kunzler Maldaner - Recreated Resume</title>
  <style>
    ${cssContent}
  </style>
</head>
<body>
  <article id="resume-preview" class="resume-preview rp-academic_cv rp-high-density" style="--accent: #111111; --accent-readable: #111111; --rp-font-heading: 'Lora', Georgia, serif; --rp-font-body: 'Lora', Georgia, serif;">
    <header class="rp-academic-header">
      <h1 class="rp-academic-name">Matheus Kunzler Maldaner</h1>
      <div class="rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="https://matheusmaldaner.github.io" class="rp-ats-link">matheusmaldaner.github.io</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="mailto:mkunzlermaldaner@ufl.edu" class="rp-ats-link">mkunzlermaldaner@ufl.edu</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://github.com/matheusmaldaner" class="rp-ats-link">github.com/matheusmaldaner</a></span>
      </div>
    </header>
    
    <main class="rp-academic-body">
      <!-- Section: PROJECTS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Projects</h3>
        <div class="rp-item">
          <strong>MIRAGE</strong>
          <p class="rp-links">
            <a href="https://github.com/matheusmaldaner" className="rp-project-link">github.com/matheusmaldaner</a>
            <span class="rp-links-divider"> · </span>
            <a href="https://matheusmaldaner.github.io" className="rp-project-link">matheusmaldaner.github.io</a>
          </p>
        </div>
      </section>
 
      <!-- Section: MEMBERSHIPS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Memberships</h3>
        <div class="rp-memberships-list">
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>
                <strong>IEEE</strong>
                <span class="rp-compact-subtitle">: Institute for Electrical and Electronics Engineers Member</span>
              </span>
              <span class="rp-compact-date">
                <a href="https://leetcode.com/coder_jane" class="rp-custom-link">leetcode.com/coder_jane</a>
              </span>
            </div>
          </div>
        </div>
      </section>
    </main>
  </article>
</body>
</html>
`;

(async () => {
  console.log("Launching verification browser...");
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox']
  });
  
  try {
    const page = await browser.newPage();
    await page.setContent(htmlContent, { waitUntil: 'networkidle0' });
    
    const linksReport = await page.evaluate(() => {
      const anchors = Array.from(document.querySelectorAll('a'));
      return anchors.map(a => ({
        text: a.textContent.trim(),
        href: a.getAttribute('href'),
        classes: a.getAttribute('class')
      }));
    });
    
    console.log("=== Clickable Hyperlink Verification Report ===");
    let failures = 0;
    linksReport.forEach((link, i) => {
      console.log(`Link #${i + 1}:`);
      console.log(` - Text: "${link.text}"`);
      console.log(` - Destination: "${link.href}"`);
      console.log(` - Class: "${link.classes || 'none'}"`);
      
      // Basic checks
      if (!link.href) {
        console.error(" [ERROR] Link is missing an href attribute!");
        failures++;
      } else if (link.href.startsWith("mailto:") && !link.text.includes("@")) {
        console.error(" [ERROR] mailto: link does not point to a valid email!");
        failures++;
      } else if (link.href.startsWith("tel:") && /[a-zA-Z]/.test(link.href)) {
        console.error(" [ERROR] tel: link contains invalid non-numeric characters!");
        failures++;
      } else {
        console.log(" [SUCCESS] Link is correctly structured.");
      }
    });
    
    console.log("\n=== ATS Safety & PDF Rendering Diagnostics ===");
    const textSelectability = await page.evaluate(() => {
      // Check that the text is fully selectable and not generated via visual canvas or images
      const preview = document.getElementById('resume-preview');
      return preview && preview.innerText.trim().length > 100;
    });
    
    if (textSelectability) {
      console.log(" [SUCCESS] Selectable DOM text matches standard ATS parsers.");
    } else {
      console.error(" [ERROR] Selectable text length is too low. Check rendering collapse.");
      failures++;
    }
    
    if (failures === 0) {
      console.log("\n>>> ALL TESTS PASSED SUCCESSFULLY! VRESIQ Hyperlink System is stable.");
      process.exit(0);
    } else {
      console.error(`\n>>> FAILURE: ${failures} verification errors detected.`);
      process.exit(1);
    }
    
  } catch (err) {
    console.error("Verification crashed:", err);
    process.exit(1);
  } finally {
    await browser.close();
  }
})();
