const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const { spawn } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const path = require('node:path');
const fs = require('node:fs');
const net = require('node:net');
let backend, window;
let userSession='';
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
    const jar = app.isPackaged ? path.join(base,'backend','pos.jar') : path.join(base,'backend','target','pos-0.3.0.jar');
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
    backend.on('exit',()=>{failure=new Error('The local database service stopped. Restart the application.'); if(window) dialog.showErrorBox('Database service stopped',failure.message);});
    let ready=false;
    for(let i=0;i<120;i++) {
      if(failure) throw failure;
      try {const r=await fetch(`http://127.0.0.1:${port}/api/health`,{headers:{'X-EOT-Token':token},signal:AbortSignal.timeout(1000)});if(r.ok){ready=true;break;}} catch{}
      await new Promise(r=>setTimeout(r,500));
    }
    if(!ready) throw new Error('Database startup timed out. Check backend.log in the application data folder.');
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
      if(!allowed && !billingAllowed) throw new Error('Unsupported operation');
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
      if(input?.kind==='template'){route='/imports/template';name='east-of-town-stock-template.xlsx';}
      else if(input?.kind==='errors' && /^[a-f0-9-]{36}$/.test(input.batchId)){route=`/imports/${input.batchId}/errors`;name='import-validation.xlsx';}
      else if(input?.kind==='labels'){route='/labels/pdf';method='POST';body=input.labels;name='barcode-labels.pdf';}
      else if(input?.kind==='receipt' && /^[a-f0-9-]{36}$/.test(input.invoiceId) && [58,80].includes(input.width)){route='/billing/invoices/'+input.invoiceId+'/pdf?width='+input.width;name='receipt-'+input.invoiceId+'.pdf';}
      else throw new Error('Unsupported download');
      const data=await api(method,route,body,true);
      const selected=await dialog.showSaveDialog(window,{defaultPath:path.join(app.getPath('downloads'),name),filters:[{name:name.endsWith('.pdf')?'PDF':'Excel',extensions:[name.endsWith('.pdf')?'pdf':'xlsx']}]});
      if(selected.canceled || !selected.filePath)return {saved:false};fs.writeFileSync(selected.filePath,data);return {saved:true};
    });
    await window.loadFile(path.join(app.getAppPath(),'dist','index.html'));
  } catch(e) { dialog.showErrorBox('Unable to start East of Town',e.message);app.quit(); }
});
app.on('window-all-closed',()=>app.quit());
app.on('before-quit',()=>{if(backend){backend.removeAllListeners('exit');backend.kill();}});
}
