const fs = require('fs');
const path = require('path');

try {
  const reportPath = path.join(__dirname, 'pdf-audit-report.json');
  if (!fs.existsSync(reportPath)) {
    console.log('Report JSON does not exist yet (runner is still executing).');
    process.exit(0);
  }
  const report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
  console.log('Overall Status:', report.passed ? 'PASS' : 'FAIL');
  console.log('Failures count:', report.failures);
  console.log('Total Split Words:', report.splitWordCount);
  console.log('Total Missing Chars:', report.missingCharacterCount);
  console.log('Total Missing Links:', report.missingLinkCount);
  
  if (report.affectedList && report.affectedList.length > 0) {
    console.log('\nSample regression detail (first 3):');
    console.log(JSON.stringify(report.affectedList.slice(0, 3), null, 2));
  }
} catch (err) {
  console.error(err);
}
