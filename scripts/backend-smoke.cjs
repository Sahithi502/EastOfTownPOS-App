const {spawn}=require('node:child_process');
const {mkdtempSync,existsSync}=require('node:fs');
const {tmpdir}=require('node:os');
const path=require('node:path');
const {randomBytes}=require('node:crypto');
const assert=require('node:assert/strict');
const net=require('node:net');
const root=path.resolve(__dirname,'..');
const directory=mkdtempSync(path.join(tmpdir(),'eastoftown-api-test-'));
const token=randomBytes(32).toString('hex');
let processHandle,port,session='';
async function call(method,route,body,authorized=true){
  const response=await fetch(`http://127.0.0.1:${port}/api${route}`,{method,headers:{'Content-Type':'application/json',...(authorized?{'X-EOT-Token':token,'X-EOT-Session':session}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(15000)});
  const text=await response.text();let value;try{value=JSON.parse(text);}catch{value=text;}
  return {status:response.status,value};
}
async function start(){
  port=await new Promise((resolve,reject)=>{const server=net.createServer();server.on('error',reject);server.listen(0,'127.0.0.1',()=>{const p=server.address().port;server.close(()=>resolve(p));});});
  const bundled=path.join(root,'runtime','bin','java.exe');
  const java=existsSync(bundled)?bundled:process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin','java.exe'):'java';
  console.log('Using Java: '+java);
  processHandle=spawn(java,['-jar',path.join(root,'backend','target','pos-0.3.0.jar')],{windowsHide:true,stdio:['ignore','ignore','pipe'],env:{...process.env,EOT_DATA_DIR:directory,EOT_PORT:String(port),EOT_API_TOKEN:token}});
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
    assert.equal((await call('POST','/auth/setup',{username:'test-owner',password:'test-only-owner-password'})).status,200);
    session=(await call('POST','/auth/login',{username:'test-owner',password:'test-only-owner-password'})).value.session;
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
    const adjustment={variantId:created.value.id,quantity:2,expectedStock:8,reason:'TEST ONLY received stock',date:new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()),requestKey:require('node:crypto').randomUUID()};
    assert.equal((await call('POST','/stock/adjustments',adjustment)).status,200);
    assert.equal((await call('POST','/stock/adjustments',adjustment)).status,200);
    assert.equal((await call('POST','/stock/adjustments',{...adjustment,requestKey:require('node:crypto').randomUUID(),quantity:-100,expectedStock:10})).status,400);
    await stop();session='';await start();
    assert.equal((await call('POST','/stock/adjustments',adjustment)).status,401);
    session=(await call('POST','/auth/login',{username:'test-owner',password:'test-only-owner-password'})).value.session;
    records=(await call('GET','/variants')).value;
    assert.equal(records.length,1);assert.equal(records[0].barcode,barcode);
    assert.equal(records[0].selling_paise,89950);assert.equal(records[0].current_stock,10);
    assert.equal((await call('GET','/health')).value.database,'ok');
    const label={items:[{variantId:created.value.id,copies:2}],widthMm:80,heightMm:40};
    assert.equal((await call('POST','/labels/preview',label)).status,200);
    assert.equal((await call('POST','/labels/print',{labels:label,printer:'TEST-MISSING-PRINTER',test:false})).status,400);
    console.log('PASS: authorization, validation, duplicate rollback, edit conflict, permanent barcode, exact prices, idempotent stock adjustments, label preview, missing printer, restart persistence and database health.');
    const customer=await call('POST','/billing/customers',{name:'TEST ONLY API Customer',mobile:'9999912345',email:null});assert.equal(customer.status,200);
    const cart={lines:[{variantId:created.value.id,quantity:2,discountPaise:100}],billDiscountPaise:50,customerId:customer.value.id};
    const quote=await call('POST','/billing/quote',cart);assert.equal(quote.status,200);assert.equal(quote.value.totalPaise,179750);
    const checkout={requestKey:require('node:crypto').randomUUID(),cart,quoteToken:quote.value.token,payments:[{method:'CASH',amountPaise:100000},{method:'UPI',amountPaise:79750}]};
    const saved=await call('POST','/billing/checkout',checkout);assert.equal(saved.status,200);const invoiceId=saved.value.invoice.id;
    assert.equal((await call('POST','/billing/checkout',checkout)).value.invoice.id,invoiceId);assert.equal((await call('GET','/variants')).value[0].current_stock,8);
    await stop();session='';await start();session=(await call('POST','/auth/login',{username:'test-owner',password:'test-only-owner-password'})).value.session;
    assert.equal((await call('POST','/billing/checkout',checkout)).value.invoice.id,invoiceId);assert.equal((await call('GET','/variants')).value[0].current_stock,8);
    assert.equal((await call('GET','/billing/invoices?search=9999912345')).value.length,1);assert.equal((await call('GET','/billing/requests/'+checkout.requestKey)).value.found,true);
    assert.equal((await call('POST','/billing/invoices/'+invoiceId+'/print',{widthMm:80,printer:'TEST-MISSING-PRINTER'})).status,400);
    console.log('PASS: Stage 3 split checkout, customer purchase invoice, duplicate retry, printer failure and persistent invoice/payment/stock records across backend restart.');
    console.log('Isolated test database retained at '+directory);
  }finally{await stop();}
})().catch(error=>{console.error(error);process.exitCode=1;});
