const {_electron:electron,expect}=require('@playwright/test');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict'),{randomUUID}=require('node:crypto');
const root=path.resolve(__dirname,'..'),profile=path.join(os.tmpdir(),'eot-acceptance-'+randomUUID());fs.mkdirSync(profile);
const executable=path.join(root,'release','win-unpacked','East of Town.exe');
const launch=()=>electron.launch({executablePath:executable,args:['--acceptance-profile='+profile],env:{...process.env,JAVA_HOME:'',PATH:process.env.SystemRoot+'\\System32'},timeout:90000});
const call=(page,method,route,body)=>page.evaluate(({method,route,body})=>window.catalog.request(method,route,body),{method,route,body});
async function login(page,setup){await page.getByLabel('Username',{exact:true}).fill('test-owner');await page.getByLabel('Password',{exact:true}).fill('test-only-packaged-password');await page.getByRole('button',{name:setup?'Create administrator and sign in':'Sign in',exact:true}).click();await expect(page.getByRole('heading',{name:'Product catalog',exact:true})).toBeVisible({timeout:45000});}
(async()=>{let app;try{
 app=await launch();let page=await app.firstWindow();await login(page,true);await page.context().setOffline(true);
 assert.equal(await app.evaluate(({app})=>app.isPackaged),true);assert.equal(await app.evaluate(({app})=>app.getVersion()),'0.6.0');
 const product=await call(page,'POST','/variants',{name:'TEST ONLY packaged shirt',categoryId:'shirts',sku:'TEST-PACKAGED',size:'M',colour:'Blue',barcode:null,cost:'40',price:'100',openingStock:4,reorderLevel:1,active:true,version:0});
 const customer=await call(page,'POST','/customers',{name:'TEST ONLY packaged customer',country:'IN',mobile:'9999800022',email:null});const cart={lines:[{variantId:product.id,quantity:2,discountPaise:0}],billDiscountPaise:0,customerId:customer.id};const quote=await call(page,'POST','/billing/quote',cart);await call(page,'POST','/billing/checkout',{requestKey:randomUUID(),cart,quoteToken:quote.token,payments:[{method:'CASH',amountPaise:20000}]});
 const backup=await call(page,'POST','/backups');assert.ok(fs.existsSync(backup.path));await app.close();app=await launch();page=await app.firstWindow();await login(page,false);assert.equal((await call(page,'GET','/billing/invoices')).length,1);assert.equal((await call(page,'GET','/variants?search=TEST-PACKAGED'))[0].current_stock,2);assert.equal((await call(page,'GET','/billing/customers?search=9999800022'))[0].mobile,'+919999800022');
 await page.getByRole('button',{name:'Dashboard',exact:true}).click();await expect(page.locator('[data-metric="Invoice value including tax"]')).toContainText('200.00');
 console.log('PASS: packaged 0.6.0 app, bundled Java with system Java absent from PATH, fresh isolated profile, renderer offline, real checkout/stock/customer/backup and app restart persistence.');console.log('Isolated acceptance profile: '+profile);
}finally{if(app)await app.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
