/**
 * render-pdf-screenshot.js
 * Renders a PDF page as a screenshot using Puppeteer to visually inspect content
 */
const puppeteer = require('puppeteer');
const fs = require('fs');
const path = require('path');

async function screenshotFromPDF(pdfPath, outputPng) {
  const browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
  const page = await browser.newPage();
  
  // Set the viewport to match letter page dimensions
  await page.setViewport({ width: 816, height: 1056, deviceScaleFactor: 2 });
  
  const fileUrl = 'file:///' + pdfPath.replace(/\\/g, '/');
  await page.goto(fileUrl, { waitUntil: 'networkidle0' });
  await new Promise(r => setTimeout(r, 1000));
  
  await page.screenshot({ path: outputPng, fullPage: false });
  console.log('Saved', outputPng);
  
  await browser.close();
}

(async () => {
  const dir = __dirname;
  await screenshotFromPDF(
    path.join(dir, 'pdf-test-old.pdf'),
    path.join(dir, 'pdf-old-rendered.png')
  );
  await screenshotFromPDF(
    path.join(dir, 'pdf-test-fixed.pdf'),
    path.join(dir, 'pdf-fixed-rendered.png')
  );
})();
