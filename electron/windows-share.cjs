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
    let output='',opened=false,error='';
    const timer=setTimeout(()=>{child.kill();reject(Error('Windows Share did not open. Your invoice is saved. Use Save and open WhatsApp.'));},20000);
    child.stdout.on('data',data=>{output+=data.toString();if(!opened&&output.includes('share-opened')){opened=true;clearTimeout(timer);resolve({message:'Windows Share opened with the receipt file. Choose WhatsApp if available, select the customer, and review and send there. Delivery is not confirmed.'});}});
    child.stderr.on('data',data=>{error=(error+data.toString()).slice(-1000);});
    child.on('error',()=>{clearTimeout(timer);processes.delete(child);reject(Error('Windows Share could not start. Windows security may require a trusted signed helper. Use Save and open WhatsApp.'));});
    child.on('exit',code=>{clearTimeout(timer);processes.delete(child);if(!opened)reject(Error('Windows Share could not open'+(error?' ('+error.trim()+')':'')+'. Use Save and open WhatsApp.'));});
  });
}
function stopShares(){for(const child of processes)child.kill();processes.clear();}
module.exports={shareReceipt,cleanupShares,stopShares};
