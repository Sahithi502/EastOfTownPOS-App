const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const { spawn } = require('node:child_process');
const { randomBytes } = require('node:crypto');
const path = require('node:path');
const fs = require('node:fs');
const net = require('node:net');
let backend, window;
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
    const jar = app.isPackaged ? path.join(base,'backend','pos.jar') : path.join(base,'backend','target','pos-0.1.0.jar');
    const java = app.isPackaged ? path.join(base,'runtime','bin','java.exe') : 'java';
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
    ipcMain.handle('catalog:request',async(event,method,route,body)=>{
      if(event.sender!==window.webContents || event.senderFrame!==window.webContents.mainFrame) throw new Error('Unauthorized caller');
      const allowed=(method==='GET' && /^\/(health|categories|variants(?:\?search=[^#]*)?)$/.test(route)) || (method==='POST' && ['/categories','/variants'].includes(route)) || (method==='PUT' && /^\/variants\/[a-f0-9-]{36}$/.test(route));
      if(!allowed) throw new Error('Unsupported operation');
      const response=await fetch(`http://127.0.0.1:${port}/api${route}`,{method,headers:{'Content-Type':'application/json','X-EOT-Token':token},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});
      const result=await response.json();
      if(!response.ok) throw new Error(result.message || 'Request failed');
      return result;
    });
    await window.loadFile(path.join(app.getAppPath(),'dist','index.html'));
  } catch(e) { dialog.showErrorBox('Unable to start East of Town',e.message);app.quit(); }
});
app.on('window-all-closed',()=>app.quit());
app.on('before-quit',()=>{if(backend){backend.removeAllListeners('exit');backend.kill();}});
}
