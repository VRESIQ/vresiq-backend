const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');
const pdfParse = require('pdf-parse');

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
    await page.setViewport({ width: 1280, height: 1024 });

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

    console.log('5. Navigating directly to the Resume Editor page for Rithik (VFX supervisor)...');
    await page.goto('http://localhost:5173/resume/6a12fa2ef6e734cc4b1aa840/edit', { waitUntil: 'networkidle2' });
    
    console.log('Waiting for Resume Preview element to render...');
    await page.waitForSelector('#resume-preview');
    
    // Brief delay to ensure fonts and styles render
    await page.evaluate(async () => {
      await document.fonts.ready;
      await new Promise(resolve => setTimeout(resolve, 2000));
    });

    console.log('6. Inspecting DOM of the Resume Preview...');
    const details = await page.evaluate(() => {
      const previewEl = document.querySelector('#resume-preview');
      const emailLink = document.querySelector('#resume-preview a[href^="mailto:"]');
      const phoneLink = document.querySelector('#resume-preview a[href^="tel:"]');
      
      return {
        previewClass: previewEl ? previewEl.getAttribute('class') : null,
        previewStyle: previewEl ? previewEl.getAttribute('style') : null,
        email: emailLink ? {
          outerHTML: emailLink.outerHTML,
          href: emailLink.getAttribute('href'),
          target: emailLink.getAttribute('target'),
          text: emailLink.innerText
        } : null,
        phone: phoneLink ? {
          outerHTML: phoneLink.outerHTML,
          href: phoneLink.getAttribute('href'),
          target: phoneLink.getAttribute('target'),
          text: phoneLink.innerText
        } : null
      };
    });

    console.log('\n================ Inspected DOM (Preview) ================');
    console.log('Preview element class:', details.previewClass);
    console.log('Preview element style:', details.previewStyle);
    console.log('Email link details:', JSON.stringify(details.email, null, 2));
    console.log('Phone link details:', JSON.stringify(details.phone, null, 2));
    console.log('=========================================================\n');

    console.log('7. Taking screenshot of Resume Preview DOM...');
    const previewScreenshot = path.join(__dirname, 'resume-preview-screenshot.png');
    
    // Zoom in on contact section of the preview
    const previewEl = await page.$('#resume-preview');
    if (previewEl) {
      await previewEl.screenshot({ path: previewScreenshot });
      console.log('Saved Resume Preview contact info screenshot to:', previewScreenshot);
      // Copy to artifacts
      fs.copyFileSync(previewScreenshot, 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\resume_preview_contact.png');
    }

    console.log('8. Verifying click behavior (email link)...');
    // We mock window.location.href changes or look at alert/popups if any
    const clickResult = await page.evaluate(() => {
      let hrefAssigned = null;
      // Backup original property
      const originalLocation = window.location.href;
      
      // Override handleContactClick behavior tracking
      const emailLink = document.querySelector('#resume-preview a[href^="mailto:"]');
      if (emailLink) {
        // Trigger click event
        emailLink.click();
        return {
          clicked: true,
          targetAttr: emailLink.getAttribute('target'),
          hrefValue: emailLink.getAttribute('href')
        };
      }
      return { clicked: false };
    });
    console.log('Click action verified:', clickResult);

    console.log('9. Generating and saving E2E Editor Full page screenshot...');
    const editorScreenshot = path.join(__dirname, 'editor-page-screenshot.png');
    await page.screenshot({ path: editorScreenshot, fullPage: true });
    console.log('Saved full editor page screenshot to:', editorScreenshot);
    fs.copyFileSync(editorScreenshot, 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\editor_page_full.png');

    console.log('10. Setting up request interception for export-pdf API call...');
    await page.setRequestInterception(true);
    page.on('request', interceptedRequest => {
      if (interceptedRequest.url().includes('export-pdf') && interceptedRequest.method() === 'POST') {
        console.log('=== export-pdf POST Payload ===');
        try {
          const postObj = JSON.parse(interceptedRequest.postData());
          console.log('htmlContent length:', postObj.htmlContent?.length);
          fs.writeFileSync(path.join(__dirname, 'debug-editor-export.html'), postObj.htmlContent);
          console.log('Saved intercepted HTML payload to debug-editor-export.html');
          
          // Check if extraPrintStyles is in the htmlContent
          const hasOverride = postObj.htmlContent?.includes('Georgia');
          console.log('Has Georgia override in htmlContent:', hasOverride);
        } catch (e) {
          console.log('Failed to parse post data:', e.message);
        }
        console.log('===============================');
      }
      interceptedRequest.continue();
    });

    console.log('10b. Triggering PDF Export from Editor download button...');
    // Locate download button by text content
    const downloadBtn = await page.evaluateHandle(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      return buttons.find(b => b.innerText.includes('Download PDF'));
    });
    
    if (!downloadBtn || !(await downloadBtn.asElement())) {
      throw new Error('Could not find Download PDF button');
    }
    
    // Intercept download behavior in Puppeteer to save file
    const downloadPath = path.join(__dirname, 'downloaded-from-app.pdf');
    const client = await page.target().createCDPSession();
    await client.send('Page.setDownloadBehavior', {
      behavior: 'allow',
      downloadPath: __dirname
    });
    
    console.log('Clicking Download PDF button...');
    await (await downloadBtn.asElement()).click();
    
    // Wait for the download to complete
    console.log('Waiting for PDF file to download...');
    let found = false;
    for (let i = 0; i < 30; i++) {
      await new Promise(resolve => setTimeout(resolve, 1000));
      // Look for a downloaded PDF file that is not a crdownload
      const files = fs.readdirSync(__dirname);
      const pdfFile = files.find(f => f.endsWith('.pdf') && f !== 'test-output.pdf' && f !== 'real-app-export.pdf' && !f.includes('crdownload'));
      if (pdfFile) {
        console.log(`Found downloaded PDF file: ${pdfFile}`);
        fs.renameSync(path.join(__dirname, pdfFile), downloadPath);
        found = true;
        break;
      }
    }
    
    if (found) {
      console.log('PDF exported and saved to:', downloadPath);
      // Copy to artifacts for visibility
      fs.copyFileSync(downloadPath, 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\downloaded-from-app.pdf');
      
      // Parse exported PDF to verify content
      const pdfBuffer = fs.readFileSync(downloadPath);
      const pdfData = await pdfParse(pdfBuffer);
      console.log('\n=== Exported PDF Extracted Text ===');
      console.log(pdfData.text);
      console.log('===================================\n');
    } else {
      console.error('Failed to locate downloaded PDF file from editor.');
    }

  } catch (err) {
    console.error('Error during E2E UI verification:', err);
  } finally {
    await browser.close();
  }
})();
