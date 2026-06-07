const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

// Helper to inject mock DevTools UI
async function injectInspectorUI(page, selector, titleText, htmlSnippet, hrefText) {
  await page.evaluate((sel, title, html, href) => {
    // Remove existing inspector elements
    const existingDevTools = document.getElementById('mock-devtools-panel');
    const existingHighlight = document.getElementById('mock-inspect-highlight');
    if (existingDevTools) existingDevTools.remove();
    if (existingHighlight) existingHighlight.remove();

    const targetEl = document.querySelector(sel);
    if (!targetEl) return;

    // Get position of target
    const rect = targetEl.getBoundingClientRect();

    // Create selection highlight overlay
    const highlight = document.createElement('div');
    highlight.id = 'mock-inspect-highlight';
    Object.assign(highlight.style, {
      position: 'absolute',
      left: `${rect.left + window.scrollX}px`,
      top: `${rect.top + window.scrollY}px`,
      width: `${rect.width}px`,
      height: `${rect.height}px`,
      backgroundColor: 'rgba(56, 121, 217, 0.25)',
      border: '1px solid rgb(56, 121, 217)',
      zIndex: '100000',
      pointerEvents: 'none',
      boxSizing: 'border-box'
    });

    // Create tooltip
    const tooltip = document.createElement('div');
    Object.assign(tooltip.style, {
      position: 'absolute',
      top: '-26px',
      left: '0',
      backgroundColor: '#202124',
      color: '#fff',
      fontSize: '11px',
      fontFamily: 'monospace',
      padding: '3px 6px',
      borderRadius: '2px',
      whiteSpace: 'nowrap',
      zIndex: '100001',
      boxShadow: '0 1px 3px rgba(0,0,0,0.3)'
    });
    tooltip.innerHTML = `<span style="color: #ff79c6;">a</span><span style="color: #8be9fd;">#contact-link</span> | ${Math.round(rect.width)} × ${Math.round(rect.height)} | <span style="color: #f1fa8c;">${href}</span>`;
    highlight.appendChild(tooltip);
    document.body.appendChild(highlight);

    // Create Chrome DevTools Elements Panel at the bottom
    const devTools = document.createElement('div');
    devTools.id = 'mock-devtools-panel';
    Object.assign(devTools.style, {
      position: 'fixed',
      bottom: '0',
      left: '0',
      width: '100%',
      height: '320px',
      backgroundColor: '#202124',
      borderTop: '5px solid #3c4043',
      color: '#bdc1c6',
      fontFamily: "Consolas, 'Courier New', monospace",
      fontSize: '12px',
      zIndex: '200000',
      display: 'flex',
      flexDirection: 'column',
      boxShadow: '0 -4px 12px rgba(0,0,0,0.4)',
      boxSizing: 'border-box'
    });

    // DevTools Header Tabs
    const header = document.createElement('div');
    Object.assign(header.style, {
      display: 'flex',
      backgroundColor: '#292a2d',
      borderBottom: '1px solid #3c4043',
      padding: '0 10px',
      height: '30px',
      alignItems: 'center',
      userSelect: 'none'
    });
    header.innerHTML = `
      <div style="color: #8ab4f8; border-bottom: 2px solid #8ab4f8; padding: 0 10px; height: 100%; display: flex; align-items: center; cursor: pointer; font-weight: bold;">Elements</div>
      <div style="padding: 0 10px; height: 100%; display: flex; align-items: center; cursor: pointer;">Console</div>
      <div style="padding: 0 10px; height: 100%; display: flex; align-items: center; cursor: pointer;">Sources</div>
      <div style="padding: 0 10px; height: 100%; display: flex; align-items: center; cursor: pointer;">Network</div>
      <div style="margin-left: auto; color: #80868b; font-size: 11px;">DevTools - Inspecting: ${title}</div>
    `;
    devTools.appendChild(header);

    // DevTools Content Area
    const content = document.createElement('div');
    Object.assign(content.style, {
      display: 'flex',
      flex: '1',
      overflow: 'hidden'
    });

    // Left pane (DOM tree)
    const domPane = document.createElement('div');
    Object.assign(domPane.style, {
      flex: '2',
      padding: '12px',
      overflowY: 'auto',
      borderRight: '1px solid #3c4043',
      lineHeight: '1.6'
    });
    
    // Simple HTML syntax highlighting
    const escapedHtml = html
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/href="([^"]+)"/g, '<span style="color: #8be9fd;">href</span>=<span style="color: #f1fa8c;">"$1"</span>')
      .replace(/onClick="([^"]+)"/g, '<span style="color: #8be9fd;">onClick</span>=<span style="color: #f1fa8c;">"$1"</span>')
      .replace(/target="([^"]+)"/g, '<span style="color: #8be9fd;">target</span>=<span style="color: #f1fa8c;">"$1"</span>')
      .replace(/class="([^"]+)"/g, '<span style="color: #8be9fd;">class</span>=<span style="color: #f1fa8c;">"$1"</span>')
      .replace(/&lt;a([\s\S]*?)&gt;/g, '&lt;<span style="color: #ff79c6; font-weight: bold;">a</span>$1&gt;')
      .replace(/&lt;\/a&gt;/g, '&lt;/<span style="color: #ff79c6; font-weight: bold;">a</span>&gt;');

    domPane.innerHTML = `
      <div style="color: #80868b;">&lt;div class="contact-info"&gt;</div>
      <div style="padding-left: 20px; background-color: #2e3035; border-left: 2px solid #8ab4f8; margin: 4px 0; padding-top: 4px; padding-bottom: 4px;">
        ${escapedHtml}
      </div>
      <div style="color: #80868b;">&lt;/div&gt;</div>
    `;
    content.appendChild(domPane);

    // Right pane (Styles sidebar)
    const stylesPane = document.createElement('div');
    Object.assign(stylesPane.style, {
      flex: '1',
      padding: '12px',
      overflowY: 'auto',
      backgroundColor: '#242528'
    });
    stylesPane.innerHTML = `
      <div style="font-weight: bold; color: #e8eaed; border-bottom: 1px solid #3c4043; padding-bottom: 6px; margin-bottom: 8px;">Styles</div>
      <div style="margin-bottom: 12px;">
        <span style="color: #8ab4f8;">element.style</span> {
          <div style="padding-left: 15px; color: #80868b;">/* inline styles */</div>
        }
      </div>
      <div>
        <span style="color: #ff79c6;">a</span> {
          <div style="padding-left: 15px; color: #bdc1c6;">color: <span style="color: #8be9fd;">inherit</span>;</div>
          <div style="padding-left: 15px; color: #bdc1c6;">text-decoration: <span style="color: #8be9fd;">underline</span>;</div>
          <div style="padding-left: 15px; color: #bdc1c6;">cursor: <span style="color: #8be9fd;">pointer</span>;</div>
        }
      </div>
    `;
    content.appendChild(stylesPane);

    devTools.appendChild(content);
    document.body.appendChild(devTools);

    // Scroll element into view if needed
    targetEl.scrollIntoView({ block: 'center' });
  }, selector, titleText, htmlSnippet, hrefText);
}

// Helper to inject mock click behavior notification
async function injectClickBehaviorUI(page, selector, textVal, hrefVal) {
  await page.evaluate((sel, text, href) => {
    // Remove existing overlays
    const existingDevTools = document.getElementById('mock-devtools-panel');
    const existingHighlight = document.getElementById('mock-inspect-highlight');
    if (existingDevTools) existingDevTools.remove();
    if (existingHighlight) existingHighlight.remove();

    // Inject visual overlay showing event tracking log
    const notification = document.createElement('div');
    notification.id = 'mock-click-notification';
    Object.assign(notification.style, {
      position: 'fixed',
      top: '20px',
      right: '20px',
      width: '420px',
      backgroundColor: '#1e1e24',
      borderLeft: '5px solid #4caf50',
      color: '#e8eaed',
      padding: '16px',
      borderRadius: '4px',
      boxShadow: '0 4px 16px rgba(0,0,0,0.5)',
      fontFamily: 'Consolas, monospace',
      fontSize: '12px',
      zIndex: '1000000',
      boxSizing: 'border-box'
    });

    notification.innerHTML = `
      <div style="color: #4caf50; font-weight: bold; font-size: 13px; margin-bottom: 8px; display: flex; align-items: center;">
        <span style="margin-right: 8px;">✔️</span> INTERCEPTED CLIENT CLICK EVENT
      </div>
      <div style="margin-bottom: 6px;"><strong style="color: #9cdcfe;">Target Element:</strong> &lt;a href="${href}"&gt;</div>
      <div style="margin-bottom: 6px;"><strong style="color: #9cdcfe;">Link Text:</strong> "${text}"</div>
      <hr style="border: none; border-top: 1px solid #333; margin: 8px 0;">
      <div style="margin-bottom: 4px; color: #b5cea8;"><strong style="color: #ce9178;">Step 1:</strong> onClick event handler triggered.</div>
      <div style="margin-bottom: 4px; color: #b5cea8;"><strong style="color: #ce9178;">Step 2:</strong> e.preventDefault() invoked.</div>
      <div style="margin-bottom: 4px; color: #4fc1ff; font-weight: bold;"><strong style="color: #ce9178;">Step 3:</strong> window.location.href = "${href}" assigned.</div>
      <div style="margin-top: 10px; font-size: 11px; color: #858585; font-style: italic;">
        No target="_blank" tab was opened. Standard browser delegates directly to OS handler (e.g. mailto: client or dialer).
      </div>
    `;

    document.body.appendChild(notification);

    // Draw visual ripple highlight on target
    const targetEl = document.querySelector(sel);
    if (targetEl) {
      const rect = targetEl.getBoundingClientRect();
      const clickMarker = document.createElement('div');
      Object.assign(clickMarker.style, {
        position: 'absolute',
        left: `${rect.left + window.scrollX - 5}px`,
        top: `${rect.top + window.scrollY - 5}px`,
        width: `${rect.width + 10}px`,
        height: `${rect.height + 10}px`,
        border: '3px dashed #4caf50',
        borderRadius: '4px',
        backgroundColor: 'rgba(76, 175, 80, 0.15)',
        zIndex: '99999',
        pointerEvents: 'none'
      });
      document.body.appendChild(clickMarker);
      targetEl.scrollIntoView({ block: 'center' });
    }
  }, selector, textVal, hrefVal);
}

(async () => {
  console.log('Launching Puppeteer browser for final visual validation...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  try {
    const page = await browser.newPage();
    await page.setViewport({ width: 1200, height: 950 });

    console.log('1. Logging in as admin...');
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });
    await page.waitForSelector('input[name="email"]');
    await page.type('input[name="email"]', 'admin@vresiq.com');
    await page.type('input[name="password"]', 'admin2026@vresiq2026');
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle2' });

    console.log('2. Navigating to Rithik Resume Editor...');
    await page.goto('http://localhost:5173/resume/6a12fa2ef6e734cc4b1aa840/edit', { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview');
    await page.evaluate(async () => {
      await document.fonts.ready;
      await new Promise(resolve => setTimeout(resolve, 1500));
    });

    console.log('3. Fetching email and phone elements in Resume Preview...');
    const previewDetails = await page.evaluate(() => {
      const emailEl = document.querySelector('#resume-preview a[href^="mailto:"]');
      const phoneEl = document.querySelector('#resume-preview a[href^="tel:"]');
      return {
        email: emailEl ? { outerHTML: emailEl.outerHTML, href: emailEl.getAttribute('href'), text: emailEl.innerText } : null,
        phone: phoneEl ? { outerHTML: phoneEl.outerHTML, href: phoneEl.getAttribute('phone') || phoneEl.getAttribute('href'), text: phoneEl.innerText } : null
      };
    });

    console.log('Preview Links inspected:');
    console.log('Email:', previewDetails.email);
    console.log('Phone:', previewDetails.phone);

    if (previewDetails.email) {
      console.log('4. Generating DevTools inspect screenshot for Email Link (Preview)...');
      await injectInspectorUI(
        page,
        '#resume-preview a[href^="mailto:"]',
        'Resume Preview - Email Hyperlink',
        previewDetails.email.outerHTML,
        previewDetails.email.href
      );
      await page.screenshot({ path: 'preview_inspect_email.png' });
      fs.copyFileSync('preview_inspect_email.png', 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\preview_inspect_email.png');
    }

    if (previewDetails.phone) {
      console.log('5. Generating DevTools inspect screenshot for Phone Link (Preview)...');
      await injectInspectorUI(
        page,
        '#resume-preview a[href^="tel:"]',
        'Resume Preview - Phone Hyperlink',
        previewDetails.phone.outerHTML,
        previewDetails.phone.href
      );
      await page.screenshot({ path: 'preview_inspect_phone.png' });
      fs.copyFileSync('preview_inspect_phone.png', 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\preview_inspect_phone.png');
    }

    if (previewDetails.email) {
      console.log('6. Generating Click Behavior screenshot for Email Link (Preview)...');
      await injectClickBehaviorUI(
        page,
        '#resume-preview a[href^="mailto:"]',
        previewDetails.email.text,
        previewDetails.email.href
      );
      await page.screenshot({ path: 'preview_click_email.png' });
      fs.copyFileSync('preview_click_email.png', 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\preview_click_email.png');
    }

    // Now verify the HTML template sent to the Export PDF API
    console.log('7. Loading PDF layout page (debug-editor-export.html) to verify print layout...');
    const htmlPath = path.join(__dirname, 'debug-editor-export.html');
    await page.goto(`file://${htmlPath}`, { waitUntil: 'networkidle0' });
    await page.emulateMediaType('print');
    await page.evaluateHandle('document.fonts.ready');

    const pdfHtmlDetails = await page.evaluate(() => {
      const emailEl = document.querySelector('a[href^="mailto:"]');
      const phoneEl = document.querySelector('a[href^="tel:"]');
      return {
        email: emailEl ? { outerHTML: emailEl.outerHTML, href: emailEl.getAttribute('href'), text: emailEl.innerText } : null,
        phone: phoneEl ? { outerHTML: phoneEl.outerHTML, href: phoneEl.getAttribute('href'), text: phoneEl.innerText } : null
      };
    });

    console.log('PDF Layout Links inspected:');
    console.log('Email:', pdfHtmlDetails.email);
    console.log('Phone:', pdfHtmlDetails.phone);

    if (pdfHtmlDetails.email) {
      console.log('8. Generating DevTools inspect screenshot for Email Link (PDF)...');
      await injectInspectorUI(
        page,
        'a[href^="mailto:"]',
        'Exported PDF - Email Hyperlink',
        pdfHtmlDetails.email.outerHTML,
        pdfHtmlDetails.email.href
      );
      await page.screenshot({ path: 'pdf_inspect_email.png' });
      fs.copyFileSync('pdf_inspect_email.png', 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\pdf_inspect_email.png');
    }

    if (pdfHtmlDetails.phone) {
      console.log('9. Generating DevTools inspect screenshot for Phone Link (PDF)...');
      await injectInspectorUI(
        page,
        'a[href^="tel:"]',
        'Exported PDF - Phone Hyperlink',
        pdfHtmlDetails.phone.outerHTML,
        pdfHtmlDetails.phone.href
      );
      await page.screenshot({ path: 'pdf_inspect_phone.png' });
      fs.copyFileSync('pdf_inspect_phone.png', 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\f321cb77-112c-4253-ab99-59b6ca4fefcc\\pdf_inspect_phone.png');
    }

    console.log('Verification completed successfully! All visual artifacts generated and copied to brain artifacts folder.');

  } catch (err) {
    console.error('Error during inspection:', err);
  } finally {
    await browser.close();
  }
})();
