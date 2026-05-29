const fs = require('fs');
const path = require('path');

try {
  const pdfPath = path.join(__dirname, 'downloaded-from-app.pdf');
  const pdfBuffer = fs.readFileSync(pdfPath);
  const pdfText = pdfBuffer.toString('binary');

  console.log('Searching generated PDF for mailto: and tel: links...');
  
  // PDF URI actions look like: /URI (mailto:...) or similar
  const mailtoMatches = [...pdfText.matchAll(/\/URI\s*\((mailto:[^)]+)\)/g)];
  const telMatches = [...pdfText.matchAll(/\/URI\s*\((tel:[^)]+)\)/g)];
  
  console.log(`Found ${mailtoMatches.length} mailto: link(s):`);
  mailtoMatches.forEach(m => console.log(' -', m[1]));
  
  console.log(`Found ${telMatches.length} tel: link(s):`);
  telMatches.forEach(m => console.log(' -', m[1]));

} catch (err) {
  console.error('Error verifying PDF links:', err);
}
