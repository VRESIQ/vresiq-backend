const puppeteer = require('puppeteer');

const FRONTEND_URL = 'http://localhost:4173';

const getMockResume = (template, headerStyle) => {
  return {
    id: "audit-resume",
    title: "Audit Resume",
    template: template,
    fontPairing: null,
    profileInfo: {
      fullName: "Jane R. Smith",
      designation: "Senior Software Architect & Team Lead",
      summary: "Proven track record of designing high-throughput cloud architectures.",
      targetRole: "Principal Engineer"
    },
    contactInfo: {
      email: "jane.smith@vresiq.com",
      phone: "+1 555-019-2831",
      location: "San Francisco, CA",
      linkedIn: "linkedin.com/in/janesmith",
      github: "github.com/janesmith",
      website: "janesmith.dev"
    },
    decoratives: {
      headerStyle: headerStyle || 'minimal',
      accentColor: '#3a7d44',
      dividerStyle: 'line',
      photoShape: 'circle',
      progressStyle: 'bar',
      bulletStyle: 'disc',
      linkStyle: 'standard'
    }
  };
};

async function diagnose() {
  console.log('Starting template diagnostics with disabled cache...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  const page = await browser.newPage();
  await page.setCacheEnabled(false);
  
  // Set up API interception
  await page.setRequestInterception(true);
  let activeMockData = getMockResume('premium1', 'minimal');
  
  page.on('request', req => {
    const url = req.url();
    if (url.includes('/api/auth/login')) {
      req.respond({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ id: "user-123", token: "mock-jwt-token-123" })
      });
    } else if (url.includes('/api/auth/profile')) {
      req.respond({ status: 200, contentType: 'application/json', body: JSON.stringify({ id: "user-123" }) });
    } else if (url.includes('/api/resumes/audit-resume')) {
      req.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(activeMockData) });
    } else if (url.includes('/api/resumes')) {
      req.respond({ status: 200, contentType: 'application/json', body: JSON.stringify([{ id: "audit-resume" }]) });
    } else if (url.includes('/api/templates')) {
      req.respond({ status: 200, contentType: 'application/json', body: JSON.stringify({ templates: [], userPlan: "premium" }) });
    } else {
      req.continue();
    }
  });

  // Login
  await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'networkidle2' });
  await page.waitForSelector('input[name="email"]');
  await page.type('input[name="email"]', 'admin@vresiq.com');
  await page.type('input[name="password"]', 'admin2026@vresiq2026');
  await page.click('button[type="submit"]');
  await new Promise(r => setTimeout(r, 1000));

  const templates = [
    { key: 'premium1', name: 'Prestige', styles: ['minimal', 'card'] },
    { key: 'premium3', name: 'Elite', styles: ['minimal'] },
    { key: 'premium5', name: 'Apex', styles: ['minimal'] },
    { key: 'premium9', name: 'Centered', styles: ['full-bleed'] },
    { key: 'tech_faang', name: 'Atlas', styles: ['full-bleed'] }
  ];

  for (const t of templates) {
    for (const s of t.styles) {
      console.log(`\n=== Diagnosing ${t.name} (${t.key}) under ${s} ===`);
      activeMockData = getMockResume(t.key, s);
      if (t.key === 'premium1') {
        activeMockData.decoratives.accentColor = '#3a7d44';
      }
      
      await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
      await page.waitForSelector('#resume-preview');
      await new Promise(r => setTimeout(r, 1000));
      
      const diagnosis = await page.evaluate(() => {
        const preview = document.querySelector('#resume-preview');
        const contactLinks = Array.from(document.querySelectorAll('.rp-contact-link, .rp-ats-contact a'));
        const targetRole = document.querySelector('.rp-target-role-badge, .rp-ats-badge');
        const contactRow = document.querySelector('.rp-contact, .rp-ats-contact');
        
        return {
          hstyle: preview ? preview.getAttribute('data-hstyle') : 'N/A',
          lstyle: preview ? preview.getAttribute('data-lstyle') : 'N/A',
          links: contactLinks.map(link => ({
            text: link.innerText,
            classes: link.className,
            color: window.getComputedStyle(link).color,
            textDecoration: window.getComputedStyle(link).textDecoration
          })),
          targetRole: targetRole ? {
            classes: targetRole.className,
            color: window.getComputedStyle(targetRole).color,
            bg: window.getComputedStyle(targetRole).backgroundColor,
            padding: window.getComputedStyle(targetRole).padding
          } : null,
          contactRow: contactRow ? {
            classes: contactRow.className,
            display: window.getComputedStyle(contactRow).display,
            alignItems: window.getComputedStyle(contactRow).alignItems,
            justifyContent: window.getComputedStyle(contactRow).justifyContent
          } : null
        };
      });
      
      console.log(JSON.stringify(diagnosis, null, 2));
    }
  }

  await browser.close();
}

diagnose();
