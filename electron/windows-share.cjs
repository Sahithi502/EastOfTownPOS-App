const fs=require('node:fs'),path=require('node:path'),{randomUUID}=require('node:crypto');
const processes=new Set();
let current=null,queue=Promise.resolve();
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
  const key=JSON.stringify([path.resolve(base),path.resolve(directory),invoiceId,format]);
  if(current&&!current.ended){
    if(current.key===key)return attempt(current);
    await closeSession(current);
  }
  fs.mkdirSync(directory,{recursive:true});cleanupShares(directory);
  const file=path.join(directory,'receipt-'+invoiceNumber+'-'+randomUUID()+'.'+format);
  fs.writeFileSync(file,bytes,{flag:'wx'});
  const child=require('node:child_process').spawn(executable,['--managed',file],{windowsHide:true,stdio:['pipe','pipe','pipe']});processes.add(child);
  let closed;const session={child,key,ready:false,ended:false,pending:null,closed:new Promise(resolve=>{closed=resolve;})};current=session;
  let output='',error='';
  const ended=()=>{session.ended=true;processes.delete(child);if(current===session)current=null;closed();};
  child.stdout.on('data',data=>{
    output+=data.toString();
    let end;
    while((end=output.indexOf('\n'))>=0){
      const line=output.slice(0,end).trim();output=output.slice(end+1);
      if(line==='share-window-ready'){session.ready=true;if(session.pending)requestOpen(session);continue;}
      const pending=session.pending;if(!pending)continue;
      if(line==='share-attempt-started'){pending.opened=false;pending.attached=false;}
      if(line==='share-opened')pending.opened=true;
      if(line==='attachment-provided')pending.attached=true;
      if(pending.opened&&pending.attached)finish(session,null,{state:'attachment-provided',message:'Windows Share opened with the receipt file. Choose WhatsApp if available, select the customer, and review and send there. Delivery is not confirmed.'});
      else if(line==='share-retry-available')finish(session,null,{state:'awaiting-share',message:'Windows has not requested the attachment yet. The receipt sharing window is available: click Open Windows Share there, or click Send via Windows Share again to return to it. Your invoice is saved. Use Save and open WhatsApp if Windows remains unavailable.'});
      else if(line==='share-cancelled')finish(session,null,{state:'cancelled',message:'Windows sharing was cancelled. Your invoice is saved; you can share it again.'});
      else if(line==='share-existing-active')finish(session,null,{state:'existing-share',message:'The existing Windows Share panel for this receipt was brought forward. Finish or cancel that sharing session there. Delivery is not confirmed.'});
      else if(line==='share-existing-pending')finish(session,null,{state:'awaiting-share',message:'Windows Share is already open for this receipt, but Windows did not bring it forward. Switch to its Share panel to finish or cancel. Your invoice is saved; delivery is not confirmed.'});
    }
  });
  child.stderr.on('data',data=>{error=(error+data.toString()).slice(-1000);});
  child.stdin.on('error',()=>{finish(session,Error('The receipt sharing window stopped responding. Try sharing again or use Save and open WhatsApp.'));child.kill();});
  child.on('error',()=>{ended();finish(session,Error('Windows Share could not start. Windows security may require a trusted signed helper. Use Save and open WhatsApp.'));});
  child.on('exit',()=>{ended();finish(session,Error('Windows Share could not open'+(error?' ('+error.trim()+')':'')+'. Use Save and open WhatsApp.'));});
  return attempt(session);
}
function stopShares(){for(const child of processes)child.kill();processes.clear();}
module.exports={shareReceipt,cleanupShares,stopShares};
