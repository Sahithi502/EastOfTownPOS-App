const {spawn}=require('node:child_process');
const {mkdtempSync}=require('node:fs');
const {tmpdir}=require('node:os');
const path=require('node:path');
const {randomBytes}=require('node:crypto');
const assert=require('node:assert/strict');
const net=require('node:net');
const root=path.resolve(__dirname,'..');
const directory=mkdtempSync(path.join(tmpdir(),'eastoftown-api-test-'));
const token=randomBytes(32).toString('hex');
let processHandle,port;
async function call(method,route,body,authorized=true){
  const response=await fetch(`http://127.0.0.1:${port}/api${route}`,{method,headers:{'Content-Type':'application/json',...(authorized?{'X-EOT-Token':token}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(5000)});
  const text=await response.text();let value;try{value=JSON.parse(text);}catch{value=text;}
  return {status:response.status,value};
}
async function start(){
  port=await new Promise((resolve,reject)=>{const server=net.createServer();server.on('error',reject);server.listen(0,'127.0.0.1',()=>{const p=server.address().port;server.close(()=>resolve(p));});});
  processHandle=spawn(path.join(root,'runtime','bin','java.exe'),['-jar',path.join(root,'backend','target','pos-0.1.0.jar')],{windowsHide:true,stdio:['ignore','ignore','pipe'],env:{...process.env,EOT_DATA_DIR:directory,EOT_PORT:String(port),EOT_API_TOKEN:token}});
  let failure;processHandle.on('error',e=>{failure=e;});
  processHandle.stderr.on('data',()=>{});
  for(let i=0;i<100;i++){if(failure)throw failure;if(processHandle.exitCode!==null)throw new Error('Backend exited during startup');try{if((await call('GET','/health')).status===200)return;}catch{}await new Promise(r=>setTimeout(r,200));}
  throw new Error('Backend did not start');
}
async function stop(){if(processHandle&&processHandle.exitCode===null){await new Promise(resolve=>{processHandle.once('exit',resolve);processHandle.kill();});}}
(async()=>{
  try{
    await start();
    assert.equal((await call('GET','/variants',undefined,false)).status,401);
    assert.equal((await call('GET','/categories')).value.length,8);
    const input={name:'TEST ONLY Cotton shirt',categoryId:'shirts',sku:'TEST-SH-M-BL',size:'M',colour:'Blue',barcode:null,cost:'450.25',price:'999.99',openingStock:8,reorderLevel:3,active:true,version:0};
    assert.equal((await call('POST','/variants',{...input,name:''})).status,400);
    assert.equal((await call('POST','/variants',{...input,price:'1.001'})).status,400);
    assert.equal((await call('POST','/variants',{...input,openingStock:-1})).status,400);
    const created=await call('POST','/variants',input);assert.equal(created.status,200);
    assert.equal((await call('POST','/variants',input)).status,409);
    let records=(await call('GET','/variants')).value;assert.equal(records.length,1);
    const barcode=records[0].barcode;
    assert.equal(records[0].selling_paise,99999);assert.equal(records[0].current_stock,8);
    const edited={...input,barcode,openingStock:0,price:'899.50'};
    assert.equal((await call('PUT','/variants/'+created.value.id,edited)).status,200);
    assert.equal((await call('PUT','/variants/'+created.value.id,edited)).status,400);
    await stop();await start();
    records=(await call('GET','/variants')).value;
    assert.equal(records.length,1);assert.equal(records[0].barcode,barcode);
    assert.equal(records[0].selling_paise,89950);assert.equal(records[0].current_stock,8);
    assert.equal((await call('GET','/health')).value.database,'ok');
    console.log('PASS: unauthorized access, validation, duplicate rollback, edit conflict, permanent barcode, exact prices, restart persistence and database health.');
    console.log('Isolated test database retained at '+directory);
  }finally{await stop();}
})().catch(error=>{console.error(error);process.exitCode=1;});
