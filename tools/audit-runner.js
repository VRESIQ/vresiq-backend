const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

const mockResume = {
  id: "e2e-test-resume",
  title: "E2E Test Resume",
  template: "ats_lead",
  profileInfo: {
    fullName: "Dr. Alexander-Constantine Maximillian von Hohenzollern-Sigmaringen-Braganza III",
    designation: "Chief Executive Visual Effects Supervisor & Senior Technical Lead Practitioner",
    targetRole: "Principal Solutions Architect & Director of Creative Technologies",
    summary: "Highly motivated and results-driven technical architect with a track record of building complex distributed web systems using React, Node.js, Express, Go, MongoDB, Redis, PostgreSQL, and Docker while enforcing layout boundaries across 8 responsive breakpoints.",
    ProfilePreviewUrl: ""
  },
  contactInfo: {
    email: "alexander.von.hohenzollern-sigmaringen-braganza-iii@subdomain.verbose-domain-name-with-extreme-length.com",
    phone: "+91 98765 43210",
    location: "Hyderabad, Telangana, Republic of India, South Asia",
    linkedIn: "https://linkedin.com/in/alexander-constantine-von-hohenzollern-sigmaringen-braganza-iii-9b8a7c6d5e",
    github: "https://github.com/alexander-constantine-von-hohenzollern-sigmaringen-braganza-iii",
    website: "https://subdomain.really-long-and-extremely-verbose-portfolio-domain-name-with-multiple-levels-of-nesting-and-extra-query-parameters.com/projects/resume-builder/visual-audit?session=abcdef1234567890"
  },
  workExperience: [
    {
      company: "Google India Technical Operations & Engineering Center",
      role: "Lead Software Engineering Architect Specialist",
      location: "Hyderabad Campus",
      startDate: "Jan 2023",
      endDate: "Present",
      description: "Led a team of 15 engineers to rewrite core search index ranking pipelines, reducing latency by 45% and optimizing memory allocations.\nBuilt layout boundary systems that prevent any visual overflow of helper texts, URLs, and fields.\nMentored junior developers and designed internal microservice frameworks."
    },
    {
      company: "Meta Platforms Technologies Group",
      role: "Senior Frontend Infrastructure Engineer",
      location: "Remote, India",
      startDate: "2020",
      endDate: "2022",
      description: "Developed internal UI component library used by 5,000+ internal tooling developers.\nImplemented automated screenshot visual validation testing across multiple mobile viewports."
    },
    {
      company: "Microsoft Research Systems Division",
      role: "Research Intern",
      location: "Bangalore",
      startDate: "Jan",
      endDate: "Jun",
      description: "Researched compiler optimization techniques for low-power edge computers."
    }
  ],
  education: [
    {
      degree: "Master of Technology (M.Tech) in Computer Science & Artificial Intelligence",
      institution: "International Institute of Information Technology (IIIT)",
      location: "Hyderabad",
      gpa: "9.8",
      startDate: "2018",
      endDate: "2020",
      description: "Data Structures, Database Management Systems, Advanced Operating Systems, Distributed Consensus Protocols, Neural Networks."
    },
    {
      degree: "Bachelor of Technology (B.Tech)",
      institution: "St. Martin's Engineering College",
      location: "Secunderabad",
      gpa: "8.5",
      startDate: "2014",
      endDate: "2018",
      description: "Computer Science Engineering coursework: Data Structures, Design & Analysis of Algorithms, Software Engineering."
    }
  ],
  skills: [
    { "name": "React, Node.js, Express, Go, MongoDB, Redis, PostgreSQL, Docker, Kubernetes, AWS, WebRTC, TailwindCSS, Puppeteer, Selenium, Jenkins, Git", "progress": 95 }
  ],
  projects: [
    {
      title: "Artificial Intelligence Assisted Resume Builder with ATS Parser & Visual Validator",
      description: "Designed and built an enterprise-grade resume builder featuring automated ATS scoring, real-time visual alignment, and PDF export with no card boundaries leaking out.",
      github: "https://github.com/alexander/ai-resume-builder-ats-visual-validator-pipeline",
      liveDemo: "https://ai-resume-builder.subdomain.verbose-domain-name-with-extreme-length.com"
    }
  ],
  certifications: [
    {
      "title": "Amazon Web Services (AWS) Certified Solutions Architect – Professional",
      "issuer": "Amazon Web Services",
      "year": "March 2025",
      "certificateUrl": "https://aws.amazon.com/verification/cert/9876543210-professional-architect-verification-system"
    }
  ],
  languages: [
    { "name": "English, Telugu, Hindi, German", "progress": 90 }
  ],
  interests: [
    "Building open-source developer tooling",
    "Competitive programming & algorithms design"
  ],
  customSections: {
    publications: [
      {
        title: "A Decoupled High-Throughput Consensus Architecture for Distributed Document Management & Parsing",
        subtitle: "IEEE Journal of Artificial Intelligence Research & Systems",
        date: "January 2026",
        authors: "Dr. Alexander-Constantine von Hohenzollern-Sigmaringen-Braganza III, Dr. Alan Turing",
        abstract: "We present a decoupled architecture for parsing unstructured resume PDF documents and rendering them with pixel-perfect accuracy at runtime. Our system guarantees that no text strings or input cards exceed parent element boundaries, preventing horizontal scrolling completely.",
        paperUrl: "https://arxiv.org/abs/2606.12345-decoupled-high-throughput-consensus-architecture-document-parsing"
      }
    ],
    volunteering: [
      {
        title: "Lead Volunteer Coding Instructor & Curriculum Designer",
        subtitle: "Girls Who Code Hyderabad Chapter",
        date: "September 2024 - Present",
        description: "Taught programming principles (Python, JavaScript, Git) to over 150 high school students."
      }
    ]
  },
  decoratives: {
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
    
    console.log('5. Performing visual captures...');
    const viewports = [320, 360, 375, 390, 430, 768, 1024];
    for (const width of viewports) {
      console.log(`Auditing width: ${width}px`);
      await page.setViewport({ width, height: 1400 });
      await new Promise(resolve => setTimeout(resolve, 1500));
      
      const ssPath = path.join(screenshotsDir, `viewport_${width}_full.png`);
      await page.screenshot({ path: ssPath, fullPage: true });
      console.log(`Saved screenshot to ${ssPath}`);
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
      
      // Let's run pdf-generator.js on visual_audit_temp.html
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
