const fs=require('node:fs'),path=require('node:path'),{randomUUID}=require('node:crypto');
const processes=new Set();
function cleanupShares(directory){
  if(!fs.existsSync(directory))return;
  for(const entry of fs.readdirSync(directory,{withFileTypes:true})){
    if(!entry.isFile()||!/^receipt-[A-Za-z0-9-]{1,50}-[a-f0-9-]{36}\.(pdf|png)$/.test(entry.name))continue;
    const file=path.join(directory,entry.name);
    try{if(Date.now()-fs.statSync(file).mtimeMs>7*24*60*60*1000)fs.unlinkSync(file);}catch{}
  }
}
async function shareReceipt({base,directory,invoiceId,invoiceNumber,format,bytes}){
  if(!/^[a-f0-9-]{36}$/.test(invoiceId)||!['pdf','png'].includes(format))throw Error('Invalid receipt sharing request');
  if(process.platform!=='win32')throw Error('Windows Share is available on Windows. Use Save and open WhatsApp.');
  const helper=path.join(base,'native','bin','ReceiptShare.exe');
  const executable=fs.existsSync(path.join(base,'native','ReceiptShare.exe'))?path.join(base,'native','ReceiptShare.exe'):helper;
  if(!fs.existsSync(executable))throw Error('Windows Share helper is missing. Build it with npm.cmd run build:share, or use Save and open WhatsApp.');
  if(processes.size)throw Error('Close the existing receipt sharing window before opening another.');
  fs.mkdirSync(directory,{recursive:true});cleanupShares(directory);
  if(!/^[A-Z0-9-]{1,40}$/.test(invoiceNumber))throw Error('Invalid invoice number for sharing');
  const file=path.join(directory,'receipt-'+invoiceNumber+'-'+randomUUID()+'.'+format);
  fs.writeFileSync(file,bytes,{flag:'wx'});
  return new Promise((resolve,reject)=>{
    const child=require('node:child_process').spawn(executable,['--share',file],{windowsHide:true,stdio:['ignore','pipe','pipe']});processes.add(child);
    let output='',settled=false,error='';
    const timer=setTimeout(()=>{settled=true;reject(Error('Windows Share did not become ready. Your invoice is saved. Use Save and open WhatsApp.'));child.kill();},30000);
    child.stdout.on('data',data=>{
      output+=data.toString();
      if(settled)return;
      if(output.includes('share-opened')&&output.includes('attachment-provided')){
        settled=true;clearTimeout(timer);resolve({state:'attachment-provided',message:'Windows Share opened with the receipt file. Choose WhatsApp if available, select the customer, and review and send there. Delivery is not confirmed.'});
      }else if(output.includes('share-retry-available')){
        // The native window remains open for a deliberate retry. This is not an
        // attachment/delivery confirmation and must never use the success text.
        settled=true;clearTimeout(timer);resolve({state:'awaiting-share',message:'The receipt sharing window is ready, but Windows has not requested the attachment yet. Switch to East of Town — Share receipt and click Open Windows Share. If it still does not open, close that window and use Save and open WhatsApp. Your invoice is saved.'});
      }
    });
    child.stderr.on('data',data=>{error=(error+data.toString()).slice(-1000);});
    child.on('error',()=>{clearTimeout(timer);processes.delete(child);reject(Error('Windows Share could not start. Windows security may require a trusted signed helper. Use Save and open WhatsApp.'));});
    child.on('exit',code=>{clearTimeout(timer);processes.delete(child);if(!settled)reject(Error('Windows Share could not open'+(error?' ('+error.trim()+')':'')+'. Use Save and open WhatsApp.'));});
  });
}
function stopShares(){for(const child of processes)child.kill();processes.clear();}
module.exports={shareReceipt,cleanupShares,stopShares};
