const puppeteer = require('puppeteer');

const FRONTEND_URL = 'http://localhost:4173';

async function main() {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  try {
    const page = await browser.newPage();
    await page.setRequestInterception(true);

    page.on('request', interceptedRequest => {
      const url = interceptedRequest.url();
      if (url.includes('/api/auth/login')) {
        interceptedRequest.respond({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ token: "mock-jwt-token-123" })
        });
      } else if (url.includes('/api/auth/profile')) {
        interceptedRequest.respond({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ id: "user-123", subscriptionPlan: "premium" })
        });
      } else if (url.includes('/api/resumes/audit-resume')) {
        interceptedRequest.respond({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: "audit-resume",
            template: "academic_cv",
            decoratives: { headerStyle: 'full-bleed', accentColor: '#1a5fb4' }
          })
        });
      } else {
        interceptedRequest.continue();
      }
    });

    await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('input[name="email"]');
    await page.type('input[name="email"]', 'admin@vresiq.com');
    await page.type('input[name="password"]', 'admin2026@vresiq2026');
    await page.click('button[type="submit"]');
    await new Promise(r => setTimeout(r, 1000));
    await page.evaluate(() => sessionStorage.setItem("token", "mock-jwt-token-123"));

    await page.setViewport({ width: 1280, height: 1200 });
    await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview', { timeout: 10000 });

    const stats = await page.evaluate(() => {
      const el = document.querySelector('.rp-academic-header');
      // Inject rule overriding max-width
      el.style.setProperty('max-width', 'none', 'important');
      
      const container = document.querySelector('.rp-ats-container');
      const cRect = container.getBoundingClientRect();
      const hRect = el.getBoundingClientRect();
      const compStyle = window.getComputedStyle(container);
      const hStyle = window.getComputedStyle(el);

      return {
        containerWidth: cRect.width,
        headerWidth: hRect.width,
        headerMaxWidth: hStyle.maxWidth,
        leftMargin: Math.round(hRect.left - cRect.left),
        rightMargin: Math.round(cRect.right - hRect.right)
      };
    });

    console.log('STATS WITH max-width: none:');
    console.log(JSON.stringify(stats, null, 2));

  } catch (e) {
    console.error('ERROR:', e);
  } finally {
    await browser.close();
  }
}

main();
