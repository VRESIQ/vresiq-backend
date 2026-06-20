const puppeteer = require('puppeteer');

const mockResume = {
  id: "e2e-test-resume",
  title: "E2E Test Resume",
  template: "premium6",
  fontPairing: null,
  profileInfo: { fullName: "Jane Doe", designation: "Software Engineer", summary: "Experienced engineer", ProfilePreviewUrl: "" },
  contactInfo: { email: "jane@doe.com", phone: "+12345678", location: "San Francisco", linkedIn: "", github: "", website: "" },
  workExperience: [],
  education: [],
  skills: [],
  projects: [],
  certifications: [],
  languages: [],
  interests: [],
  customSections: {
    technicalProfiles: [
      { title: "LeetCode", date: "https://leetcode.com/coder_jane", description: "Top 1.5%\nKnight\n500+ Problems" }
    ]
  },
  decoratives: {
    sectionVisibility: JSON.stringify({ technicalProfiles: true }),
    sectionBullets: "{}"
  }
};

(async () => {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  const page = await browser.newPage();
  await page.setRequestInterception(true);

  page.on('request', interceptedRequest => {
    const url = interceptedRequest.url();
    if (url.includes('/api/auth/login')) {
      interceptedRequest.respond({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: "user-123",
          name: "Visual Auditor",
          email: "admin@vresiq.com",
          subscriptionPlan: "premium",
          emailVerified: true,
          token: "mock-jwt-token-123"
        })
      });
    } else if (url.includes('/api/auth/profile')) {
      interceptedRequest.respond({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: "user-123", name: "Visual Auditor", email: "admin@vresiq.com", subscriptionPlan: "premium", emailVerified: true })
      });
    } else if (url.includes('/api/resumes/e2e-test-resume')) {
      interceptedRequest.respond({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(mockResume)
      });
    } else if (url.includes('/api/resumes')) {
      interceptedRequest.respond({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: "e2e-test-resume", title: "E2E Test Resume", template: "premium6" }])
      });
    } else if (url.includes('/api/templates')) {
      interceptedRequest.respond({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ templates: [], userPlan: "premium" })
      });
    } else {
      interceptedRequest.continue();
    }
  });

  page.on('console', msg => console.log('PAGE LOG:', msg.text()));
  page.on('pageerror', err => console.error('PAGE ERROR:', err));

  try {
    console.log('Navigating to login...');
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });

    console.log('Logging in...');
    await page.waitForSelector('input[name="email"]', { timeout: 10000 });
    await page.type('input[name="email"]', 'admin@vresiq.com');
    await page.type('input[name="password"]', 'admin2026@vresiq2026');
    await page.click('button[type="submit"]');

    await page.evaluate(() => {
      sessionStorage.setItem("token", "mock-jwt-token-123");
    });

    console.log('Navigating to editor...');
    await page.goto('http://localhost:5173/resume/e2e-test-resume/edit', { waitUntil: 'networkidle2' });

    console.log('Waiting for resume preview...');
    await page.waitForSelector('#resume-preview', { timeout: 10000 });
    
    // Wait a moment for rendering
    await new Promise(resolve => setTimeout(resolve, 2000));

    console.log('Evaluating target elements in the DOM...');
    const results = await page.evaluate(() => {
      const elements = Array.from(document.querySelectorAll('*'));
      const matches = [];
      
      for (const el of elements) {
        if (el.childNodes.length === 1 && el.childNodes[0].nodeType === Node.TEXT_NODE && el.textContent.includes('Top 1.5%')) {
          const rules = [];
          for (const sheet of document.styleSheets) {
            try {
              for (const rule of sheet.cssRules) {
                if (rule.selectorText && el.matches(rule.selectorText)) {
                  rules.push({
                    selector: rule.selectorText,
                    color: rule.style.color,
                    important: rule.style.getPropertyPriority('color') === 'important',
                    cssText: rule.cssText
                  });
                }
              }
            } catch (e) {}
          }
          
          matches.push({
            tagName: el.tagName,
            className: el.className,
            id: el.id,
            text: el.textContent,
            computedColor: window.getComputedStyle(el).color,
            computedOpacity: window.getComputedStyle(el).opacity,
            matchingRules: rules
          });
        }
      }
      return matches;
    });

    console.log('Live inspection results:');
    console.log(JSON.stringify(results, null, 2));

  } catch (err) {
    console.error('An error occurred:', err.message);
    const bodyHtml = await page.evaluate(() => document.body.innerHTML);
    console.log('PAGE HTML ON FAILURE:', bodyHtml);
  } finally {
    await browser.close();
  }
})();
