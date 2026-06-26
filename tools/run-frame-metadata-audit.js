const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const FRONTEND_URL = 'http://localhost:4173';
const OUTPUT_DIR = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\9900b19e-f9e8-434d-9d65-290f4b05187f';

if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

const getMockResume = (template, headerStyle) => {
  return {
    id: "audit-resume",
    title: "Frame Metadata Audit",
    template: template,
    fontPairing: null,
    profileInfo: {
      fullName: "Jane R. Smith",
      designation: "Student",
      summary: "Proven track record of designing high-throughput cloud architectures and mentoring engineering teams. Skilled in React, Node.js, and Java Spring Boot with a focus on web performance and semantic page structures.",
      targetRole: "Software Engineer"
    },
    contactInfo: {
      email: "jane.smith@vresiq.com",
      phone: "+1 555-019-2831",
      location: "Hyderabad, India",
      linkedIn: "linkedin.com/in/janesmith",
      github: "github.com/janesmith",
      website: "rithikmettu"
    },
    workExperience: [
      {
        role: "Lead Software Architect",
        company: "Innovatech Solutions",
        duration: "2021 - Present",
        description: "Spearheaded cloud migration of key microservices, reducing latencies by 30%. Guided a team of 8 senior developers."
      }
    ],
    education: [
      {
        degree: "MS in Computer Science",
        school: "Stanford University",
        duration: "2018 - 2020"
      }
    ],
    skills: [
      { name: "React / Vite / Next.js", level: "Expert" }
    ],
    projects: [
      {
        title: "VRESIQ PDF Engine",
        description: "Designed a high-fidelity PDF renderer using Puppeteer with exact layout symmetry and print styling."
      }
    ],
    decoratives: {
      headerStyle: headerStyle || 'card',
      accentColor: '#1a5fb4',
      dividerStyle: 'line',
      photoShape: 'circle',
      progressStyle: 'bar',
      bulletStyle: 'disc',
      linkStyle: 'standard'
    }
  };
};

async function runFrameAudit() {
  console.log('🚀 Launching Frame header metadata visual audit...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  try {
    let mockResumeData = getMockResume('engineer_ats', 'card');
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
          body: JSON.stringify(mockResumeData)
        });
      } else if (url.includes('/api/resumes')) {
        interceptedRequest.respond({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([])
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

    console.log('Logging in...');
    await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('input[name="email"]');
    await page.type('input[name="email"]', 'admin@vresiq.com');
    await page.type('input[name="password"]', 'admin2026@vresiq2026');
    await page.click('button[type="submit"]');
    await page.evaluate(() => sessionStorage.setItem("token", "mock-jwt-token-123"));

    // 1. Frame Card mode screenshot
    console.log('Capturing Frame Card mode...');
    mockResumeData = getMockResume('engineer_ats', 'card');
    await page.setViewport({ width: 1280, height: 1200 });
    await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview', { timeout: 10000 });
    await new Promise(r => setTimeout(r, 1500));
    let previewEl = await page.$('#resume-preview');
    await previewEl.screenshot({ path: path.join(OUTPUT_DIR, 'frame_metadata_card.png') });

    // 2. Frame Full-bleed mode screenshot
    console.log('Capturing Frame Full-bleed mode...');
    mockResumeData = getMockResume('engineer_ats', 'full-bleed');
    await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview', { timeout: 10000 });
    await new Promise(r => setTimeout(r, 1500));
    previewEl = await page.$('#resume-preview');
    await previewEl.screenshot({ path: path.join(OUTPUT_DIR, 'frame_metadata_fullbleed.png') });

    // 3. Frame PDF
    console.log('Capturing Frame PDF...');
    await page.emulateMediaType('print');
    await page.pdf({
      path: path.join(OUTPUT_DIR, 'frame_metadata_pdf.pdf'),
      format: 'letter',
      printBackground: true
    });
    await page.emulateMediaType('screen');

    // 4. Standard template (for comparison)
    console.log('Capturing Standard template Card mode...');
    mockResumeData = getMockResume('ats_classic', 'card');
    await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview', { timeout: 10000 });
    await new Promise(r => setTimeout(r, 1500));
    previewEl = await page.$('#resume-preview');
    await previewEl.screenshot({ path: path.join(OUTPUT_DIR, 'standard_metadata_card.png') });

    console.log('Audit run completed successfully!');

  } catch (err) {
    console.error('Audit failed:', err);
  } finally {
    await browser.close();
  }
}

runFrameAudit();
