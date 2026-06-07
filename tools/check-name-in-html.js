const fs = require('fs');
const path = require('path');

const htmlPath = path.join(__dirname, 'debug-editor-export.html');
const htmlContent = fs.readFileSync(htmlPath, 'utf8');

const regex = /<h1[^>]*class="[^"]*rp-ats-name[^"]*"[^>]*>([\s\S]*?)<\/h1>/gi;
const match = regex.exec(htmlContent);

if (match) {
  console.log('--- Name element match ---');
  console.log('Full tag match:', match[0]);
  console.log('Inner HTML:', match[1]);
  console.log('Char codes of Inner HTML:', Array.from(match[1]).map(c => c.charCodeAt(0)));
} else {
  console.log('No match found for rp-ats-name h1 tag');
}
