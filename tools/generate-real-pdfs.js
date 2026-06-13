#!/usr/bin/env node

/**
 * Generate accurate test PDFs with proper conditional watermark rendering
 * Mimics how ResumePreview.jsx conditionally renders watermark
 */

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

async function generatePDF(inputPath, outputPath, isFreePlan) {
  return new Promise((resolve, reject) => {
    const args = [
      path.join(__dirname, 'pdf-generator.js'),
      inputPath,
      outputPath,
      String(isFreePlan)
    ];

    const child = spawn('node', args, {
      cwd: __dirname,
      stdio: 'pipe'
    });

    let output = '';

    child.stdout.on('data', (data) => {
      output += data.toString();
    });

    child.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(`PDF generation failed`));
      } else {
        resolve({ output, success: true });
      }
    });
  });
}

async function createResumeHTML(isFreePlan) {
  const baseHtml = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Professional Resume</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', sans-serif;
            line-height: 1.6;
            color: #333;
            padding: 40px;
            background: white;
        }

        .header {
            text-align: center;
            margin-bottom: 30px;
        }

        .name {
            font-size: 28px;
            font-weight: 700;
            margin-bottom: 8px;
        }

        .contact-info {
            font-size: 12px;
            color: #666;
            margin-bottom: 20px;
        }

        .section {
            margin-bottom: 25px;
        }

        .section-title {
            font-size: 14px;
            font-weight: 700;
            text-transform: uppercase;
            border-bottom: 2px solid #333;
            padding-bottom: 8px;
            margin-bottom: 12px;
            letter-spacing: 1px;
        }

        .experience-item {
            margin-bottom: 15px;
        }

        .job-title {
            font-weight: 700;
            font-size: 13px;
        }

        .company {
            font-style: italic;
            color: #666;
            font-size: 12px;
        }

        .date {
            font-size: 11px;
            color: #999;
            margin-bottom: 6px;
        }

        .description {
            font-size: 12px;
            line-height: 1.5;
            margin-top: 6px;
        }

        ul {
            margin-left: 20px;
            font-size: 12px;
        }

        li {
            margin-bottom: 5px;
        }

        .skills {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            font-size: 12px;
        }

        .skill {
            background: #f0f0f0;
            padding: 4px 8px;
            border-radius: 3px;
        }

        @media print {
            body {
                padding: 20px;
            }
        }

        /* Watermark styling - identical to ResumePreview.css */
        .watermark-footer {
            position: sticky;
            bottom: 0;
            left: 0;
            right: 0;
            z-index: 50;
            text-align: center;
            font-family: 'Inter', 'Manrope', 'Plus Jakarta Sans', sans-serif;
            font-size: 8px;
            font-weight: 400;
            color: #999999;
            opacity: 0.35;
            letter-spacing: 0px;
            padding: 4px 0;
            background: transparent;
            pointer-events: none;
            user-select: none;
            width: 100%;
            margin-top: 0;
        }

        @media print {
            .watermark-footer {
                position: fixed !important;
                bottom: 8px !important;
                left: 0 !important;
                right: 0 !important;
                z-index: 9999 !important;
                width: 100% !important;
                page-break-inside: avoid !important;
                break-inside: avoid !important;
                display: block !important;
                visibility: visible !important;
                opacity: 0.35 !important;
            }
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="name">JOHN ALEXANDER SMITH</div>
        <div class="contact-info">
            San Francisco, CA 94105 | (555) 123-4567 | john.smith@email.com | linkedin.com/in/johnsmith
        </div>
    </div>

    <div class="section">
        <div class="section-title">Professional Summary</div>
        <p style="font-size: 12px; line-height: 1.6;">
            Experienced Senior Software Engineer with 8+ years of expertise in full-stack development, cloud architecture, and leading cross-functional teams. Proven track record of delivering high-impact projects at scale, optimizing system performance, and mentoring junior developers. Skilled in modern web technologies, microservices architecture, and DevOps practices.
        </p>
    </div>

    <div class="section">
        <div class="section-title">Professional Experience</div>

        <div class="experience-item">
            <div class="job-title">Senior Software Engineer</div>
            <div class="company">TechCorp Industries, San Francisco, CA</div>
            <div class="date">January 2022 - Present (4+ years)</div>
            <div class="description">
                <ul>
                    <li>Led development of microservices architecture serving 2M+ daily active users</li>
                    <li>Reduced API response time by 65% through optimization and caching strategies</li>
                    <li>Mentored team of 5 junior engineers, establishing coding standards and best practices</li>
                    <li>Implemented CI/CD pipeline, reducing deployment time from 2 hours to 15 minutes</li>
                    <li>Designed and deployed Kubernetes clusters supporting production workloads</li>
                </ul>
            </div>
        </div>

        <div class="experience-item">
            <div class="job-title">Software Engineer</div>
            <div class="company">DataSolutions Inc, San Francisco, CA</div>
            <div class="date">June 2018 - December 2021 (3+ years)</div>
            <div class="description">
                <ul>
                    <li>Developed React-based single-page applications with 500K+ monthly users</li>
                    <li>Built RESTful APIs supporting complex data analytics features</li>
                    <li>Collaborated with product and design teams to ship features on schedule</li>
                    <li>Improved database query performance, reducing load by 40%</li>
                </ul>
            </div>
        </div>

        <div class="experience-item">
            <div class="job-title">Junior Software Engineer</div>
            <div class="company">StartupHub Technologies, San Francisco, CA</div>
            <div class="date">June 2016 - May 2018 (2 years)</div>
            <div class="description">
                <ul>
                    <li>Built full-stack web applications using Node.js and React</li>
                    <li>Participated in code reviews and contributed to testing infrastructure</li>
                    <li>Assisted in system architecture decisions and technology evaluations</li>
                </ul>
            </div>
        </div>
    </div>

    <div class="section">
        <div class="section-title">Technical Skills</div>
        <div class="skills">
            <div class="skill">JavaScript</div>
            <div class="skill">TypeScript</div>
            <div class="skill">React</div>
            <div class="skill">Node.js</div>
            <div class="skill">Python</div>
            <div class="skill">PostgreSQL</div>
            <div class="skill">MongoDB</div>
            <div class="skill">AWS</div>
            <div class="skill">Docker</div>
            <div class="skill">Kubernetes</div>
            <div class="skill">Git</div>
            <div class="skill">GraphQL</div>
            <div class="skill">REST APIs</div>
            <div class="skill">System Design</div>
            <div class="skill">Agile/Scrum</div>
        </div>
    </div>

    <div class="section">
        <div class="section-title">Education</div>

        <div class="experience-item">
            <div class="job-title">Bachelor of Science in Computer Science</div>
            <div class="company">University of California, Berkeley</div>
            <div class="date">Graduated: May 2016</div>
            <div class="description">GPA: 3.8/4.0 | Dean's List all 4 years</div>
        </div>
    </div>

    <div class="section">
        <div class="section-title">Certifications & Awards</div>
        <ul style="font-size: 12px;">
            <li><strong>AWS Certified Solutions Architect</strong> - Amazon Web Services, 2021</li>
            <li><strong>Employee of the Year</strong> - TechCorp Industries, 2023</li>
            <li><strong>Best Technical Presentation</strong> - Tech Conference 2022</li>
            <li><strong>Google Cloud Certified Associate Cloud Engineer</strong> - Google Cloud, 2020</li>
        </ul>
    </div>

    ${isFreePlan ? '<div class="watermark-footer" aria-hidden="true">Made with VRESIQ</div>' : ''}
</body>
</html>`;

  return baseHtml;
}

async function main() {
  console.log('════════════════════════════════════════════════════════════════');
  console.log('GENERATING REAL PDFS WITH CONDITIONAL WATERMARK RENDERING');
  console.log('════════════════════════════════════════════════════════════════\n');

  const outputDir = path.join(__dirname, 'visual-verification');
  
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  try {
    // Generate Free User HTML and PDF
    console.log('📄 Generating FREE USER PDF...');
    const freeHtml = await createResumeHTML(true);
    const freeHtmlPath = path.join(outputDir, 'resume-free-user-temp.html');
    fs.writeFileSync(freeHtmlPath, freeHtml);
    
    const freePdfPath = path.join(outputDir, 'resume-free-user.pdf');
    await generatePDF(freeHtmlPath, freePdfPath, true);
    console.log('✓ Free user PDF generated');
    console.log(`  Path: ${freePdfPath}`);
    console.log(`  Expected: Watermark on every page\n`);

    // Generate Pro User HTML and PDF
    console.log('📄 Generating PRO USER PDF...');
    const proHtml = await createResumeHTML(false);
    const proHtmlPath = path.join(outputDir, 'resume-pro-user-temp.html');
    fs.writeFileSync(proHtmlPath, proHtml);
    
    const proPdfPath = path.join(outputDir, 'resume-pro-user.pdf');
    await generatePDF(proHtmlPath, proPdfPath, false);
    console.log('✓ Pro user PDF generated');
    console.log(`  Path: ${proPdfPath}`);
    console.log(`  Expected: NO watermark\n`);

    // Verify files
    const freeStat = fs.statSync(freePdfPath);
    const proStat = fs.statSync(proPdfPath);

    console.log('════════════════════════════════════════════════════════════════');
    console.log('FILE VERIFICATION');
    console.log('════════════════════════════════════════════════════════════════\n');

    console.log('✓ Free User PDF:');
    console.log(`  File: ${freePdfPath}`);
    console.log(`  Size: ${(freeStat.size / 1024).toFixed(2)} KB\n`);

    console.log('✓ Pro User PDF:');
    console.log(`  File: ${proPdfPath}`);
    console.log(`  Size: ${(proStat.size / 1024).toFixed(2)} KB\n`);

    // Verify watermark content
    console.log('════════════════════════════════════════════════════════════════');
    console.log('WATERMARK TEXT VERIFICATION');
    console.log('════════════════════════════════════════════════════════════════\n');

    try {
      const pdfParseDefault = await import('pdf-parse/lib/pdf-parse.js');
      const pdfParse = pdfParseDefault.default;

      // Free User PDF
      const freePdfBuffer = fs.readFileSync(freePdfPath);
      const freeData = await pdfParse(freePdfBuffer);
      const freeWatermarkCount = (freeData.text.match(/Made with VRESIQ/gi) || []).length;

      console.log('✓ FREE USER PDF:');
      console.log(`  Pages: ${freeData.numpages}`);
      console.log(`  Watermark occurrences: ${freeWatermarkCount}`);
      console.log(`  Expected: ${freeData.numpages}`);
      console.log(`  Status: ${freeWatermarkCount === freeData.numpages ? '✓ CORRECT' : '✗ MISMATCH'}\n`);

      // Pro User PDF
      const proPdfBuffer = fs.readFileSync(proPdfPath);
      const proData = await pdfParse(proPdfBuffer);
      const proWatermarkCount = (proData.text.match(/Made with VRESIQ/gi) || []).length;

      console.log('✓ PRO USER PDF:');
      console.log(`  Pages: ${proData.numpages}`);
      console.log(`  Watermark occurrences: ${proWatermarkCount}`);
      console.log(`  Expected: 0`);
      console.log(`  Status: ${proWatermarkCount === 0 ? '✓ CORRECT' : '✗ UNEXPECTED'}\n`);

    } catch (e) {
      console.log('⚠ Text extraction not available, but PDFs generated successfully\n');
    }

    // Cleanup temp files
    fs.unlinkSync(freeHtmlPath);
    fs.unlinkSync(proHtmlPath);

    console.log('════════════════════════════════════════════════════════════════');
    console.log('✓ PDFS READY FOR VISUAL INSPECTION');
    console.log('════════════════════════════════════════════════════════════════\n');

    console.log('📍 OPEN THESE FILES IN A PDF VIEWER:\n');
    console.log(`Free User:  ${freePdfPath}`);
    console.log(`Pro User:   ${proPdfPath}\n`);

    console.log('✓ INSTRUCTIONS:\n');
    console.log('1. Open Free User PDF');
    console.log('   → Scroll to bottom of page 1 → watermark should be visible');
    console.log('   → Go to page 2 → watermark should be visible\n');

    console.log('2. Open Pro User PDF');
    console.log('   → Scroll to bottom of page 1 → NO watermark');
    console.log('   → Go to page 2 → NO watermark\n');

    console.log('3. Compare:');
    console.log('   → Free PDF has "Made with VRESIQ" at bottom');
    console.log('   → Pro PDF has NO watermark\n');

    console.log('✓ Watermark characteristics (Free User PDF):');
    console.log('   → Font size: 8px (small, subtle)');
    console.log('   → Opacity: 35% (faint, not intrusive)');
    console.log('   → Color: Light gray (#999999)');
    console.log('   → Position: Bottom center of each page');
    console.log('   → Visibility: Visible without zooming\n');

  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
}

main();
