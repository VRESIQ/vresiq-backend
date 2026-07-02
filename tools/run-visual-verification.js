const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const FRONTEND_URL = 'http://localhost:4173';
const OUTPUT_DIR = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\9900b19e-f9e8-434d-9d65-290f4b05187f';

const getMockResume = (template, headerStyle, accentColor) => {
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
      accentColor: accentColor || '#3a7d44',
      dividerStyle: 'line',
      photoShape: 'circle',
      progressStyle: 'bar',
      bulletStyle: 'disc',
      linkStyle: 'standard'
    }
  };
};

async function audit() {
  console.log('Starting visual validation screen captures...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  const page = await browser.newPage();
  await page.setCacheEnabled(false);
  
  // Set up API interception
  await page.setRequestInterception(true);
  let activeMockData = getMockResume('premium1', 'minimal', '#3a7d44');
  
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

  const cases = [
    { key: 'premium1', name: 'Prestige_minimal', hstyle: 'minimal', accent: '#3a7d44' },
    { key: 'premium1', name: 'Prestige_card', hstyle: 'card', accent: '#3a7d44' },
    { key: 'premium3', name: 'Elite_minimal', hstyle: 'minimal', accent: '#0d3b66' },
    { key: 'premium5', name: 'Apex_minimal', hstyle: 'minimal', accent: '#c1121f' },
    { key: 'premium9', name: 'Centered_fullbleed', hstyle: 'full-bleed', accent: '#2c6e49' },
    { key: 'tech_faang', name: 'Atlas_fullbleed', hstyle: 'full-bleed', accent: '#1a5fb4' }
  ];

  for (const c of cases) {
    console.log(`Rendering case: ${c.name}`);
    activeMockData = getMockResume(c.key, c.hstyle, c.accent);
    
    await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview');
    await new Promise(r => setTimeout(r, 1000));
    
    // Take screenshot of #resume-preview
    const previewEl = await page.$('#resume-preview');
    const shotPath = path.join(OUTPUT_DIR, `${c.name}.png`);
    await previewEl.screenshot({ path: shotPath });
    console.log(`Saved screenshot: ${shotPath}`);
  }

  await browser.close();
}

audit();
