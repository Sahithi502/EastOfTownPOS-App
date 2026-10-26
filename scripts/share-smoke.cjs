const {spawnSync}=require('node:child_process'),path=require('node:path'),fs=require('node:fs'),os=require('node:os'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),directory=fs.mkdtempSync(path.join(os.tmpdir(),'eot-share-test-')),file=path.join(directory,'test-only-receipt.pdf');
// A minimal local PDF. This test never chooses a target or sends anything.
fs.writeFileSync(file,'%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 0/Kids[]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF');
const helper=path.join(root,'native','bin','ReceiptShare.exe'),mode=process.argv.includes('--show')?'--self-test':'--probe';
const result=spawnSync(helper,[mode,file],{encoding:'utf8',windowsHide:true,timeout:20000});assert.ifError(result.error);assert.equal(result.status,0,result.stderr);assert.match(result.stdout,mode==='--probe'?/attachment-ready/:/attachment-provided/);console.log('PASS: native Windows '+(mode==='--probe'?'attachment preparation':'Share UI and file request')+'. Nothing sent.');console.log('Isolated file: '+file);
