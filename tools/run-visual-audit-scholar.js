const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const FRONTEND_URL = 'http://localhost:4173';
const OUTPUT_DIR = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\9900b19e-f9e8-434d-9d65-290f4b05187f';

if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

const getMockResume = (template, headerStyle, profileInfoOverrides = {}, contactInfoOverrides = {}) => {
  return {
    id: "audit-resume",
    title: "Audit Resume",
    template: template,
    fontPairing: null,
    profileInfo: {
      fullName: "Jane R. Smith",
      designation: "Senior Software Architect & Team Lead",
      summary: "Proven track record of designing high-throughput cloud architectures and mentoring engineering teams. Skilled in React, Node.js, and Java Spring Boot with a focus on web performance and semantic page structures.",
      targetRole: "Principal Engineer",
      ...profileInfoOverrides
    },
    contactInfo: {
      email: "jane.smith@vresiq.com",
      phone: "+1 555-019-2831",
      location: "San Francisco, CA",
      linkedIn: "linkedin.com/in/janesmith",
      github: "github.com/janesmith",
      website: "janesmith.dev",
      ...contactInfoOverrides
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
      { name: "React / Vite / Next.js", level: "Expert" },
      { name: "Node.js / Express", level: "Expert" },
      { name: "Java / Spring Boot", level: "Intermediate" }
    ],
    projects: [
      {
        title: "VRESIQ PDF Engine",
        description: "Designed a high-fidelity PDF renderer using Puppeteer with exact layout symmetry and print styling."
      }
    ],
    certifications: [
      { name: "AWS Certified Solutions Architect", organization: "Amazon Web Services" }
    ],
    languages: [
      { name: "English", level: "Native" }
    ],
    interests: ["Generative AI", "Aesthetics & Graphic Design"],
    decoratives: {
      headerStyle: headerStyle || 'minimal',
      accentColor: '#1a5fb4',
      dividerStyle: 'line',
      photoShape: 'circle',
      progressStyle: 'bar',
      bulletStyle: 'disc',
      linkStyle: 'standard'
    }
  };
};

async function runVisualAudit() {
  console.log('🚀 Launching visual audit...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  const measurements = [];

  try {
    let mockResumeData = getMockResume('academic_cv', 'card');

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
          body: JSON.stringify([{ id: "audit-resume", title: "Audit Resume", template: mockResumeData.template }])
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
    await page.waitForSelector('input[name="email"]', { timeout: 10000 });
    await page.type('input[name="email"]', 'admin@vresiq.com');
    await page.type('input[name="password"]', 'admin2026@vresiq2026');
    await page.click('button[type="submit"]');
    await page.evaluate(() => {
      sessionStorage.setItem("token", "mock-jwt-token-123");
    });
    console.log('Login complete.');

    const updatePreview = async (width = 1280) => {
      await page.setViewport({ width, height: 1200 });
      await page.goto(`${FRONTEND_URL}/resume/audit-resume/edit`, { waitUntil: 'networkidle2' });
      await page.waitForSelector('#resume-preview', { timeout: 10000 });
      await new Promise(resolve => setTimeout(resolve, 1500));
    };

    const measureLayout = async (template, style, width, mode = 'screen') => {
      return await page.evaluate((t, s, w, m) => {
        const container = document.querySelector('.rp-ats-container');
        const header = document.querySelector('.rp-academic-header, .rp-engineer-header, .rp-ats-header, .rp-ats-header-left');
        if (!container || !header) return null;

        const cRect = container.getBoundingClientRect();
        const hRect = header.getBoundingClientRect();

        return {
          template: t,
          style: s,
          viewportWidth: w,
          mode: m,
          containerWidth: Math.round(cRect.width),
          headerLeftMargin: Math.round(hRect.left - cRect.left),
          headerRightMargin: Math.round(cRect.right - hRect.right),
          topSpacing: Math.round(hRect.top - cRect.top)
        };
      }, template, style, width, mode);
    };

    const testCases = [
      { template: 'engineer_ats', name: 'frame', styles: ['card', 'full-bleed'] },
      { template: 'academic_cv', name: 'scholar', styles: ['card', 'full-bleed'] },
      { template: 'ats_classic', name: 'standard', styles: ['card', 'full-bleed'] },
      { template: 'ats_experienced', name: 'prime', styles: ['card', 'full-bleed'] }
    ];

    for (const tc of testCases) {
      for (const style of tc.styles) {
        for (const width of [1280, 1440]) {
          console.log(`Auditing ${tc.name} template in ${style} mode at ${width}px...`);
          mockResumeData = getMockResume(tc.template, style);
          await updatePreview(width);
          
          const previewEl = await page.$('#resume-preview');
          const shotPath = path.join(OUTPUT_DIR, `${tc.name}_${style}_${width}.png`);
          await previewEl.screenshot({ path: shotPath });
          console.log(`Saved screenshot: ${shotPath}`);

          // Measure layout properties
          const measurement = await measureLayout(tc.name, style, width, 'screen');
          if (measurement) measurements.push(measurement);
        }
      }
    }

    // A4 Print Preview
    for (const tc of testCases) {
      console.log(`Auditing A4 Print Preview for ${tc.name} template in full-bleed mode...`);
      mockResumeData = getMockResume(tc.template, 'full-bleed');
      await updatePreview(816);
      await page.emulateMediaType('print');
      
      const pdfShotPath = path.join(OUTPUT_DIR, `${tc.name}_full-bleed_pdf.png`);
      const previewElPdf = await page.$('#resume-preview');
      await previewElPdf.screenshot({ path: pdfShotPath });
      console.log(`Saved PDF preview screenshot: ${pdfShotPath}`);

      const measurement = await measureLayout(tc.name, 'full-bleed', 816, 'print');
      if (measurement) measurements.push(measurement);

      if (tc.name === 'frame') {
        const realPdfPath = path.join(OUTPUT_DIR, `frame-fullbleed.pdf`);
        await page.pdf({
          path: realPdfPath,
          format: 'letter',
          printBackground: true
        });
        console.log(`Saved real PDF: ${realPdfPath}`);
      }
      await page.emulateMediaType('screen');
    }

    // Edge cases for Frame
    const edgeCases = [
      {
        name: 'frame_edge_long-name',
        profileOverrides: { fullName: "Professor Alexander Bartholomew-Chamberlain, PhD" },
        contactOverrides: {}
      },
      {
        name: 'frame_edge_6-contacts',
        profileOverrides: {},
        contactOverrides: {
          email: "jane.smith@vresiq.com",
          phone: "+1 555-019-2831",
          location: "San Francisco, CA",
          linkedIn: "linkedin.com/in/janesmith",
          github: "github.com/janesmith",
          website: "janesmith.dev"
        }
      },
      {
        name: 'frame_edge_long-location',
        profileOverrides: {},
        contactOverrides: { location: "Department of Systems Science and Engineering, Institute of Advanced Technology, Cambridge, Massachusetts, USA" }
      },
      {
        name: 'frame_edge_no-badge',
        profileOverrides: { targetRole: "" },
        contactOverrides: {}
      },
      {
        name: 'frame_edge_no-subtitle',
        profileOverrides: { designation: "" },
        contactOverrides: {}
      }
    ];

    for (const ec of edgeCases) {
      console.log(`Auditing Frame edge case ${ec.name}...`);
      mockResumeData = getMockResume('engineer_ats', 'full-bleed', ec.profileOverrides, ec.contactOverrides);
      await updatePreview(1280);
      
      const el = await page.$('#resume-preview');
      const edgeShotPath = path.join(OUTPUT_DIR, `${ec.name}.png`);
      await el.screenshot({ path: edgeShotPath });
      console.log(`Saved edge case screenshot: ${edgeShotPath}`);
    }

    // Mobile Viewport
    console.log('Auditing mobile viewport 375px for Frame template...');
    mockResumeData = getMockResume('engineer_ats', 'full-bleed');
    await updatePreview(375);
    const mobileEl = await page.$('#resume-preview');
    const mobileShotPath = path.join(OUTPUT_DIR, `frame_mobile.png`);
    await mobileEl.screenshot({ path: mobileShotPath });
    console.log(`Saved mobile viewport screenshot: ${mobileShotPath}`);

    // Output measurements
    const measuresPath = path.join(OUTPUT_DIR, `measurements.json`);
    fs.writeFileSync(measuresPath, JSON.stringify(measurements, null, 2));
    console.log(`Saved measurements data: ${measuresPath}`);

  } catch (err) {
    console.error('Audit failed:', err);
  } finally {
    await browser.close();
    console.log('Browser closed. Visual audit complete.');
  }
}

runVisualAudit();
