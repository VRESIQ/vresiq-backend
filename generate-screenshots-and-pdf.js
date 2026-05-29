const fs = require('fs');
const path = require('path');
const puppeteer = require('puppeteer');

const cssPath = 'c:\\Users\\ACER\\Desktop\\PROJECTS\\ResumeBuilder\\resume-builder-frontend\\src\\components\\ResumePreview.css';
const cssContent = fs.readFileSync(cssPath, 'utf8');

const htmlContent = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Matheus Kunzler Maldaner - Recreated Resume</title>
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Lora:ital,wght@0,400;0,500;0,600;0,700;1,400&display=swap">
  <style>
    /* Reset styles */
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

    #resume-preview {
      width: 816px !important;
      max-width: 816px !important;
      height: auto !important;
      min-height: 1056px !important;
      margin: 0 auto !important;
      box-sizing: border-box !important;
      box-shadow: none !important;
      border: none !important;
      background: #fff !important;
      color: #000000 !important;
      visibility: visible !important;
    }

    body * {
      visibility: visible !important;
    }
  </style>
  <style>
    ${cssContent}
  </style>
</head>
<body>
  <article id="resume-preview" class="resume-preview rp-academic_cv rp-high-density" style="--accent: #111111; --accent-readable: #111111; --rp-font-heading: 'Lora', Georgia, serif; --rp-font-body: 'Lora', Georgia, serif;">
    <header class="rp-academic-header">
      <h1 class="rp-academic-name">Matheus Kunzler Maldaner</h1>
      <p class="rp-academic-role"></p>
      <div class="rp-ats-contact">
        <span class="rp-ats-contact-item"><a href="https://matheusmaldaner.github.io" style="color: inherit; text-decoration: none;">matheusmaldaner.github.io</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="mailto:mkunzlermaldaner@ufl.edu" style="color: inherit; text-decoration: none;">mkunzlermaldaner@ufl.edu</a><span class="rp-ats-bullet"> | </span></span>
        <span class="rp-ats-contact-item"><a href="https://github.com/matheusmaldaner" style="color: inherit; text-decoration: none;">github.com/matheusmaldaner</a></span>
      </div>
    </header>
    
    <main class="rp-academic-body">
      <!-- Section 1: ACADEMIC -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Academic</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>UNIVERSITY OF FLORIDA</strong>
            <span class="rp-item-location">Gainesville, FL</span>
          </div>
          <div class="rp-item-sub">
            <span>B.S. Data Science, Minor in Computer Science, AI Certificate</span>
            <span>Aug 2021 - May 2026</span>
          </div>
          <p class="rp-education-gpa" style="margin: 2px 0 0; font-size: var(--rp-fs-meta); color: #111;">
            <strong>GPA: 3.9 / 4.0</strong>
          </p>
          <div class="rp-item-desc" style="margin-top: 4px;">
            <span class="rp-desc-text">Relevant Coursework: Neural Networks and Deep Learning, Fund of Machine Learning, Image Processing and Computer Vision, Data Structures, Regression Analysis, Linear Algebra, Discrete Structures, Probability, Statistics Theory, Bioinformatics, Computer Organization, Operating Systems, Database Systems, Calculus.</span>
          </div>
        </div>
      </section>
      
      <!-- Section 2: PROFESSIONAL EXPERIENCE -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Professional Experience</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>Researcher and Developer</strong>
            <span>May 2024 - Current</span>
          </div>
          <div class="rp-item-sub">
            <span>CARNEGIE MELLON UNIVERSITY</span>
            <span class="rp-item-location">Pittsburgh, PA</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Conducting research under CMU's Human-Computer Interaction Institute on auditing generative AI models.</li>
              <li>Created MIRAGE, a software to facilitate the comparison of different Text-To-Image AI models.</li>
              <li>Presented 5 research posters, first-author demo paper at AAAI HCOMP and currently working on a paper for CHI2025.</li>
            </ul>
          </div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>Researcher and Developer</strong>
            <span>Aug 2023 - Current</span>
          </div>
          <div class="rp-item-sub">
            <span>FLORIDA INSTITUTE FOR NATIONAL SECURITY</span>
            <span class="rp-item-location">Gainesville, FL</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Conducting research on Neurosymbolic AI, combining symbolic reasoning and neural networks for transparent AI models.</li>
              <li>Helped on a new framework for Explainable AI as a 2nd author which was submitted to Springer AI and is under review.</li>
              <li>Working on Differentiable Logic Gate Networks. Trained 260 models on HiPerGator. Presented 6 posters in the past year.</li>
            </ul>
          </div>
        </div>
      </section>
 
      <!-- Section 3: PROJECTS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Projects</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>MIRAGE</strong>
            <span>May 2024 - Current</span>
          </div>
          <div class="rp-item-sub">
            <span>Full Stack Developer and Researcher</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Developed MIRAGE*, a web tool that allows users to generate and compare outputs from multiple Text-To-Image Models</li>
              <li>Collaborating with researchers from UNISINOS in Brazil to create a modified version of the tool for their specific research.</li>
              <li>Built with Django, AWS (Lambda Functions, LightSail Container, DynamoDB, S3 Bucket), Replicate, and Docker.</li>
            </ul>
          </div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>ECOLOGIC</strong>
            <span></span>
          </div>
          <div class="rp-item-sub">
            <span>Developer and Researcher</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Trained Differentiable Logic Gate Networks and extracted learned gates for conversion into Verilog and VHDL.</li>
              <li>Synthesized gate-level netlists and deployed them onto a DE10-Lite FPGA.</li>
              <li>Measured inference speeds of 239µs, 78% faster than the 1087µs on the same model using NVIDIA A100 GPUs.</li>
              <li>Integrated the DAD3 Logic Analyzer for real-time disaster alerts based on FPGA outputs.</li>
            </ul>
          </div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>SALIENCYSLIDER</strong>
            <span>Feb 2024 - May 2024</span>
          </div>
          <div class="rp-item-sub">
            <span>Full Stack Developer and Researcher</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Web app where users can upload pictures and see how a CNN uses certain regions of the image for classification.</li>
              <li>Used Django and pythonanywhere to deploy the web application and GradCam implementation for interpretability.</li>
            </ul>
          </div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>ASA DATAFEST</strong>
            <span>Apr 2024</span>
          </div>
          <div class="rp-item-sub">
            <span>Data Analyst</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Applied t-SNE and k-means to visualize educational data clusters, winning Best Overall at ASA Datafest competition.</li>
              <li>Submitted our work to the Undergraduate Class Project Competition (USCLAP) as a paper, where we won 2nd Place.</li>
              <li>Gave a talk on our work at the electronic Undergraduate Statistics Research Conference 2024 (eUSR).</li>
            </ul>
          </div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>ADAPTREE: AI POWERED EDUCATION PLATFORM</strong>
            <span>Sep 2023</span>
          </div>
          <div class="rp-item-sub">
            <span>Full Stack Developer</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Django, React, and PostgreSQL web app with OpenAI's API that creates customized lessons based on the user's background.</li>
              <li>Secured finalist position at the 2023 UF AI Days Hackathon among 450+ participants.</li>
            </ul>
          </div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>MUS MUSCULUS NEURONAL NETWORK ANALYSIS</strong>
            <span>Aug 2023 - Dec 2023</span>
          </div>
          <div class="rp-item-sub">
            <span>Researcher and Developer</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Led Mus Musculus gene analysis using R, processing 30,000+ Ensembl IDs and visualizing data with topGO.</li>
              <li>Applied K-means to decode neuronal network patterns from 5,000+ genes.</li>
            </ul>
          </div>
        </div>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>BRAND LOGOS IMAGE CLASSIFICATION</strong>
            <span>Jun 2023 - Aug 2023</span>
          </div>
          <div class="rp-item-sub">
            <span>Developer</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Achieved 95% accuracy on MNIST dataset using EfficientNet and transfer learning on UF's HiPerGator supercomputer.</li>
              <li>Reduced overfitting by 20% for 10,000+ logos through dropout and data augmentation regularization techniques.</li>
            </ul>
          </div>
        </div>
      </section>
 
      <!-- Section 4: LEADERSHIP -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Leadership</h3>
        <div class="rp-item">
          <div class="rp-item-head">
            <strong>President (Prev. Workshop Director)</strong>
            <span>Aug 2023 - Current</span>
          </div>
          <div class="rp-item-sub">
            <span>DATA SCIENCE AND INFORMATICS INSTITUTE</span>
            <span class="rp-item-location">Gainesville, FL</span>
          </div>
          <div class="rp-item-desc">
            <ul class="rp-desc-list">
              <li>Overseeing an executive board of 31 students to ensure proper organization of the club's activities.</li>
              <li>Currently organizing an Nvidia Deep Learning workshop series with 450+ registered participants.</li>
              <li>Organized and received $4600 in support of taking students to compete in Florida's Largest Hackathon.</li>
              <li>Created a public GitHub repository, ensuring post-presentation access to workshop materials.</li>
            </ul>
          </div>
        </div>
        <div class="rp-item" style="margin-bottom: 6px;">
          <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
            <span>
              <strong>SIGNAL PROCESSING SOCIETY</strong>
              <span class="rp-compact-subtitle">: Vice President</span>
            </span>
            <span class="rp-compact-date">Gainesville, FL | Aug 2024 - Current</span>
          </div>
        </div>
        <div class="rp-item" style="margin-bottom: 6px;">
          <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
            <span>
              <strong>AI STUDENT ADVISORY COUNCIL</strong>
              <span class="rp-compact-subtitle">: Community Director</span>
            </span>
            <span class="rp-compact-date">Gainesville, FL | Aug 2024 - Current</span>
          </div>
        </div>
        <div class="rp-item" style="margin-bottom: 6px;">
          <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
            <span>
              <strong>AMERICAN STATISTICAL ASSOCIATION</strong>
              <span class="rp-compact-subtitle">: Vice President</span>
            </span>
            <span class="rp-compact-date">Gainesville, FL | Aug 2023 - May 2024</span>
          </div>
        </div>
      </section>
 
      <!-- Section 5: POSTER PRESENTATIONS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Poster Presentations</h3>
        <div class="rp-memberships-list">
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>FURC - "Neuro Symbolic AI: Bridging Neural Networks and Symbolic Reasoning for Enhanced AI Transparency"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>Sigma Xi - "Neuro Symbolic AI: Bridging Neural Networks and Symbolic Reasoning for Enhanced AI Transparency"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>University of Florida Spring Symposium - "Ethical Horizons in the field of Neuro Symbolic AI"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>SHPE Spring Symposium - "Ethical Horizons in the field of Neuro Symbolic AI"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>DSI Spring Symposium - "Uniting Symbolic and Neural Paradigms for Transparent AI"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>Carnegie Mellon - "MIRAGE: Multi-model Interface for Reviewing and Auditing GEnerative Text-to-Image AI"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>HCOMP - "MIRAGE: Multi-model Interface for Reviewing and Auditing GEnerative Text-to-Image AI"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>University of Florida Fall Symposium - "Side-by-Side Multi-Models T2I Comparisons Unveiling Problems in AI."</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>HiPerGator Symposium - "exPLogic: Explaining Logic Types and Patterns in DiffLogic Networks"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>UF AI Days Symposium - "MIRAGE: Multi-model Interface for Reviewing and Auditing GEnerative Text-to-Image AI"</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>McKnight Fellows Conference - "Side-by-Side Multi-Models T2I Comparisons Unveiling Problems in AI."</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>Warren B. Nelms IoT - "Accelerating Real-Time Inference with FPGA-Implemented Logic Gate Neural Networks."</span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
        </div>
      </section>
 
      <!-- Section 6: RESEARCH PAPERS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Research Papers</h3>
        <div class="rp-publications-list">
          <div class="rp-citation-item" style="display: flex; gap: 8px; marginBottom: 8px; pageBreakInside: avoid; breakInside: avoid;">
            <div class="rp-citation-num" style="font-weight: 600; minWidth: 18px;">1.</div>
            <div class="rp-citation-content" style="flex: 1;">
              <span class="rp-citation-prefix">[Submitted to Springer AI. Under Review] </span>
              <strong class="rp-citation-title">
                The QI-Framework: Basic Foundations for XAI <span style="font-weight: normal;">[*]</span>
              </strong>
              <div class="rp-citation-authors" style="marginTop: 2px; fontSize: var(--rp-fs-meta); color: #333;">
                Stephen Wormald, <strong>Matheus Kunzler Maldaner</strong>, Kristian O'Connor, Daniel Capecci, Olivia Dizon-Paradis, Damon Woodard.
              </div>
            </div>
          </div>
          <div class="rp-citation-item" style="display: flex; gap: 8px; marginBottom: 8px; pageBreakInside: avoid; breakInside: avoid;">
            <div class="rp-citation-num" style="font-weight: 600; minWidth: 18px;">2.</div>
            <div class="rp-citation-content" style="flex: 1;">
              <span class="rp-citation-prefix">[HCOMP] </span>
              <strong class="rp-citation-title">
                MIRAGE: Multi-model Interface for Reviewing and Auditing GEnerative Text-to-Image AI <span style="font-weight: normal;">[*]</span>
              </strong>
              <div class="rp-citation-authors" style="marginTop: 2px; fontSize: var(--rp-fs-meta); color: #333;">
                <strong>Matheus Kunzler Maldaner</strong>, Wesley Deng, Ken Holstein, Motahhare Eslami, Jason Hong.
              </div>
            </div>
          </div>
          <div class="rp-citation-item" style="display: flex; gap: 8px; marginBottom: 8px; pageBreakInside: avoid; breakInside: avoid;">
            <div class="rp-citation-num" style="font-weight: 600; minWidth: 18px;">3.</div>
            <div class="rp-citation-content" style="flex: 1;">
              <span class="rp-citation-prefix">[USCLAP] </span>
              <strong class="rp-citation-title">
                Optimizing Digital Learning Through Data Analytics and Natural Language Processing <span style="font-weight: normal;">[*]</span>
              </strong>
              <div class="rp-citation-authors" style="marginTop: 2px; fontSize: var(--rp-fs-meta); color: #333;">
                <strong>Matheus Kunzler Maldaner</strong>, Justin Witter, Patrick Lehman, Raul Valle, Eric Chao.
              </div>
            </div>
          </div>
          <div class="rp-citation-item" style="display: flex; gap: 8px; marginBottom: 8px; pageBreakInside: avoid; breakInside: avoid;">
            <div class="rp-citation-num" style="font-weight: 600; minWidth: 18px;">4.</div>
            <div class="rp-citation-content" style="flex: 1;">
              <span class="rp-citation-prefix">[Submitted to ITMG. Accepted] </span>
              <strong class="rp-citation-title">
                Global, Semi-Global, and Local Explanations Built Off the DiffLogic Architecture.
              </strong>
              <div class="rp-citation-authors" style="marginTop: 2px; fontSize: var(--rp-fs-meta); color: #333;">
                Stephen Wormald, David Koblah, <strong>Matheus Kunzler Maldaner</strong>, Dominic Forte, Damon Woodard.
              </div>
            </div>
          </div>
        </div>
      </section>
 
      <!-- Section 7: TECHNICAL SKILLS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Technical Skills</h3>
        <div class="rp-memberships-list">
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head">
              <span>
                <strong>Technologies: </strong>
                <span class="rp-compact-subtitle">C++, Java, Python, R, Rust, AHK, Qualtrics, ArcMap, Mastercam, R Studio, Matlab, Flutter, Django, Azure, AWS, JavaScript, HTML, CSS, React, Flask, Lambda functions, Replicate, Git, Docker.</span>
              </span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head">
              <span>
                <strong>Certifications & Training: </strong>
                <span class="rp-compact-subtitle">Microsoft Azure Fundamentals (AZ900), Microsoft AI Fundamentals (AI900), Microsoft Networking Fundamentals (M40366), Python Fundamentals (SS3520), Agile Methodology in Project Management (SS3398), Network Administration (SS3136), Deloitte Future of Work Institute, Nvidia Deep Learning Fundamentals.</span>
              </span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head">
              <span>
                <strong>Languages: </strong>
                <span class="rp-compact-subtitle">Portuguese (Fluent), English (Fluent), Spanish (Conversational).</span>
              </span>
            </div>
          </div>
        </div>
      </section>
 
      <!-- Section 8: HONORS AND AWARDS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Honors and Awards</h3>
        <div class="rp-awards-paragraph" style="line-height: var(--rp-lh-body); fontSize: var(--rp-fs-body)">
          <span class="rp-award-inline-item">
            <strong>UF GenAI Arts Competition 2nd Place Overall</strong> [2024] • 
            <strong>UF AI Days Hackathon Most Popular</strong> [2024] • 
            <strong>ShellHacks Hackathon Most Popular</strong> [2024] • 
            <strong>FGLSAMP Scholar</strong> [2024] • 
            <strong>USCLAP 2nd Place Overall</strong> [2024] • 
            <strong>Hispanic Scholarship Fund Scholar</strong> [2024] • 
            <strong>UF Joe Glover Data Science Scholarship</strong> [2024] • 
            <strong>UF AI Scholar</strong> [2024] • 
            <strong>UF University Scholar</strong> [2024] • 
            <strong>Apple Pathways Alliance Fellow</strong> [2024] • 
            <strong>ASA DataFest Best Overall</strong> [2024] • 
            <strong>OSC Hackathon Best AI Hack & 2nd Place</strong> [2024] • 
            <strong>William G. Cross Award Finalist</strong> [2024] • 
            <strong>FURC Travel Award</strong> [2024] • 
            <strong>Dean's Honor List</strong> [2021, 2022, 2023, 2024] • 
            <strong>President's Honor Roll</strong> [2023] • 
            <strong>UF AI Days Hackathon Finalist</strong> [2023] • 
            <strong>Bright Futures Program Scholar</strong> [2021, 2022, 2023, 2024]
          </span>
        </div>
      </section>
 
      <!-- Section 9: MEMBERSHIPS -->
      <section class="rp-section">
        <h3 class="rp-stitle" data-divider="line">Memberships</h3>
        <div class="rp-memberships-list">
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>
                <strong>IEEE</strong>
                <span class="rp-compact-subtitle">: Institute for Electrical and Electronics Engineers Member</span>
              </span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>
                <strong>AAAI</strong>
                <span class="rp-compact-subtitle">: Association for the Advancement of Artificial Intelligence Member</span>
              </span>
              <span class="rp-compact-date">2024</span>
            </div>
          </div>
          <div class="rp-compact-item" style="margin-bottom: 4px;">
            <div class="rp-compact-head" style="display: flex; justify-content: space-between; align-items: baseline;">
              <span>
                <strong>SHPE</strong>
                <span class="rp-compact-subtitle">: Society of Hispanic Professional Engineers Member</span>
              </span>
              <span class="rp-compact-date">2023-2024</span>
            </div>
          </div>
        </div>
      </section>
    </main>
  </article>
</body>
</html>
`;

(async () => {
  const artifactDir = 'C:\\Users\\ACER\\.gemini\\antigravity\\brain\\3f288bbf-53a3-4884-9b4d-c66b59fa50ef';
  const outPdf = path.join(artifactDir, 'matheus_maldaner_recreated.pdf');
  const outImgP1 = path.join(artifactDir, 'matheus_maldaner_recreated_p1.png');
  const outImgP2 = path.join(artifactDir, 'matheus_maldaner_recreated_p2.png');
  
  console.log('Launching browser...');
  const browser = await puppeteer.launch({
    headless: 'new',
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--font-render-hinting=none'
    ]
  });
  
  try {
    const page = await browser.newPage();
    
    // Width matched to Letter at 96 DPI
    await page.setViewport({
      width: 816,
      height: 1056 * 2, // Capture both pages
      deviceScaleFactor: 2 // High resolution rendering
    });
    
    await page.emulateMediaType('screen');
    await page.setContent(htmlContent, { waitUntil: 'networkidle0' });
    
    // Wait for fonts to load
    await page.evaluateHandle('document.fonts.ready');
    
    console.log('Generating PDF...');
    await page.pdf({
      path: outPdf,
      format: 'letter',
      printBackground: true
    });
    console.log(`PDF saved: ${outPdf}`);
    
    console.log('Capturing Screenshot Page 1...');
    await page.screenshot({
      path: outImgP1,
      clip: { x: 0, y: 0, width: 816, height: 1056 }
    });
    console.log(`Page 1 screenshot saved: ${outImgP1}`);
    
    console.log('Capturing Screenshot Page 2...');
    await page.screenshot({
      path: outImgP2,
      clip: { x: 0, y: 1056, width: 816, height: 1056 }
    });
    console.log(`Page 2 screenshot saved: ${outImgP2}`);
    
    console.log('Asset generation complete.');
  } catch (err) {
    console.error('Error during generation:', err);
  } finally {
    await browser.close();
  }
})();
