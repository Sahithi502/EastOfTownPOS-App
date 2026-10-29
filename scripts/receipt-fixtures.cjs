function testPdf(){
 const text='BT /F1 12 Tf 20 200 Td (TEST ONLY East of Town receipt) Tj ET';
 const objects=['<</Type/Catalog/Pages 2 0 R>>','<</Type/Pages/Count 1/Kids[3 0 R]>>','<</Type/Page/Parent 2 0 R/MediaBox[0 0 226 260]/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>','<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>','<</Length '+text.length+'>>\nstream\n'+text+'\nendstream'];
 let pdf='%PDF-1.4\n',offsets=[0];for(let i=0;i<objects.length;i++){offsets.push(Buffer.byteLength(pdf));pdf+=(i+1)+' 0 obj\n'+objects[i]+'\nendobj\n';}
 const xref=Buffer.byteLength(pdf);pdf+='xref\n0 '+offsets.length+'\n0000000000 65535 f \n'+offsets.slice(1).map(n=>String(n).padStart(10,'0')+' 00000 n \n').join('')+'trailer<</Size '+offsets.length+'/Root 1 0 R>>\nstartxref\n'+xref+'\n%%EOF\n';return Buffer.from(pdf);
}
function testPng(){return Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=','base64');}
module.exports={testPdf,testPng};
