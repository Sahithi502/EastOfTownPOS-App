const assert=require('node:assert/strict'),cp=require('node:child_process'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{randomUUID}=require('node:crypto');
const root=path.resolve(__dirname,'..'),helper=path.join(root,'native','bin','ReceiptShare.exe');
function receipt(png=false){
 const result=cp.execFileSync(process.execPath,[path.join(__dirname,'share-smoke.cjs'),...(png?['--png']:[])],{encoding:'utf8',windowsHide:true});
 const file=result.match(/^Isolated file: (.+)$/m)?.[1]?.trim();assert.ok(file,'Generated receipt fixture path');return file;
}
function closeManaged(file,eof){
 return new Promise((resolve,reject)=>{
  const child=cp.spawn(helper,['--managed',file],{windowsHide:true,stdio:['pipe','pipe','pipe']});let output='',error='',ready=false;
  const timer=setTimeout(()=>{child.kill();reject(Error('Managed native source did not close'));},15000);
  child.stdout.on('data',data=>{output+=data;if(!ready&&output.includes('share-window-ready')){ready=true;if(eof)child.stdin.end();else child.stdin.write('close\n');}});
  child.stderr.on('data',data=>{error+=data;});child.stdin.on('error',()=>{});
  child.on('error',failure=>{clearTimeout(timer);reject(failure);});
  child.on('exit',code=>{clearTimeout(timer);try{assert.equal(code,0,error);assert.equal(ready,true);assert.doesNotMatch(output,/share-opened|attachment-provided/);resolve();}catch(failure){reject(failure);}});
 });
}
(async()=>{
 const pdf=receipt(),png=receipt(true);
 await closeManaged(pdf,false);await closeManaged(png,true);
 console.log('PASS: real managed native helper readiness, graceful close and parent-pipe EOF; no chooser opened.');
 if(!process.argv.includes('--show'))return;
 // Optional interactive source test. It never selects a target or presses Send.
 const share=require('../electron/windows-share.cjs'),original=cp.spawn,children=[],markers=new Set();
 cp.spawn=(...args)=>{
  if(children.length)assert.ok(children.at(-1).exitCode!==null||children.at(-1).signalCode!==null,'Previous source exited before replacement');
  const child=original(...args);children.push(child);let output='';child.stdout.on('data',data=>{output+=data;let end;while((end=output.indexOf('\n'))>=0){markers.add(output.slice(0,end).trim());output=output.slice(end+1);}});return child;
 };
 try{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'eot-share-session-'));
  const input={base:root,directory,invoiceId:randomUUID(),invoiceNumber:'TEST-ONLY-SHARE',format:'pdf',bytes:fs.readFileSync(pdf)};
  for(const request of [input,input,{...input,format:'png',bytes:fs.readFileSync(png)}]){
   const result=await share.shareReceipt(request);assert.ok(['attachment-provided','awaiting-share','existing-share'].includes(result.state));console.log('Native source result: '+result.state);
  }
  assert.equal(children.length,2,'Repeated same-receipt click must reuse its source');
  console.log('PASS: real native PDF retry reused one helper, then PNG replaced it after exit. Nothing sent.');
  console.log('Observed native signals: '+[...markers].sort().join(', '));
 }finally{
  share.stopShares();cp.spawn=original;
  await Promise.all(children.map(child=>child.exitCode!==null||child.signalCode!==null?Promise.resolve():new Promise(resolve=>{const timer=setTimeout(resolve,5000);child.once('exit',()=>{clearTimeout(timer);resolve();});})));
 }
})().catch(error=>{console.error(error);process.exitCode=1;});
