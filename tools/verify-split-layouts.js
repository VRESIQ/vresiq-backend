const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

(async () => {
  console.log('Launching Puppeteer browser...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  try {
    const page = await browser.newPage();
    page.on('console', msg => console.log('PAGE LOG:', msg.text()));
    page.on('pageerror', err => console.error('PAGE ERROR:', err));
    await page.setViewport({ width: 1440, height: 1024 });

    console.log('1. Navigating to Login page...');
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });

    console.log('2. Entering admin credentials...');
    await page.waitForSelector('input[name="email"]');
    await page.type('input[name="email"]', 'admin@vresiq.com');
    await page.type('input[name="password"]', 'admin2026@vresiq2026');

    console.log('3. Clicking Sign In...');
    await page.click('button[type="submit"]');

    console.log('4. Waiting for Dashboard navigation...');
    await page.waitForNavigation({ waitUntil: 'networkidle2' });
    console.log('Current URL after login:', page.url());

    console.log('5. Navigating directly to the Resume Editor...');
    await page.goto('http://localhost:5173/resume/6a12fa2ef6e734cc4b1aa840/edit', { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview');

    // Select Customization section (which displays Templates, Fonts, and Decoratives)
    console.log('6. Selecting Customization section...');
    const sidebarButtons = await page.$$('.sidebar-btn');
    for (const btn of sidebarButtons) {
      const text = await page.evaluate(el => el.innerText, btn);
      if (text.includes('Customization')) {
        await btn.click();
        break;
      }
    }
    await new Promise(resolve => setTimeout(resolve, 1000));

    console.log('7. Clicking Split template card...');
    const templateCards = await page.$$('.editor-template');
    let clickedSplit = false;
    for (const card of templateCards) {
      const text = await page.evaluate(el => el.innerText, card);
      if (text.includes('Split')) {
        await card.click();
        clickedSplit = true;
        break;
      }
    }
    if (!clickedSplit) {
      console.log('WARNING: Could not find template card containing "Split".');
    }
    await new Promise(resolve => setTimeout(resolve, 1500));

    const styles = ['Minimal', 'Card', 'Full Bleed'];
    for (const style of styles) {
      console.log(`\n--- Verifying Split (${style}) ---`);

      // Click the header style chip
      console.log(`Selecting header style: ${style}...`);
      const chips = await page.$$('.dec-chip');
      for (const chip of chips) {
        const text = await page.evaluate(el => el.innerText, chip);
        if (text.trim() === style) {
          await chip.click();
          break;
        }
      }
      // Wait for layout updates
      await new Promise(resolve => setTimeout(resolve, 1500));

      // Take preview screenshot
      const styleNameClean = style.toLowerCase().replace(' ', '-');
      const previewPath = path.join(__dirname, `preview-split-${styleNameClean}.png`);
      const previewEl = await page.$('#resume-preview');
      if (previewEl) {
        await previewEl.screenshot({ path: previewPath });
        console.log(`Saved preview screenshot to: ${previewPath}`);
        fs.copyFileSync(previewPath, `C:\\Users\\ACER\\.gemini\\antigravity\\brain\\32478b21-4525-46c8-bf90-a0d7f9eda66a\\preview-split-${styleNameClean}.png`);
      }

      // Generate HTML payload for print preview
      console.log('Getting print HTML payload...');
      const htmlPayload = await page.evaluate(() => {
        const previewEl = document.querySelector('#resume-preview');
        const styles = Array.from(document.querySelectorAll('style')).map(s => s.outerHTML).join('\n');
        const links = Array.from(document.querySelectorAll('link[rel="stylesheet"]')).map(l => l.outerHTML).join('\n');
        
        return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  ${links}
  ${styles}
  <style>
    html, body { background: #fff !important; margin: 0 !important; padding: 0 !important; }
    #resume-preview { margin: 0 !important; }
  </style>
</head>
<body>
  ${previewEl.outerHTML}
</body>
</html>`;
      });

      // Load payload in print view page and take screenshot
      const printPage = await browser.newPage();
      await printPage.setViewport({ width: 816, height: 1056 }); // Letter page height
      await printPage.setContent(htmlPayload, { waitUntil: 'networkidle0' });
      await printPage.emulateMediaType('print');
      
      const printPath = path.join(__dirname, `pdf-split-${styleNameClean}.png`);
      await printPage.screenshot({ path: printPath, fullPage: true });
      console.log(`Saved print/PDF screenshot to: ${printPath}`);
      fs.copyFileSync(printPath, `C:\\Users\\ACER\\.gemini\\antigravity\\brain\\32478b21-4525-46c8-bf90-a0d7f9eda66a\\pdf-split-${styleNameClean}.png`);
      
      await printPage.close();
    }

  } catch (err) {
    console.error('Error during Split template E2E visual verification:', err);
  } finally {
    await browser.close();
    console.log('Verification script done.');
  }
})();
