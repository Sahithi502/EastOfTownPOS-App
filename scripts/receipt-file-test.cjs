const {test}=require('node:test'),assert=require('node:assert/strict'),{createHash}=require('node:crypto');
const {validateReceipt}=require('../electron/receipt-file.cjs'),{testPdf,testPng}=require('./receipt-fixtures.cjs');
test('valid receipt bytes and MIME agree and produce a stable digest',()=>{
 for(const [format,bytes,mime] of [['pdf',testPdf(),'application/pdf'],['png',testPng(),'image/png']]){const result=validateReceipt(bytes,format,mime+'; charset=binary');assert.equal(result.bytes,bytes.length);assert.equal(result.mime,mime);assert.equal(result.sha256,createHash('sha256').update(bytes).digest('hex'));}
});
test('the exact 21-byte error, HTML, JSON and empty payloads cannot be receipts',()=>{
 for(const value of ['Internal Server Error','<html>Internal Server Error</html>','{"error":"Internal Server Error"}',''])for(const format of ['pdf','png'])assert.throws(()=>validateReceipt(Buffer.from(value),format),/Nothing was shared/);
});
test('mismatched MIME, renamed files and truncated documents are rejected',()=>{
 assert.throws(()=>validateReceipt(testPdf(),'pdf','text/plain'),/Nothing was shared/);assert.throws(()=>validateReceipt(testPng(),'pdf'),/Nothing was shared/);assert.throws(()=>validateReceipt(testPdf(),'png'),/Nothing was shared/);assert.throws(()=>validateReceipt(testPdf().subarray(0,-12),'pdf'),/Nothing was shared/);assert.throws(()=>validateReceipt(testPng().subarray(0,-12),'png'),/Nothing was shared/);
});
test('a PNG with corrupted image data or invalid chunk framing is rejected',()=>{
 const bad=testPng();bad[45]^=1;assert.throws(()=>validateReceipt(bad,'png'),/Nothing was shared/);const length=testPng();length.writeUInt32BE(0xffffffff,8);assert.throws(()=>validateReceipt(length,'png'),/Nothing was shared/);
});
