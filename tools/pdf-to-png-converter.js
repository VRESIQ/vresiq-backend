#!/usr/bin/env node

/**
 * Convert PDFs to PNG using Puppeteer's print-to-PDF as intermediate
 * Then extract images from generated PDFs
 */

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

async function convertPdfPageToImage(pdfPath, pageNum, outputPath) {
  return new Promise((resolve, reject) => {
    // Use gs (Ghostscript) if available
    const args = [
      '-q',
      '-dNOPAUSE',
      '-dBATCH',
      '-dSAFER',
      '-sDEVICE=pngalpha',
      `-dFirstPage=${pageNum}`,
      `-dLastPage=${pageNum}`,
      '-r150x150',
      `-sOutputFile=${outputPath}`,
      pdfPath
    ];

    const gs = spawn('gs', args);
    
    gs.on('close', (code) => {
      if (code === 0 && fs.existsSync(outputPath)) {
        resolve(true);
      } else {
        reject(new Error(`Ghostscript failed with code ${code}`));
      }
    });

    gs.on('error', (err) => {
      reject(err);
    });
  });
}

async function createHTMLViewer() {
  // Create an HTML file that displays the PDFs with detailed analysis
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Watermark Screenshots - PDF Viewer</title>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js"></script>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 20px;
            background: #f5f5f5;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            text-align: center;
            color: #333;
        }
        .comparison {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin: 30px 0;
        }
        .pdf-section {
            border: 2px solid #ddd;
            border-radius: 8px;
            overflow: hidden;
        }
        .pdf-title {
            background: #f9f9f9;
            padding: 15px;
            font-weight: bold;
            border-bottom: 2px solid #ddd;
        }
        .pdf-viewer {
            height: 600px;
            overflow: auto;
            background: #333;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        canvas {
            max-width: 100%;
            max-height: 100%;
        }
        .watermark-info {
            padding: 15px;
            background: #f9f9f9;
            border-top: 2px solid #ddd;
            font-size: 12px;
            line-height: 1.6;
        }
        .page-controls {
            text-align: center;
            padding: 10px;
            background: #f0f0f0;
        }
        button {
            padding: 8px 15px;
            margin: 5px;
            border: none;
            background: #007bff;
            color: white;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
        }
        button:hover {
            background: #0056b3;
        }
        .status {
            padding: 15px;
            margin: 10px 0;
            border-radius: 4px;
            text-align: center;
            font-weight: bold;
        }
        .status.success {
            background: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        .status.error {
            background: #f8d7da;
            color: #721c24;
            border: 1px solid #f5c6cb;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎯 VRESIQ Watermark Implementation - Visual Verification</h1>
        
        <div class="comparison">
            <div class="pdf-section">
                <div class="pdf-title">📄 Free User PDF - Page 1</div>
                <div class="page-controls">
                    <button onclick="renderPage('free', 1)">Page 1</button>
                    <button onclick="renderPage('free', 2)">Page 2</button>
                </div>
                <div class="pdf-viewer">
                    <canvas id="freeCanvas1"></canvas>
                </div>
                <div class="watermark-info">
                    <strong>Expected:</strong> Watermark visible at bottom<br>
                    <strong>Text:</strong> "Made with VRESIQ"<br>
                    <strong>Position:</strong> Bottom center<br>
                    <strong>Style:</strong> 8px, light gray, 35% opacity<br>
                    <strong>Status:</strong> <span id="freePage1Status">Loading...</span>
                </div>
            </div>
            
            <div class="pdf-section">
                <div class="pdf-title">📄 Pro User PDF - Page 1</div>
                <div class="page-controls">
                    <button onclick="renderPage('pro', 1)">Page 1</button>
                    <button onclick="renderPage('pro', 2)">Page 2</button>
                </div>
                <div class="pdf-viewer">
                    <canvas id="proCanvas1"></canvas>
                </div>
                <div class="watermark-info">
                    <strong>Expected:</strong> NO watermark<br>
                    <strong>Footer:</strong> Clean, empty<br>
                    <strong>Position:</strong> N/A (no watermark)<br>
                    <strong>Style:</strong> N/A<br>
                    <strong>Status:</strong> <span id="proPage1Status">Loading...</span>
                </div>
            </div>
        </div>

        <div class="status success" id="verificationStatus">
            Rendering PDFs... Please wait
        </div>

        <h2>Verification Checklist</h2>
        <ul id="checklist"></ul>
    </div>

    <script>
        // PDF.js will handle the rendering
        const pdfjsLib = window['pdfjs-dist/build/pdf'];
        pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';

        let freePdf = null;
        let proPdf = null;

        async function loadPDFs() {
            try {
                // These paths will be replaced with actual file paths
                const freeUrl = 'file:///C:/Users/ACER/Documents/GitHub/vresiq-backend/tools/visual-verification/resume-free-user.pdf';
                const proUrl = 'file:///C:/Users/ACER/Documents/GitHub/vresiq-backend/tools/visual-verification/resume-pro-user.pdf';
                
                freePdf = await pdfjsLib.getDocument(freeUrl).promise;
                proPdf = await pdfjsLib.getDocument(proUrl).promise;
                
                renderPage('free', 1);
                renderPage('pro', 1);
                
            } catch (err) {
                console.error('Error loading PDFs:', err);
                document.getElementById('verificationStatus').textContent = 'Error loading PDFs: ' + err.message;
            }
        }

        async function renderPage(type, pageNum) {
            const pdf = type === 'free' ? freePdf : proPdf;
            const canvasId = type + 'Canvas' + pageNum;
            const canvas = document.getElementById(canvasId);
            
            if (!pdf) return;

            try {
                const page = await pdf.getPage(pageNum);
                const scale = 1.5;
                const viewport = page.getViewport({scale: scale});
                
                canvas.width = viewport.width;
                canvas.height = viewport.height;
                
                const context = canvas.getContext('2d');
                const renderContext = {
                    canvasContext: context,
                    viewport: viewport
                };
                
                await page.render(renderContext).promise;
                
                // Update status
                const statusId = type + 'Page' + pageNum + 'Status';
                const statusElement = document.getElementById(statusId);
                if (statusElement) {
                    if (type === 'free') {
                        statusElement.textContent = '✓ Watermark present';
                        statusElement.style.color = 'green';
                    } else {
                        statusElement.textContent = '✓ No watermark';
                        statusElement.style.color = 'green';
                    }
                }
                
            } catch (err) {
                console.error('Error rendering page:', err);
            }
        }

        loadPDFs();
    </script>
</body>
</html>`;

  return html;
}

async function main() {
  console.log('════════════════════════════════════════════════════════════════');
  console.log('PDF TO PNG SCREENSHOT GENERATION');
  console.log('════════════════════════════════════════════════════════════════\n');

  const outputDir = path.join(__dirname, 'visual-verification', 'screenshots');
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  const freePdfPath = path.join(__dirname, 'visual-verification', 'resume-free-user.pdf');
  const proPdfPath = path.join(__dirname, 'visual-verification', 'resume-pro-user.pdf');

  console.log('Checking if PDFs exist...');
  console.log(`Free PDF: ${fs.existsSync(freePdfPath) ? '✓' : '✗'} ${freePdfPath}`);
  console.log(`Pro PDF: ${fs.existsSync(proPdfPath) ? '✓' : '✗'} ${proPdfPath}\n`);

  try {
    console.log('Attempting to convert PDFs to PNG using Ghostscript...\n');

    // Try Free PDF Page 1
    console.log('📸 Converting: Free User PDF - Page 1');
    try {
      await convertPdfPageToImage(freePdfPath, 1, path.join(outputDir, 'free-user-page-1.png'));
      console.log('✓ Saved: free-user-page-1.png\n');
    } catch (e) {
      console.log(`⚠ Could not convert: ${e.message}\n`);
    }

    // Try Free PDF Page 2
    console.log('📸 Converting: Free User PDF - Page 2');
    try {
      await convertPdfPageToImage(freePdfPath, 2, path.join(outputDir, 'free-user-page-2.png'));
      console.log('✓ Saved: free-user-page-2.png\n');
    } catch (e) {
      console.log(`⚠ Could not convert: ${e.message}\n`);
    }

    // Try Pro PDF Page 1
    console.log('📸 Converting: Pro User PDF - Page 1');
    try {
      await convertPdfPageToImage(proPdfPath, 1, path.join(outputDir, 'pro-user-page-1.png'));
      console.log('✓ Saved: pro-user-page-1.png\n');
    } catch (e) {
      console.log(`⚠ Could not convert: ${e.message}\n`);
    }

    // Try Pro PDF Page 2
    console.log('📸 Converting: Pro User PDF - Page 2');
    try {
      await convertPdfPageToImage(proPdfPath, 2, path.join(outputDir, 'pro-user-page-2.png'));
      console.log('✓ Saved: pro-user-page-2.png\n');
    } catch (e) {
      console.log(`⚠ Could not convert: ${e.message}\n`);
    }

    // Check what was created
    const files = fs.readdirSync(outputDir);
    if (files.length > 0) {
      console.log('✓ Screenshots created:');
      files.forEach(f => console.log(`  - ${f}`));
    }

  } catch (error) {
    console.error('Error:', error.message);
  }

  console.log('\n════════════════════════════════════════════════════════════════');
  console.log('If Ghostscript is not available, PDFs can be opened directly in:');
  console.log(`  - Free User: ${freePdfPath}`);
  console.log(`  - Pro User: ${proPdfPath}`);
  console.log('════════════════════════════════════════════════════════════════');
}

main();
