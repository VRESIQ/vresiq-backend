const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const mockResume = {
  id: "e2e-test-resume",
  title: "",
  template: "ats_lead",
  profileInfo: {
    fullName: "",
    designation: "",
    targetRole: "",
    summary: "",
    ProfilePreviewUrl: ""
  },
  contactInfo: {
    email: "",
    phone: "",
    location: "",
    linkedIn: "",
    github: "",
    website: ""
  },
  workExperience: [
    { company: "", role: "", location: "", startDate: "", endDate: "", description: "" }
  ],
  education: [
    { degree: "", institution: "", location: "", gpa: "", startDate: "", endDate: "", description: "" }
  ],
  skills: [
    { name: "", progress: 0 }
  ],
  projects: [
    { title: "", description: "", github: "", liveDemo: "" }
  ],
  certifications: [
    { title: "", issuer: "", year: "", certificateUrl: "" }
  ],
  languages: [
    { name: "", progress: 0 }
  ],
  interests: [
    ""
  ],
  customSections: {
    internships: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    achievements: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    publications: [
      { title: "", subtitle: "", date: "", authors: "", abstract: "", paperUrl: "" }
    ],
    volunteering: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    leadership: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    hackathons: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    openSource: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    awards: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    workshops: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    coursework: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    extracurriculars: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    technicalProfiles: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    patents: [
      { title: "", subtitle: "", date: "", description: "" }
    ],
    researchExperience: [
      { title: "", subtitle: "", date: "", description: "" }
    ]
  },
  decoratives: {
    sectionVisibility: "{\"experience\":true,\"internships\":true,\"languages\":true,\"interests\":true,\"achievements\":true,\"publications\":true,\"volunteering\":true,\"leadership\":true,\"hackathons\":true,\"openSource\":true,\"awards\":true,\"workshops\":true,\"coursework\":true,\"technicalProfiles\":true,\"extracurriculars\":true,\"patents\":true,\"researchExperience\":true}",
    sectionBullets: "{\"experience\":true,\"education\":true,\"skills\":true,\"languages\":true,\"projects\":true,\"publications\":true,\"volunteering\":true}"
  }
};

(async () => {
  const artifactsDir = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\bed31f36-2718-4bd8-8b1e-3de777db6e12';
  const screenshotsDir = path.join(artifactsDir, 'screenshots');
  if (!fs.existsSync(screenshotsDir)) {
    fs.mkdirSync(screenshotsDir, { recursive: true });
  }

  console.log('Launching Puppeteer browser with API Interceptor...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-web-security']
  });

  try {
    const page = await browser.newPage();
    await page.setRequestInterception(true);

    page.on('request', interceptedRequest => {
      const url = interceptedRequest.url();
      const method = interceptedRequest.method();

      if (url.includes('/api/auth/login')) {
        console.log('Mocking Login Request');
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
        console.log('Mocking Profile Request');
        interceptedRequest.respond({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: "user-123",
            name: "Visual Auditor",
            email: "admin@vresiq.com",
            subscriptionPlan: "premium",
            emailVerified: true
          })
        });
      } else if (url.includes('/api/resumes/e2e-test-resume/export-pdf')) {
        console.log('Mocking PDF Export request and executing local generator');
        try {
          const body = JSON.parse(interceptedRequest.postData());
          // Save HTML to tools
          fs.writeFileSync(path.join(__dirname, 'visual_audit_temp.html'), body.htmlContent);
          interceptedRequest.respond({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ success: true })
          });
        } catch (e) {
          interceptedRequest.respond({ status: 500, body: e.message });
        }
      } else if (url.includes('/api/resumes/e2e-test-resume')) {
        console.log('Mocking Resume Fetch');
        interceptedRequest.respond({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockResume)
        });
      } else if (url.includes('/api/resumes')) {
        console.log('Mocking User Resumes List');
        interceptedRequest.respond({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify([
            { id: "e2e-test-resume", title: "E2E Test Resume", template: "ats_lead" }
          ])
        });
      } else if (url.includes('/api/templates')) {
        console.log('Mocking Templates Fetch');
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

    console.log('1. Navigating to login...');
    await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle2' });

    console.log('2. Submitting login credentials...');
    await page.waitForSelector('input[name="email"]');
    await page.type('input[name="email"]', 'admin@vresiq.com');
    await page.type('input[name="password"]', 'admin2026@vresiq2026');
    await page.click('button[type="submit"]');

    console.log('3. Waiting for dashboard navigation...');
    // We mock sessionStorage inside the page first to validate profile check
    await page.evaluate(() => {
      sessionStorage.setItem("token", "mock-jwt-token-123");
    });
    
    // Direct navigate to editor
    console.log('4. Navigating to mock resume editor...');
    await page.goto('http://localhost:5173/resume/e2e-test-resume/edit', { waitUntil: 'networkidle2' });
    await page.waitForSelector('#resume-preview');
    
    console.log('5. Clicking through sections and performing visual captures...');
    const sections = [
      "Profile", "Contact", "Summary", "Experience", "Education", "Skills", 
      "Projects", "Certifications", "Languages", "Interests", "Internships", 
      "Achievements", "Publications", "Volunteering", "Leadership", "Hackathons", 
      "Open Source Contributions", "Awards", "Workshops", "Coursework", 
      "Technical Profiles", "Extracurricular Activities", "Patents", "Research Experience"
    ];

    for (const section of sections) {
      console.log(`Navigating to section: ${section}`);
      
      await page.evaluate((secName) => {
        const buttons = Array.from(document.querySelectorAll('.sidebar-btn'));
        const targetBtn = buttons.find(b => b.innerText.trim().toLowerCase() === secName.toLowerCase());
        if (targetBtn) {
          targetBtn.click();
        }
      }, section);
      
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      const widths = [375, 1024];
      for (const width of widths) {
        await page.setViewport({ width, height: 1400 });
        await new Promise(resolve => setTimeout(resolve, 500));
        
        const ssPath = path.join(screenshotsDir, `section_${section.toLowerCase().replace(/\s+/g, '_')}_${width}.png`);
        await page.screenshot({ path: ssPath, fullPage: true });
        console.log(`Saved screenshot to ${ssPath}`);
      }
    }

    console.log('6. Intercepting PDF generation triggering...');
    const downloadBtn = await page.evaluateHandle(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      return buttons.find(b => b.innerText.includes('Download PDF'));
    });
    
    if (downloadBtn && (await downloadBtn.asElement())) {
      console.log('Clicking Download PDF button...');
      await (await downloadBtn.asElement()).click();
      await new Promise(resolve => setTimeout(resolve, 2000));
      
      const tempHtml = path.join(__dirname, 'visual_audit_temp.html');
      if (fs.existsSync(tempHtml)) {
        console.log('Running local Puppeteer PDF generator on visual_audit_temp.html...');
        const { execSync } = require('child_process');
        const pdfOut = path.join(artifactsDir, 'downloaded_audit_resume.pdf');
        
        try {
          execSync(`node "${path.join(__dirname, 'pdf-generator.js')}" "${tempHtml}" "${pdfOut}" false`, { stdio: 'inherit' });
          console.log(`✓ PDF visual audit downloaded and saved to: ${pdfOut}`);
          fs.unlinkSync(tempHtml);
        } catch (execErr) {
          console.error('Failed to run local PDF generator:', execErr.message);
        }
      }
    }

    console.log('════════════════════════════════════════════════════════════════');
    console.log('VISUAL AUDIT SUITE COMPLETED SUCCESSFULLY');
    console.log('════════════════════════════════════════════════════════════════');

  } catch (err) {
    console.error('Audit Error:', err);
  } finally {
    await browser.close();
  }
})();
