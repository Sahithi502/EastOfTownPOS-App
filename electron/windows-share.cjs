const fs=require('node:fs'),path=require('node:path'),{randomUUID}=require('node:crypto');
const {validateReceipt}=require('./receipt-file.cjs');
const processes=new Set();
let current=null,queue=Promise.resolve();
function trace(session,event){
  // Technical evidence only: no invoice number, filename, customer, phone or bytes.
  try{
    const log=path.join(session.directory,'sharing.log');
    if(fs.existsSync(log)&&fs.statSync(log).size>256*1024){fs.copyFileSync(log,log+'.previous');fs.writeFileSync(log,'');}
    fs.appendFileSync(log,JSON.stringify({time:new Date().toISOString(),session:session.id,event,format:session.info.format,bytes:session.info.bytes})+'\n');
  }catch{}
}
function cleanupShares(directory){
  if(!fs.existsSync(directory))return;
  for(const entry of fs.readdirSync(directory,{withFileTypes:true})){
    if(!entry.isFile()||!/^receipt-[A-Za-z0-9-]{1,50}-[a-f0-9-]{36}\.(pdf|png)$/.test(entry.name))continue;
    const file=path.join(directory,entry.name);
    try{if(Date.now()-fs.statSync(file).mtimeMs>7*24*60*60*1000)fs.unlinkSync(file);}catch{}
  }
}
function shareReceipt(input){
  // Serialize opening/replacing helpers, including callers that click together.
  const result=queue.then(()=>openReceipt(input));
  queue=result.catch(()=>{});
  return result;
}
async function waitForExit(session,ms){
  if(session.ended)return true;
  return new Promise(resolve=>{
    const timer=setTimeout(()=>resolve(false),ms);
    session.closed.then(()=>{clearTimeout(timer);resolve(true);});
  });
}
async function closeSession(session){
  if(session.ended)return;
  try{session.child.stdin.write('close\n');}catch{session.child.kill();}
  if(await waitForExit(session,2000))return;
  session.child.kill();
  if(!await waitForExit(session,1000))throw Error('The previous sharing window could not close. Restart the application, or use Save and open WhatsApp.');
}
function finish(session,error,result){
  const pending=session.pending;if(!pending)return;
  session.pending=null;clearTimeout(pending.timer);
  if(error)pending.reject(error);else pending.resolve(result);
}
function startupError(){
  return Error('Windows could not start the receipt-sharing helper. Windows security may require a trusted signed helper. Your invoice is saved. Use Save receipt PDF, then manually attach the file in WhatsApp.');
}
function requestOpen(session){
  try{
    session.child.stdin.write('open\n',error=>{
      if(error){finish(session,Error('The receipt sharing window stopped responding. Try sharing again or use Save and open WhatsApp.'));session.child.kill();}
    });
  }catch{
    finish(session,Error('The receipt sharing window stopped responding. Try sharing again or use Save and open WhatsApp.'));session.child.kill();
  }
}
function attempt(session){
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{
      finish(session,Error('Windows Share did not become ready. Your invoice is saved. Use Save and open WhatsApp.'));
      session.child.kill();
    },30000);
    session.pending={resolve,reject,timer,opened:false,attached:false};
    if(session.ready)requestOpen(session);
  });
}
async function openReceipt({base,directory,invoiceId,invoiceNumber,format,bytes}){
  if(!/^[a-f0-9-]{36}$/.test(invoiceId)||!['pdf','png'].includes(format))throw Error('Invalid receipt sharing request');
  if(process.platform!=='win32')throw Error('Windows Share is available on Windows. Use Save and open WhatsApp.');
  const helper=path.join(base,'native','bin','ReceiptShare.exe');
  const executable=fs.existsSync(path.join(base,'native','ReceiptShare.exe'))?path.join(base,'native','ReceiptShare.exe'):helper;
  if(!fs.existsSync(executable))throw Error('Windows Share helper is missing. Build it with npm.cmd run build:share, or use Save and open WhatsApp.');
  if(!/^[A-Z0-9-]{1,40}$/.test(invoiceNumber))throw Error('Invalid invoice number for sharing');
  const info=validateReceipt(bytes,format);
  const key=JSON.stringify([path.resolve(base),path.resolve(directory),invoiceId,format]);
  if(current&&!current.ended){
    if(current.key===key)return attempt(current);
    await closeSession(current);
  }
  fs.mkdirSync(directory,{recursive:true});cleanupShares(directory);
  const file=path.join(directory,'receipt-'+invoiceNumber+'-'+randomUUID()+'.'+format);
  const descriptor=fs.openSync(file,'wx');
  try{fs.writeFileSync(descriptor,bytes);fs.fsyncSync(descriptor);}finally{fs.closeSync(descriptor);}
  let child;
  try{child=require('node:child_process').spawn(executable,['--managed',file],{windowsHide:true,stdio:['pipe','pipe','pipe']});}
  catch{trace({directory,info,id:randomUUID()},'source-start-failed');throw startupError();}
  processes.add(child);
  let closed;const session={child,key,id:randomUUID(),directory,info,ready:false,ended:false,pending:null,closed:new Promise(resolve=>{closed=resolve;})};current=session;
  trace(session,'source-started');
  let output='',error='';
  const ended=()=>{session.ended=true;processes.delete(child);if(current===session)current=null;closed();};
  child.stdout.on('data',data=>{
    output+=data.toString();
    let end;
    while((end=output.indexOf('\n'))>=0){
      const line=output.slice(0,end).trim();output=output.slice(end+1);
      if(['share-window-ready','share-attempt-started','share-opened','attachment-provided','share-retry-available','share-cancelled','share-completed','share-existing-active','share-existing-pending','target-chosen','source-retained','attachment-failed','stream-materialized'].includes(line))trace(session,line);
      if(/^attachment-type:(application\/pdf|image\/png|application\/octet-stream|other)$/.test(line))trace(session,line);
      const verified=line.match(/^attachment-verified:(pdf|png):(\d+):([a-f0-9]{64})$/);
      if(verified){
        if(verified[1]!==info.format||Number(verified[2])!==info.bytes||verified[3]!==info.sha256){trace(session,'verification-mismatch');finish(session,Error('Receipt attachment verification failed. Nothing was shared; use Save receipt PDF.'));child.kill();return;}
        trace(session,'attachment-verified');continue;
      }
      if(line==='share-window-ready'){session.ready=true;if(session.pending)requestOpen(session);continue;}
      const pending=session.pending;if(!pending)continue;
      if(line==='attachment-failed'){finish(session,Error('Windows could not prepare the receipt attachment. Your invoice is saved. Use Save receipt PDF.'));continue;}
      if(line==='share-attempt-started'){pending.opened=false;pending.attached=false;}
      if(line==='share-opened')pending.opened=true;
      if(line==='attachment-provided')pending.attached=true;
      if(pending.opened&&pending.attached)finish(session,null,{state:'attachment-provided',message:'Receipt supplied to Windows Share. Keep its sharing window open until the correct PDF/PNG preview appears in WhatsApp, then review and send there. Delivery is not confirmed.'});
      else if(line==='share-retry-available')finish(session,null,{state:'awaiting-share',message:'Windows has not requested the attachment yet. The receipt sharing window is available: click Open Windows Share there, or click Send via Windows Share again to return to it. Your invoice is saved. Use Save and open WhatsApp if Windows remains unavailable.'});
      else if(line==='share-cancelled')finish(session,null,{state:'cancelled',message:'Windows sharing was cancelled. Your invoice is saved; you can share it again.'});
      else if(line==='share-existing-active')finish(session,null,{state:'existing-share',message:'The existing Windows Share panel for this receipt was brought forward. Finish or cancel that sharing session there. Delivery is not confirmed.'});
      else if(line==='share-existing-pending')finish(session,null,{state:'awaiting-share',message:'Windows Share is already open for this receipt, but Windows did not bring it forward. Switch to its Share panel to finish or cancel. Your invoice is saved; delivery is not confirmed.'});
    }
  });
  child.stderr.on('data',data=>{error=(error+data.toString()).slice(-1000);});
  child.stdin.on('error',()=>{finish(session,Error('The receipt sharing window stopped responding. Try sharing again or use Save and open WhatsApp.'));child.kill();});
  child.on('error',()=>{trace(session,'source-start-failed');ended();finish(session,startupError());});
  child.on('exit',()=>{trace(session,'source-exited');ended();finish(session,Error('Windows Share could not open'+(error?' ('+error.trim()+')':'')+'. Use Save and open WhatsApp.'));});
  return attempt(session);
}
function stopShares(){for(const child of processes)child.kill();processes.clear();}
module.exports={shareReceipt,cleanupShares,stopShares};
