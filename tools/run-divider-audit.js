const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const FRONTEND_URL = 'http://localhost:4173';
const OUTPUT_DIR = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\ff16161c-c039-40a5-b655-7eefd1ef94e5';

if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

const getMockResume = (template, dividerStyle) => {
  return {
    id: "audit-resume",
    title: "Divider Audit Resume",
    template: template,
    fontPairing: null,
    profileInfo: {
      fullName: "Jane R. Smith",
      designation: "Senior Software Architect & Team Lead",
      summary: "Proven track record of designing high-throughput cloud architectures and mentoring engineering teams. Skilled in React, Node.js, and Java Spring Boot with a focus on web performance and semantic page structures.",
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
      headerStyle: 'minimal',
      accentColor: '#1a5fb4',
      dividerStyle: dividerStyle || 'line',
      photoShape: 'circle',
      progressStyle: 'bar',
      bulletStyle: 'disc',
      linkStyle: 'standard'
    }
  };
};

async function runDividerAudit() {
  console.log('🚀 Launching divider visual audit...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  try {
    let mockResumeData = getMockResume('ats_classic', 'line');
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

    const testTemplates = [
      { template: 'ats_classic', name: 'Standard' },
      { template: 'academic_cv', name: 'Scholar' },
      { template: 'engineer_ats', name: 'Frame' },
      { template: 'ats_experienced', name: 'Prime' }
    ];

    const dividerStyles = ['line', 'thick', 'dots', 'gradient'];

    for (const style of dividerStyles) {
      for (const t of testTemplates) {
        console.log(`Auditing divider style "${style}" on ${t.name} template...`);
        mockResumeData = getMockResume(t.template, style);
        
        await page.setViewport({ width: 1280, height: 1200 });
        await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
        await page.waitForSelector('#resume-preview', { timeout: 10000 });
        await new Promise(r => setTimeout(r, 1200));

        const previewEl = await page.$('#resume-preview');
        const shotPath = path.join(OUTPUT_DIR, `divider_${style}_${t.name.toLowerCase()}.png`);
        await previewEl.screenshot({ path: shotPath });
        console.log(`Saved screenshot: ${shotPath}`);
      }
    }

    // A4 Print PDF export test for Frame with dotted divider
    console.log('Generating A4 Print PDF with dots divider...');
    mockResumeData = getMockResume('engineer_ats', 'dots');
    await page.setViewport({ width: 816, height: 1100 });
    await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview');
    await page.emulateMediaType('print');
    const pdfPath = path.join(OUTPUT_DIR, `frame_dots_pdf.pdf`);
    await page.pdf({
      path: pdfPath,
      format: 'letter',
      printBackground: true
    });
    console.log(`Saved A4 print PDF: ${pdfPath}`);

  } catch (err) {
    console.error('Audit failed:', err);
  } finally {
    await browser.close();
    console.log('Browser closed. Divider visual audit complete.');
  }
}

runDividerAudit();
