const {spawnSync}=require('node:child_process'),path=require('node:path'),fs=require('node:fs'),os=require('node:os'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),directory=fs.mkdtempSync(path.join(os.tmpdir(),'eot-share-test-')),file=path.join(directory,'test-only-receipt.pdf');
// A valid one-page local PDF. This test never chooses a target or sends anything.
const text='BT /F1 12 Tf 20 200 Td (TEST ONLY East of Town receipt) Tj ET';
const objects=['<</Type/Catalog/Pages 2 0 R>>','<</Type/Pages/Count 1/Kids[3 0 R]>>','<</Type/Page/Parent 2 0 R/MediaBox[0 0 226 260]/Resources<</Font<</F1 4 0 R>>>>/Contents 5 0 R>>','<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>','<</Length '+text.length+'>>\nstream\n'+text+'\nendstream'];
let pdf='%PDF-1.4\n',offsets=[0];for(let i=0;i<objects.length;i++){offsets.push(Buffer.byteLength(pdf));pdf+=(i+1)+' 0 obj\n'+objects[i]+'\nendobj\n';}const xref=Buffer.byteLength(pdf);pdf+='xref\n0 '+offsets.length+'\n0000000000 65535 f \n'+offsets.slice(1).map(n=>String(n).padStart(10,'0')+' 00000 n \n').join('')+'trailer<</Size '+offsets.length+'/Root 1 0 R>>\nstartxref\n'+xref+'\n%%EOF\n';fs.writeFileSync(file,pdf);
const png=process.argv.includes('--png'),attachment=png?path.join(directory,'test-only-receipt.png'):file;
if(png){
 const image=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=','base64');
 // Verify the fixture itself: a file request alone cannot detect corrupt pixels.
 for(let p=8;p<image.length;){const n=image.readUInt32BE(p);let crc=0xffffffff;for(const byte of image.subarray(p+4,p+8+n)){crc^=byte;for(let bit=0;bit<8;bit++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}assert.equal((crc^0xffffffff)>>>0,image.readUInt32BE(p+8+n),'PNG fixture chunk checksum');if(image.toString('ascii',p+4,p+8)==='IDAT')assert.equal(require('node:zlib').inflateSync(image.subarray(p+8,p+8+n)).length,3);p+=n+12;}
 fs.writeFileSync(attachment,image);
}
const helper=path.join(root,'native','bin','ReceiptShare.exe'),mode=process.argv.includes('--show')?'--self-test':'--probe';
const result=spawnSync(helper,[mode,attachment],{encoding:'utf8',windowsHide:true,timeout:40000});assert.ifError(result.error);assert.equal(result.status,0,result.stderr);assert.match(result.stdout,mode==='--probe'?/attachment-ready/:/attachment-provided/);console.log('PASS: native Windows '+(mode==='--probe'?'attachment preparation':'Share UI and file request')+' ('+(png?'PNG':'PDF')+'). Nothing sent.');console.log('Isolated file: '+attachment);
