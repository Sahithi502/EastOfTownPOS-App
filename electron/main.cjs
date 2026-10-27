const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const { spawn } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const path = require('node:path');
const fs = require('node:fs');
const windowsShare = require('./windows-share.cjs');
const net = require('node:net');
let backend, window;
let shuttingDown=false;
let userSession='';
// Keep the same business data folder across source, packaged and upgraded releases.
app.setPath('userData',path.join(app.getPath('appData'),'east-of-town-pos'));
const acceptance=process.argv.find(a=>a.startsWith('--acceptance-profile='));
if(acceptance){const selected=path.resolve(acceptance.slice('--acceptance-profile='.length));if(!/^eot-acceptance-[a-f0-9-]{36}$/.test(path.basename(selected)))throw Error('Invalid acceptance profile');app.setPath('userData',selected);}
if (!app.isPackaged && process.env.EOT_TEST_PROFILE) app.setPath('userData', path.resolve(process.env.EOT_TEST_PROFILE));
const token = randomBytes(32).toString('hex');
let port;
if (!app.requestSingleInstanceLock()) app.quit();
else {
app.on('second-instance', () => { if(window) { window.restore(); window.focus(); } });
app.whenReady().then(async () => {
  try {
    port = await new Promise((resolve,reject) => { const s=net.createServer(); s.on('error',reject); s.listen(0,'127.0.0.1',()=>{const p=s.address().port;s.close(()=>resolve(p));}); });
    const base = app.isPackaged ? process.resourcesPath : app.getAppPath();
    const jar = app.isPackaged ? path.join(base,'backend','pos.jar') : path.join(base,'backend','target','pos-0.6.0.jar');
    const bundledJava=path.join(base,'runtime','bin','java.exe');
    const java = app.isPackaged || fs.existsSync(bundledJava) ? bundledJava : process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME,'bin','java.exe') : 'java';
    if(!fs.existsSync(path.join(app.getAppPath(),'dist','index.html'))) throw new Error('Build the frontend first: npm.cmd run build, then npm.cmd start');
    if(!fs.existsSync(jar)) throw new Error('Build the backend first: cd backend, then mvn package');
    const data = path.join(app.getPath('userData'),'data');
    fs.mkdirSync(data,{recursive:true});
    const log=fs.openSync(path.join(data,'backend.log'),'a');
    backend=spawn(java,['-jar',jar],{windowsHide:true,stdio:['ignore',log,log],env:{...process.env,EOT_PORT:String(port),EOT_API_TOKEN:token,EOT_DATA_DIR:data}});
    let failure;
    backend.on('error',e=>{failure=e;});
    backend.on('exit',()=>{if(shuttingDown)return;failure=new Error('The local database service stopped. Restart the application.'); if(window) dialog.showErrorBox('Database service stopped',failure.message);});
    let ready=false;
    for(let i=0;i<120;i++) {
      if(failure) throw failure;
      try {const r=await fetch(`http://127.0.0.1:${port}/api/health`,{headers:{'X-EOT-Token':token},signal:AbortSignal.timeout(1000)});if(r.ok){ready=true;break;}} catch{}
      await new Promise(r=>setTimeout(r,500));
    }
    if(!ready) throw new Error('Database startup timed out. Check backend.log in the application data folder.');
    windowsShare.cleanupShares(path.join(app.getPath('userData'),'shared-receipts'));
    window=new BrowserWindow({width:1440,height:960,minWidth:1000,minHeight:720,backgroundColor:'#f4f3ee',webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true}});
    window.webContents.setWindowOpenHandler(()=>({action:'deny'}));
    window.webContents.on('will-navigate',event=>event.preventDefault());
    const caller=event=>{if(event.sender!==window.webContents || event.senderFrame!==window.webContents.mainFrame)throw new Error('Unauthorized caller');};
    const api=async(method,route,body,binary=false)=>{
      const form=body instanceof FormData;
      const response=await fetch(`http://127.0.0.1:${port}/api${route}`,{method,headers:{...(form?{}:{'Content-Type':'application/json'}),'X-EOT-Token':token,'X-EOT-Session':userSession},body:body===undefined?undefined:form?body:JSON.stringify(body),signal:AbortSignal.timeout(120000)});
      if(!response.ok){let result;try{result=await response.json();}catch{}throw new Error(result?.message || 'Request failed. Check the local service log.');}
      return binary?Buffer.from(await response.arrayBuffer()):response.json();
    };
    ipcMain.handle('catalog:request',async(event,method,route,body)=>{
      caller(event);
      const allowed=(method==='GET' && /^\/(health|categories|variants(?:\?search=[^#]*)?|auth\/status|imports\/history|stock\/movements\/[a-f0-9-]{36}|labels\/(printers|settings)|billing\/invoices(?:\/[a-f0-9-]{36})?)$/.test(route)) || (method==='POST' && (/^\/auth\/(setup|login|logout|users|recover|recovery-code)$/.test(route) || ['/categories','/variants','/stock/adjustments','/labels/preview','/labels/print','/labels/settings','/billing/checkout'].includes(route) || /^\/imports\/[a-f0-9-]{36}\/(preview|commit)$/.test(route))) || (method==='PUT' && /^\/variants\/[a-f0-9-]{36}$/.test(route));
      const billingAllowed=(method==='GET' && /^\/billing\/(settings|taxes|customers(?:\?search=[^#]*)?|invoices(?:\?[^#]*)?|invoices\/[a-f0-9-]{36}|requests\/[a-f0-9-]{36})$/.test(route)) || (method==='POST' && (/^\/billing\/(quote|checkout|customers|settings|taxes|test-print)$/.test(route) || /^\/billing\/invoices\/[a-f0-9-]{36}\/print$/.test(route)));
      const reportsAllowed=method==='GET' && /^\/reports\/(dashboard|daily|monthly|yearly|products|categories|variants|payments|stock|inventory|profit|net-profit|expenses|returns|customers|customer-history)(?:\?[^#]*)?$/.test(route);
      const customersAllowed=(method==='GET' && /^\/customers(?:\?[^#]*|\/countries|\/duplicates\?[^#]*|\/[a-f0-9-]{36}(?:\/changes|\?[^#]*)?)?$/.test(route)) || (method==='POST' && route==='/customers') || (method==='PUT' && /^\/customers\/[a-f0-9-]{36}$/.test(route));
      const controlsAllowed=(method==='GET' && /^(\/backups|\/auth\/users|\/controls\/(expenses|expense-categories|returns\/[a-f0-9-]{36}|return\/[a-f0-9-]{36}|return-status\/[a-f0-9-]{36}))$/.test(route)) || (method==='POST' && /^(\/backups(?:\/settings|\/cancel-restore)?|\/auth\/users\/[a-f0-9-]{36}\/(reset|state)|\/controls\/(returns(?:\/preview)?|expenses(?:\/[a-f0-9-]{36}\/void)?|expense-categories))$/.test(route));
      if(!allowed && !billingAllowed && !reportsAllowed && !customersAllowed && !controlsAllowed) throw new Error('Unsupported operation');
      const result=await api(method,route,body);
      if(route==='/auth/login'){userSession=result.session;return {user:result.user};}
      if(route==='/auth/logout')userSession='';
      return result;
    });
    ipcMain.handle('catalog:upload',async event=>{
      caller(event);const chosen=await dialog.showOpenDialog(window,{title:'Choose stock workbook',properties:['openFile'],filters:[{name:'Excel workbook',extensions:['xlsx','xls']}]});
      if(chosen.canceled)return null;const file=chosen.filePaths[0];if(fs.statSync(file).size>10*1024*1024)throw new Error('Maximum workbook size is 10 MB');
      const form=new FormData();form.append('file',new Blob([fs.readFileSync(file)]),path.basename(file));return api('POST','/imports/upload',form);
    });
    ipcMain.handle('catalog:download',async(event,input)=>{
      caller(event);let route,method='GET',body,name;
      if(input?.kind==='database-export'){route='/backups/export';name='east-of-town-database.json';}
      else if(input?.kind==='template'){route='/imports/template';name='east-of-town-stock-template.xlsx';}
      else if(input?.kind==='errors' && /^[a-f0-9-]{36}$/.test(input.batchId)){route=`/imports/${input.batchId}/errors`;name='import-validation.xlsx';}
      else if(input?.kind==='labels'){route='/labels/pdf';method='POST';body=input.labels;name='barcode-labels.pdf';}
      else if(input?.kind==='receipt' && /^[a-f0-9-]{36}$/.test(input.invoiceId) && [58,80].includes(input.width)){route='/billing/invoices/'+input.invoiceId+'/pdf?width='+input.width;name='receipt-'+input.invoiceId+'.pdf';}
      else if(input?.kind==='report' && /^(daily|monthly|yearly|products|categories|variants|payments|stock|inventory|profit|net-profit|expenses|returns|customers|customer-history)$/.test(input.type) && ['pdf','xlsx'].includes(input.format)){
        const query=new URLSearchParams({format:input.format,preset:String(input.preset||'today')});
        for(const key of ['from','to','customerId'])if(input[key])query.set(key,String(input[key]));
        route='/reports/'+input.type+'/export?'+query.toString();name='east-of-town-'+input.type+'.'+input.format;
      }
      else throw new Error('Unsupported download');
      const data=await api(method,route,body,true);
      const selected=await dialog.showSaveDialog(window,{defaultPath:path.join(app.getPath('downloads'),name),filters:[{name:name.endsWith('.pdf')?'PDF':'Excel',extensions:[name.split('.').pop()]}]});
      if(selected.canceled || !selected.filePath)return {saved:false};fs.writeFileSync(selected.filePath,data);return {saved:true};
    });
    ipcMain.handle('catalog:maintenance',async(event,input)=>{
      caller(event);
      if(input?.kind==='restore'){
        await api('GET','/backups');
        const chosen=await dialog.showOpenDialog(window,{title:'Select database backup to restore',properties:['openFile'],filters:[{name:'SQLite backup',extensions:['db']}]});if(chosen.canceled)return {};
        const confirm=await dialog.showMessageBox(window,{type:'warning',buttons:['Cancel','Validate and prepare restore'],defaultId:0,cancelId:0,message:'Restore replaces all current records after restart.',detail:'A rescue copy will be kept. You must use the login credentials in the backup. Close the app immediately after preparation.'});if(confirm.response!==1)return {};
        return api('POST','/backups/restore',{path:chosen.filePaths[0]});
      }
      if(input?.kind==='windows-share' && /^[a-f0-9-]{36}$/.test(input.invoiceId) && ['pdf','png'].includes(input.format)){
        const meta=await api('POST','/billing/invoices/'+input.invoiceId+'/share-file');
        const confirmed=await dialog.showMessageBox(window,{type:'question',buttons:['Cancel','Open Windows Share'],defaultId:0,cancelId:0,message:'Share receipt '+meta.invoiceNumber+'?',detail:'The receipt file is included. Choose WhatsApp and the correct customer in Windows Share. Review and send there. Only installed apps that accept this file type appear.'});if(confirmed.response!==1)return {};
        const bytes=await api('GET','/billing/invoices/'+input.invoiceId+'/'+input.format+'?width=80',undefined,true);
        return windowsShare.shareReceipt({base:app.isPackaged?process.resourcesPath:app.getAppPath(),directory:path.join(app.getPath('userData'),'shared-receipts'),invoiceId:input.invoiceId,invoiceNumber:meta.invoiceNumber,format:input.format,bytes});
      }
      if(input?.kind==='whatsapp' && /^[a-f0-9-]{36}$/.test(input.invoiceId) && ['pdf','png'].includes(input.format)){
        const meta=await api('POST','/billing/invoices/'+input.invoiceId+'/share',{phone:input.phone});
        const confirm=await dialog.showMessageBox(window,{type:'question',buttons:['Cancel','Save receipt and open WhatsApp'],defaultId:0,cancelId:0,message:'Share purchase receipt with '+meta.phone+'?',detail:'The customer must agree. Save the file, attach it in WhatsApp and review before sending. The app cannot confirm delivery.'});if(confirm.response!==1)return {};
        const bytes=await api('GET','/billing/invoices/'+input.invoiceId+'/'+input.format+'?width=80',undefined,true);
        const selected=await dialog.showSaveDialog(window,{defaultPath:path.join(app.getPath('downloads'),'receipt-'+meta.invoiceNumber+'.'+input.format),filters:[{name:input.format.toUpperCase(),extensions:[input.format]}]});if(selected.canceled)return {};
        fs.writeFileSync(selected.filePath,bytes);shell.showItemInFolder(selected.filePath);
        await shell.openExternal('https://wa.me/'+meta.phone+'?text='+encodeURIComponent(meta.message));
        return {message:'Receipt saved. Attach it to the opened WhatsApp chat, review and send. Delivery is not confirmed by this app.'};
      }
      throw new Error('Unsupported maintenance operation');
    });
    await window.loadFile(path.join(app.getAppPath(),'dist','index.html'));
  } catch(e) { dialog.showErrorBox('Unable to start East of Town',e.message);app.quit(); }
});
app.on('window-all-closed',()=>app.quit());
app.on('before-quit',()=>{shuttingDown=true;windowsShare.stopShares();if(backend){backend.removeAllListeners('exit');backend.kill();}});
}
