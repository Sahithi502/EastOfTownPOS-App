const {test}=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),cp=require('node:child_process'),{randomUUID}=require('node:crypto');
const share=require('../electron/windows-share.cjs'),original=cp.spawn;
const base=fs.mkdtempSync(path.join(os.tmpdir(),'eot-share-bridge-'));fs.mkdirSync(path.join(base,'native','bin'),{recursive:true});fs.writeFileSync(path.join(base,'native','bin','ReceiptShare.exe'),'TEST ONLY placeholder; never executed');
const request=()=>({base,directory:path.join(base,'shared-receipts'),invoiceId:randomUUID(),invoiceNumber:'TEST-20261026-000001',format:'pdf',bytes:Buffer.from('%PDF-TEST ONLY')});
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function child(){
 const result=new EventEmitter();result.stdout=new EventEmitter();result.stderr=new EventEmitter();result.stdin=new EventEmitter();result.commands=[];
 result.stdin.write=(command,callback)=>{result.commands.push(command.trim());if(command==='close\n')result.emit('exit',0);if(callback)callback();return true;};
 result.kill=()=>result.emit('exit',null);return result;
}
function emit(fake,lines){fake.stdout.emit('data',Buffer.from(lines));}
function attached(fake){emit(fake,'share-attempt-started\nshare-opened\nattachment-provided\n');}
async function fixture(run){try{await run();}finally{share.stopShares();cp.spawn=original;await tick();}}

test('opening waits for the managed window and real file request',()=>fixture(async()=>{
 const fake=child();let args;cp.spawn=(_exe,input)=>{args=input;return fake;};let completed=false;
 const promise=share.shareReceipt(request()).then(value=>{completed=true;return value;});await tick();
 assert.equal(args[0],'--managed');assert.equal(fs.readFileSync(args[1]).toString(),'%PDF-TEST ONLY');assert.deepEqual(fake.commands,[]);
 emit(fake,'share-window-ready\nshare-attempt-started\nshare-opened\n');await tick();assert.equal(completed,false);assert.deepEqual(fake.commands,['open']);
 emit(fake,'attachment-provided\n');assert.equal((await promise).state,'attachment-provided');fake.emit('exit',0);
}));

test('closing without a file request never claims success',()=>fixture(async()=>{
 const fake=child();cp.spawn=()=>fake;const promise=share.shareReceipt(request());await tick();emit(fake,'share-window-ready\nshare-opened\n');fake.emit('exit',0);await assert.rejects(promise,/could not open/);
}));

test('a second click resumes the same receipt instead of rejecting or duplicating it',()=>fixture(async()=>{
 const fake=child(),input=request();let spawned=0;cp.spawn=()=>{spawned++;return fake;};
 const first=share.shareReceipt(input);await tick();emit(fake,'share-window-ready\nshare-opened\nshare-retry-');emit(fake,'available\n');assert.equal((await first).state,'awaiting-share');
 const second=share.shareReceipt(input);await tick();assert.equal(spawned,1);assert.deepEqual(fake.commands,['open','open']);attached(fake);assert.equal((await second).state,'attachment-provided');fake.emit('exit',0);
}));

test('the existing modal share panel can be brought forward without claiming another attachment',()=>fixture(async()=>{
 const fake=child(),input=request();cp.spawn=()=>fake;const first=share.shareReceipt(input);await tick();emit(fake,'share-window-ready\n');attached(fake);await first;
 const second=share.shareReceipt(input);await tick();emit(fake,'share-existing-active\n');const result=await second;assert.equal(result.state,'existing-share');assert.match(result.message,/brought forward/);assert.doesNotMatch(result.message,/opened with the receipt file/);fake.emit('exit',0);
}));

test('switching invoice or format closes the previous source before spawning another',()=>fixture(async()=>{
 const children=[];cp.spawn=()=>{const fake=child();children.push(fake);return fake;};let input=request();
 let promise=share.shareReceipt(input);await tick();emit(children[0],'share-window-ready\nshare-retry-available\n');await promise;
 for(const change of [{format:'png'},{invoiceId:randomUUID(),invoiceNumber:'TEST-20261026-000002'}]){
  const old=children.at(-1);input={...input,...change};promise=share.shareReceipt(input);await tick();assert.deepEqual(old.commands,['open','close']);assert.equal(children.length,change.format?2:3);
  emit(children.at(-1),'share-window-ready\n');attached(children.at(-1));await promise;
 }
 children.at(-1).emit('exit',0);
}));

test('cancellation releases the session and permits the next sharing attempt',()=>fixture(async()=>{
 const children=[];cp.spawn=()=>{const fake=child();children.push(fake);return fake;};const input=request();let promise=share.shareReceipt(input);await tick();emit(children[0],'share-window-ready\nshare-cancelled\n');assert.equal((await promise).state,'cancelled');children[0].emit('exit',0);
 promise=share.shareReceipt(input);await tick();assert.equal(children.length,2);emit(children[1],'share-window-ready\n');attached(children[1]);await promise;children[1].emit('exit',0);
}));

test('concurrent clicks are serialized and never create overlapping helpers',()=>fixture(async()=>{
 const fake=child(),input=request();let spawned=0;cp.spawn=()=>{spawned++;return fake;};const first=share.shareReceipt(input),second=share.shareReceipt(input);await tick();assert.equal(spawned,1);emit(fake,'share-window-ready\nshare-retry-available\n');await first;await tick();assert.deepEqual(fake.commands,['open','open']);attached(fake);await second;assert.equal(spawned,1);fake.emit('exit',0);
}));

test('a late exit from the old source does not clear the newer source',()=>fixture(async()=>{
 const children=[];cp.spawn=()=>{const fake=child();children.push(fake);return fake;};const old=request(),next=request();let promise=share.shareReceipt(old);await tick();emit(children[0],'share-window-ready\nshare-retry-available\n');await promise;
 promise=share.shareReceipt(next);await tick();emit(children[1],'share-window-ready\n');attached(children[1]);await promise;children[0].emit('exit',0);
 promise=share.shareReceipt(next);await tick();assert.equal(children.length,2);attached(children[1]);await promise;children[1].emit('exit',0);
}));

test('policy and broken-pipe failures keep the manual fallback and allow retry',()=>fixture(async()=>{
 const children=[];cp.spawn=()=>{const fake=child();children.push(fake);return fake;};let promise=share.shareReceipt(request());await tick();children[0].emit('error',Error('TEST ONLY policy denied'));await assert.rejects(promise,/trusted signed helper.*Save and open WhatsApp/);
 promise=share.shareReceipt(request());await tick();children[1].stdin.emit('error',Error('TEST ONLY broken pipe'));await assert.rejects(promise,/stopped responding.*Save and open WhatsApp/);
}));

test('untrusted file type and invoice filename cannot close a valid sharing session',()=>fixture(async()=>{
 const fake=child();cp.spawn=()=>fake;const promise=share.shareReceipt(request());await tick();emit(fake,'share-window-ready\nshare-retry-available\n');await promise;
 await assert.rejects(share.shareReceipt({...request(),format:'exe'}),/Invalid receipt/);await assert.rejects(share.shareReceipt({...request(),invoiceNumber:'../outside'}),/Invalid invoice number/);assert.deepEqual(fake.commands,['open']);fake.emit('exit',0);
}));

test('replacement waits for a delayed native exit',()=>fixture(async()=>{
 const children=[];cp.spawn=()=>{const fake=child();children.push(fake);return fake;};let promise=share.shareReceipt(request());await tick();emit(children[0],'share-window-ready\nshare-retry-available\n');await promise;
 children[0].stdin.write=()=>true;promise=share.shareReceipt(request());await tick();assert.equal(children.length,1);children[0].emit('exit',0);await tick();assert.equal(children.length,2);emit(children[1],'share-window-ready\n');attached(children[1]);await promise;children[1].emit('exit',0);
}));

test('a stuck source is not forgotten and cannot overlap a new source',()=>fixture(async()=>{
 const fake=child();let spawned=0;cp.spawn=()=>{spawned++;return fake;};let promise=share.shareReceipt(request());await tick();emit(fake,'share-window-ready\nshare-retry-available\n');await promise;
 fake.stdin.write=()=>true;fake.kill=()=>false;await assert.rejects(share.shareReceipt(request()),/previous sharing window could not close/);assert.equal(spawned,1);fake.emit('exit',0);
}));

test('resuming a previously attached source waits for a new file request',()=>fixture(async()=>{
 const fake=child(),input=request();cp.spawn=()=>fake;let promise=share.shareReceipt(input);await tick();emit(fake,'share-window-ready\n');attached(fake);await promise;
 let completed=false;promise=share.shareReceipt(input).then(result=>{completed=true;return result;});await tick();emit(fake,'share-attempt-started\nshare-opened\n');await tick();assert.equal(completed,false);emit(fake,'attachment-provided\n');await promise;fake.emit('exit',0);
}));
