const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

// Configurations
const FRONTEND_URL = 'http://localhost:5173';
const BACKEND_URL = 'http://localhost:8081';
const ADMIN_EMAIL = 'admin@vresiq.com';
const ADMIN_PASSWORD = 'admin2026@vresiq2026';
const OUTPUT_DIR = path.join(__dirname, 'visual-verification');

if (!fs.existsSync(OUTPUT_DIR)) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

// PDF.js worker/library viewer HTML to render PDF to canvas in headless browser
const getPdfViewerHtml = (pdfBase64) => `
<!DOCTYPE html>
<html>
<head>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"></script>
  <style>
    body { margin: 0; padding: 0; background: #ffffff; }
    canvas { display: block; width: 816px; height: 1056px; }
  </style>
</head>
<body>
  <canvas id="pdf-canvas" width="816" height="1056"></canvas>
  <script>
    pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
    
    const base64toUint8Array = (base64) => {
      const binaryString = atob(base64);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      return bytes;
    };

    async function renderPdf() {
      try {
        const pdfData = base64toUint8Array("${pdfBase64}");
        const loadingTask = pdfjsLib.getDocument({ data: pdfData });
        const pdf = await loadingTask.promise;
        const page = await pdf.getPage(1);
        
        const canvas = document.getElementById('pdf-canvas');
        const context = canvas.getContext('2d');
        
        // 816px wide at standard DPI (scale for letter size)
        const viewport = page.getViewport({ scale: 816 / page.getViewport({ scale: 1.0 }).width });
        canvas.width = viewport.width;
        canvas.height = viewport.height;
        
        await page.render({
          canvasContext: context,
          viewport: viewport
        }).promise;
        
        document.body.setAttribute('data-rendered', 'true');
      } catch (err) {
        document.body.setAttribute('data-error', err.message);
      }
    }
    renderPdf();
  </script>
</body>
</html>
`;

// Default test resume structure
const getMockResumeData = (title, templateId, headerStyle, accentColor, targetRole) => ({
  title: title,
  template: templateId,
  fontPairing: 'inter',
  decoratives: {
    headerStyle: headerStyle || 'minimal',
    accentColor: accentColor || '#1a5fb4',
    dividerStyle: 'line',
    photoShape: 'circle',
    progressStyle: 'bar',
    bulletStyle: 'disc',
    linkStyle: 'standard'
  },
  profileInfo: {
    fullName: "Jane R. Smith",
    designation: "Senior Software Architect & Team Lead",
    summary: "Proven track record of designing high-throughput cloud architectures and mentoring engineering teams. Skilled in React, Node.js, and Java Spring Boot with a focus on web performance and semantic page structures.",
    targetRole: targetRole || "Principal Engineer"
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
  interests: ["Generative AI", "Aesthetics & Graphic Design"]
});

async function runAudit() {
  console.log('🚀 Launching visual audit verification...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  try {
    const page = await browser.newPage();
    await page.setViewport({ width: 1280, height: 900 });

    // 1. Authenticate
    console.log('🔐 Logging in as admin...');
    await page.goto(`${FRONTEND_URL}/login`, { waitUntil: 'networkidle2' });
    await page.type('input[type="email"]', ADMIN_EMAIL);
    await page.type('input[type="password"]', ADMIN_PASSWORD);
    await page.click('button[type="submit"]');
    await page.waitForNavigation({ waitUntil: 'networkidle2' });
    console.log('✓ Successfully logged in, reached dashboard.');

    // Get Auth Token
    const token = await page.evaluate(() => sessionStorage.getItem('token'));
    if (!token) throw new Error("Auth token not found in session storage!");

    // Create a temporary resume to edit
    console.log('📝 Creating test resume...');
    const createResponse = await page.evaluate(async (tok) => {
      const res = await fetch('/api/resumes', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + tok
        },
        body: JSON.stringify({ title: 'Visual Audit Resume', template: 'template1' })
      });
      return await res.json();
    }, token);

    const resumeId = createResponse._id;
    console.log(`✓ Test resume created with ID: ${resumeId}`);

    // Verification plan
    const templatesToTest = [
      // BUG 1: Card headers
      { id: 'consulting_bcg', name: 'Summit_Card', hstyle: 'card' },
      { id: 'tech_faang', name: 'Atlas_Card', hstyle: 'card' },
      { id: 'harvard_ats', name: 'Stone_Card', hstyle: 'card' },
      { id: 'swiss_minimal', name: 'Metro_Card', hstyle: 'card' },
      { id: 'ats_classic', name: 'Standard_Card', hstyle: 'card' },
      { id: 'ats_entry', name: 'Edge_Card', hstyle: 'card' },
      { id: 'ats_senior', name: 'Serif_Card', hstyle: 'card' },
      { id: 'ats_lead', name: 'Lead_Card', hstyle: 'card' },
      { id: 'ats_intern', name: 'Campus_Card', hstyle: 'card' },
      { id: 'ats_experienced', name: 'Prime_Card', hstyle: 'card' },
      { id: 'academic_cv', name: 'Scholar_Card', hstyle: 'card' },
      { id: 'engineer_ats', name: 'Frame_Card', hstyle: 'card' },

      // BUG 2 & 3: Full Bleed alignment & Frame download button
      { id: 'academic_cv', name: 'Scholar_FullBleed', hstyle: 'full-bleed' },
      { id: 'engineer_ats', name: 'Frame_FullBleed', hstyle: 'full-bleed' },

      // BUG 4: Target role badge text readability
      { id: 'template2', name: 'Nova_TargetRole', hstyle: 'full-bleed', accent: '#ffb703' }, // light yellow accent
      { id: 'premium8', name: 'Visual_TargetRole', hstyle: 'full-bleed', accent: '#ff007f' }, // bright pink accent
      { id: 'premium4', name: 'Signature_TargetRole', hstyle: 'full-bleed', accent: '#3a0ca3' }, // dark violet accent
      { id: 'premium5', name: 'Apex_TargetRole', hstyle: 'full-bleed', accent: '#00f5d4' }, // neon cyan accent
      { id: 'premium6', name: 'Split_TargetRole', hstyle: 'minimal', accent: '#ffb703' },
      { id: 'premium7', name: 'Block_TargetRole', hstyle: 'full-bleed', accent: '#7209b7' },
      { id: 'premium9', name: 'Centered_TargetRole', hstyle: 'full-bleed', accent: '#ffb703' },
      { id: 'premium10', name: 'Minimal_TargetRole', hstyle: 'full-bleed', accent: '#00f5d4' },
      { id: 'consulting_bcg', name: 'Summit_TargetRole', hstyle: 'full-bleed', accent: '#ffb703' }
    ];

    let previewCount = 0;
    let pdfCount = 0;

    for (const tc of templatesToTest) {
      console.log(`\n📷 Verifying [${tc.name}] (Template: ${tc.id}, Header Style: ${tc.hstyle})...`);
      
      // Update resume via API
      const updatedResume = getMockResumeData(tc.name, tc.id, tc.hstyle, tc.accent, "Lead Staff Architect");
      await page.evaluate(async (tok, id, data) => {
        await fetch(`/api/resumes/${id}`, {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + tok
          },
          body: JSON.stringify(data)
        });
      }, token, resumeId, updatedResume);

      // Reload/Navigate to edit page
      await page.goto(`${FRONTEND_URL}/resume/${resumeId}/edit`, { waitUntil: 'networkidle2' });
      await page.waitForSelector('#resume-preview', { timeout: 10000 });
      // Allow minor fonts / CSS resources to settle
      await page.waitForTimeout ? await page.waitForTimeout(1000) : await new Promise(r => setTimeout(r, 1000));

      // 1. Capture live preview screenshot
      const previewElement = await page.$('#resume-preview');
      const previewPngPath = path.join(OUTPUT_DIR, `${tc.name}_preview.png`);
      await previewElement.screenshot({ path: previewPngPath });
      console.log(`  - Preview screenshot saved: ${previewPngPath}`);
      previewCount++;

      // 2. Generate PDF via window.getPDFBlob() and save
      const pdfBase64 = await page.evaluate(async () => {
        const blob = await window.getPDFBlob();
        return new Promise((resolve) => {
          const reader = new FileReader();
          reader.onloadend = () => resolve(reader.result.split(',')[1]);
          reader.readAsDataURL(blob);
        });
      });

      const pdfPath = path.join(OUTPUT_DIR, `${tc.name}.pdf`);
      fs.writeFileSync(pdfPath, Buffer.from(pdfBase64, 'base64'));
      console.log(`  - Real PDF saved: ${pdfPath}`);

      // 3. Render PDF page 1 to PNG via headless PDF.js page
      const pdfPage = await browser.newPage();
      await pdfPage.setViewport({ width: 816, height: 1056 });
      
      const viewerHtml = getPdfViewerHtml(pdfBase64);
      await pdfPage.setContent(viewerHtml, { waitUntil: 'load' });
      
      // Wait for rendering confirmation
      await pdfPage.waitForSelector('body[data-rendered="true"]', { timeout: 10000 });
      
      const pdfPngPath = path.join(OUTPUT_DIR, `${tc.name}_pdf.png`);
      const canvasElement = await pdfPage.$('#pdf-canvas');
      await canvasElement.screenshot({ path: pdfPngPath });
      console.log(`  - PDF rendered page PNG saved: ${pdfPngPath}`);
      pdfCount++;

      await pdfPage.close();
    }

    // Cleanup test resume
    console.log('\n🧹 Cleaning up test resume...');
    await page.evaluate(async (tok, id) => {
      await fetch(`/api/resumes/${id}`, {
        method: 'DELETE',
        headers: {
          'Authorization': 'Bearer ' + tok
        }
      });
    }, token, resumeId);
    console.log('✓ Cleanup complete.');

    console.log('\n======================================================');
    console.log('VISUAL AUDIT COMPLETE');
    console.log(`Total Preview Screenshots generated: ${previewCount}`);
    console.log(`Total PDF Screenshots generated: ${previewCount}`);
    console.log(`Files saved in: ${OUTPUT_DIR}`);
    console.log('======================================================');

  } catch (error) {
    console.error('Audit failed with error:', error);
  } finally {
    await browser.close();
  }
}

runAudit();
