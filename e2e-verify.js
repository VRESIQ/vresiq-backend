const fs = require('fs');
const path = require('path');
const pdfParse = require('pdf-parse');

const cssPath = 'c:\\Users\\ACER\\Desktop\\PROJECTS\\ResumeBuilder\\resume-builder-frontend\\src\\components\\ResumePreview.css';
const cssContent = fs.readFileSync(cssPath, 'utf8');

const htmlContent = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Resume</title>
  <style>
    \${cssContent}
  </style>
  <style>
    html, body {
      background: #ffffff !important;
      color: #000000 !important;
      margin: 0 !important;
      padding: 0 !important;
    }
    
    @page {
      size: letter;
      margin-top: 40px !important;
      margin-bottom: 40px !important;
      margin-left: 0 !important;
      margin-right: 0 !important;
    }
    
    @page :first {
      margin-top: 0 !important;
    }

    #resume-preview, .resume-preview {
      position: static !important;
      display: block !important;
      width: 816px !important;
      max-width: 816px !important;
      height: auto !important;
      min-height: 1056px !important;
      margin: 0 auto !important;
      padding: 0 !important;
      box-shadow: none !important;
      border: none !important;
      overflow: visible !important;
      visibility: visible !important;
      box-sizing: border-box !important;
    }

    body * {
      visibility: visible !important;
    }

    /* Global PDF Text Integrity Fixes */
    html, body, #resume-preview, #resume-preview * {
      font-variant-ligatures: none !important;
      font-feature-settings: "liga" 0, "clig" 0, "calt" 0 !important;
      text-rendering: auto !important;
      letter-spacing: 0 !important;
      word-spacing: 0 !important;
      transition: none !important;
      transform: none !important;
    }

    /* Ensure specific template class overrides do not reinject letter-spacing */
    .rp-ats-name,
    .rp-ats-name-serif,
    .rp-sidebar-name,
    .rp-executive-name,
    .rp-sig-name,
    .rp-centered-name,
    .rp-role,
    .rp-executive-role,
    .rp-sig-role,
    .rp-centered-role,
    .rp-stitle {
      letter-spacing: 0 !important;
      word-spacing: 0 !important;
    }
  </style>
</head>
<body>
  <article id="resume-preview" class="resume-preview rp-ats_lead" data-template="ats_lead" data-lstyle="standard">
    <header class="rp-ats-header-left">
      <h1 class="rp-ats-name">Rithik</h1>
      <p class="rp-ats-role-left">VFX SUPERVISOR</p>
      <div class="rp-ats-contact">
        <span class="rp-ats-contact-item">
          <a href="mailto:rithik.mettu@gmail.com">rithik.mettu@gmail.com</a>
          <span class="rp-ats-bullet"> | </span>
        </span>
        <span class="rp-ats-contact-item">
          <a href="tel:+911234567890">+91-1234 567 890</a>
          <span class="rp-ats-bullet"> | </span>
        </span>
        <span class="rp-ats-contact-item">Hyd, ind<span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item">
          <a href="https://linkedin.com/in/qwert">qwert</a>
          <span class="rp-ats-bullet"> | </span>
        </span>
        <span class="rp-ats-contact-item">
          <a href="https://github.com/asdf">asdf</a>
          <span class="rp-ats-bullet"> | </span>
        </span>
        <span class="rp-ats-contact-item">
          <a href="https://zxcv">zxcv</a>
        </span>
      </div>
    </header>
    <main class="rp-ats-body">
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">SKILLS</h3>
        <ul class="rp-desc-list">
          <li><strong>Java</strong></li>
          <li><strong>Video editing</strong></li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">PROJECTS</h3>
        <div class="rp-item">
          <strong>Twitter edits</strong>
          <div class="rp-item-desc">
            <span class="rp-desc-text">edits in X</span>
          </div>
          <p class="rp-links" style="margin-top: 2px;">
            <a href="https://1234567890">1234567890</a>
            <span class="rp-links-divider"> · </span>
            <a href="https://zxcvbnm">zxcvbnm</a>
          </p>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">EDUCATION</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>SMU</strong>
          </div>
          <div class="rp-item-sub">
            <span>Btech</span>
          </div>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">CERTIFICATIONS</h3>
        <ul class="rp-desc-list">
          <li><strong>X telugu editor</strong></li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">LANGUAGES</h3>
        <ul class="rp-desc-list">
          <li><strong>Telugu</strong></li>
          <li><strong>German</strong></li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">INTERESTS</h3>
        <ul class="rp-desc-list">
          <li>Hello</li>
          <li>Hi</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">ACHIEVEMENTS</h3>
        <ul class="rp-desc-list">
          <li>1234567890qwertyuiop!@#%&*() (!@#%&*()1234590qwertyuiop) [WERTYUiERTy2345678]</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">PUBLICATIONS</h3>
        <ul class="rp-desc-list">
          <li>[12345!@#%QWER] !@#%&*QWERT1234 (qwer1234)</li>
          <li>!@#QWE123</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">VOLUNTEERING</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>QWE1234</strong>
            <span>Present</span>
          </div>
          <div class="rp-item-sub">
            <span>QWER123423we</span>
            <span>X, 2026</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>QWE!@#4er</li>
              <li>qwe234</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">LEADERSHIP</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>ASDFwe1234 WE23sde</strong>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>WER2345@#ersd</li>
              <li>WER2345@#we</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">HACKATHONS</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>XyzErt234@#%erc ASDFGwer5345</strong>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>!@#QWER@#4</li>
              <li>SDFGwERT2#%23</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">OPEN SOURCE CONTRIBUTIONS</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>!@#$%#$%erty456 DFdfgert</strong>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>SDFGeRT#%%456</li>
              <li>XCVBDfg#T#5y</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">AWARDS</h3>
        <ul class="rp-desc-list">
          <li>EReR3%DFGCv (DFDFGEr#%) [CVBFCGdrfG4rT4]</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">INTERNSHIPS</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>DFdefEr34fcvb DREERXERCt2t3616</strong>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>CFGDRF#ER#%drcfgfvgb1616r</li>
              <li>CFGDER#E%45</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">WORKSHOPS</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>123567890 4567rtcv</strong>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>#@#34545rdvvb</li>
              <li>DFRG%ERg2345</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">COURSEWORK</h3>
        <ul class="rp-desc-list">
          <li>123890: #%erTfgvcb 12345qwert</li>
          <li>!@#%1234qwer</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">EXTRACURRICULAR ACTIVITIES</h3>
        <ul class="rp-desc-list">
          <li>@#%1234QWE: 45qwe 1234wer</li>
          <li>!@#%23456wert</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">TECHNICAL PROFILES</h3>
        <ul class="rp-desc-list">
          <li>!@#%23456qwer: 2345qwer !@#$%2345wer</li>
          <li>@#%2345wer</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">PATENTS</h3>
        <ul class="rp-desc-list">
          <li>[@#%234sefg] !@#41234qwer (2345qwer)</li>
          <li>@#%2345qwert</li>
        </ul>
      </section>

      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">RESEARCH EXPERIENCE</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>1236890</strong>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>!@#1235qwer</li>
              <li>!@#123qwe</li>
              <li>12345qwer</li>
            </ul>
          </div>
        </div>
      </section>
    </main>
  </article>
</body>
</html>
`;

(async () => {
  try {
    console.log('1. Logging in to running backend...');
    const loginRes = await fetch('http://localhost:8081/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: 'admin@vresiq.com',
        password: 'admin2026@vresiq2026'
      })
    });
    
    if (!loginRes.ok) {
      throw new Error(`Login failed: ${loginRes.status} ${loginRes.statusText}`);
    }
    
    const loginData = await loginRes.json();
    const token = loginData.token;
    console.log('Login successful. JWT token received.');
    
    console.log('2. Requesting PDF generation from running backend export-pdf endpoint...');
    const exportRes = await fetch('http://localhost:8081/api/resumes/e2e-test-resume/export-pdf', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ htmlContent })
    });
    
    if (!exportRes.ok) {
      const errText = await exportRes.text();
      throw new Error(`PDF generation failed: ${exportRes.status} ${exportRes.statusText}\n${errText}`);
    }
    
    const pdfBuffer = Buffer.from(await exportRes.arrayBuffer());
    const outPdf = path.join(__dirname, 'real-app-export.pdf');
    fs.writeFileSync(outPdf, pdfBuffer);
    console.log(`PDF successfully exported from running backend and saved to: ${outPdf}`);
    
    console.log('3. Extracting text from the exported PDF...');
    const data = await pdfParse(pdfBuffer);
    
    console.log('=== Extracted Text ===');
    console.log(data.text);
    console.log('======================');
    
    const txtPath = path.join(__dirname, 'real-app-extracted-text.txt');
    fs.writeFileSync(txtPath, data.text);
    console.log(`Extracted text saved to: ${txtPath}`);
    
    // Check assertions
    console.log('4. Running verification assertions...');
    const textLines = data.text.split('\\n');
    
    const nameMatch = data.text.includes('Rithik');
    console.log(`- Verify name "Rithik" remains unsplit: ${nameMatch ? 'PASS' : 'FAIL'}`);
    
    const twitterMatch = data.text.includes('Twitter');
    console.log(`- Verify "Twitter" remains unsplit: ${twitterMatch ? 'PASS' : 'FAIL'}`);
    
    // Word splitting check: check that there are no split names or key headings
    const rithikSplit = data.text.includes('Rit ik');
    console.log(`- Verify NO "Rit ik" split: ${!rithikSplit ? 'PASS' : 'FAIL'}`);
    
    const twitterSplit = data.text.includes('Twit ter');
    console.log(`- Verify NO "Twit ter" split: ${!twitterSplit ? 'PASS' : 'FAIL'}`);
    
    if (nameMatch && twitterMatch && !rithikSplit && !twitterSplit) {
      console.log('ALL VERIFICATIONS PASSED SUCCESSFULLY!');
    } else {
      console.error('SOME VERIFICATIONS FAILED!');
    }
  } catch (err) {
    console.error('Error:', err);
  }
})();
