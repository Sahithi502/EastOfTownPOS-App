const {_electron:electron,expect}=require('@playwright/test');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),profile=fs.mkdtempSync(path.join(os.tmpdir(),'eot-desktop-stage3-'));
const evidence=path.join(root,'docs','screenshots');fs.mkdirSync(evidence,{recursive:true});
async function launch(){return electron.launch({args:[root],env:{...process.env,EOT_TEST_PROFILE:profile},timeout:60000});}
async function login(page){await page.getByLabel('Username',{exact:true}).fill('test-owner');await page.getByLabel('Password',{exact:true}).fill('test-only-stage3-password');await page.getByRole('button',{name:/Create administrator and sign in|^Sign in$/}).click();await expect(page.getByRole('heading',{name:'Product catalog',exact:true})).toBeVisible({timeout:30000});}
async function call(page,method,route,body){return page.evaluate(({method,route,body})=>window.catalog.request(method,route,body),{method,route,body});}
(async()=>{let app;try{
 app=await launch();let page=await app.firstWindow();await login(page);
 await app.evaluate(({dialog},profile)=>{dialog.showSaveDialog=async(_w,options)=>({canceled:false,filePath:profile+'/'+options.defaultPath.split(/[\\/]/).pop()})},profile);
 const created=await call(page,'POST','/variants',{name:'TEST ONLY Classic shirt',categoryId:'shirts',sku:'TEST-POS-M',size:'M',colour:'Blue',barcode:null,cost:'75',price:'125',openingStock:5,reorderLevel:2,active:true,version:0});
 const variant=(await call(page,'GET','/variants?search=TEST-POS-M'))[0];
 const config=await call(page,'GET','/billing/settings');await call(page,'POST','/billing/settings',{...config,autoPrint:true,printer:''});
 await page.getByRole('button',{name:'New bill',exact:true}).click();
 await page.getByLabel('Barcode scan',{exact:true}).fill('UNKNOWN-TEST');await page.getByLabel('Barcode scan',{exact:true}).press('Enter');await expect(page.getByRole('alert')).toContainText('Unknown barcode');
 for(let i=0;i<2;i++){await page.getByLabel('Barcode scan',{exact:true}).fill(variant.barcode);await page.getByLabel('Barcode scan',{exact:true}).press('Enter');await expect(page.getByLabel('Quantity TEST-POS-M',{exact:true})).toHaveValue(String(i+1));}
 await page.getByLabel('Discount TEST-POS-M',{exact:true}).fill('5.00');await page.getByLabel('Bill discount (INR)',{exact:true}).fill('10.00');
 await page.getByRole('button',{name:'Create customer',exact:true}).click();await page.getByLabel('Customer name',{exact:true}).fill('TEST ONLY Customer');await page.getByLabel('Mobile number',{exact:true}).fill('9999900011');await page.getByRole('button',{name:'Save and select customer',exact:true}).click();
 await page.screenshot({path:path.join(evidence,'stage3-new-bill.png'),fullPage:false});
 await page.getByRole('button',{name:'Review bill and payment (F9)',exact:true}).click();await expect(page.getByRole('heading',{name:'Amount payable: ₹235.00',level:2,exact:true})).toBeVisible();
 await page.getByLabel('Payment method',{exact:true}).selectOption('SPLIT');await page.getByLabel('CASH amount (INR)',{exact:true}).fill('100');await page.getByLabel('UPI amount (INR)',{exact:true}).fill('135');
 await page.screenshot({path:path.join(evidence,'stage3-payment.png'),fullPage:false});await page.getByRole('button',{name:'Payment received — save invoice',exact:true}).click();
 await expect(page.getByRole('heading',{name:'Saved invoice',exact:true})).toBeVisible();await expect(page.getByRole('alert').last()).toContainText('Automatic printing failed');
 const receipt=page.getByLabel('Sales receipt preview',{exact:true});
 await expect(receipt.getByRole('heading',{name:'SALES RECEIPT',exact:true})).toBeVisible();await expect(receipt.getByRole('columnheader')).toHaveText(['ITEM','QTY','AMOUNT']);
 const itemRow=receipt.getByRole('row').filter({hasText:'TEST ONLY Classic shirt'});await expect(itemRow.getByRole('cell').nth(1)).toHaveText('2');await expect(itemRow.getByRole('cell').nth(2)).toHaveText('250.00');await expect(receipt.locator('.receipt-summary')).toContainText('Rs. 250.00');await expect(receipt.locator('.receipt-grand-total')).toContainText('Rs. 235.00');await expect(receipt.getByText('Line total: Rs. 235.00',{exact:true})).toBeVisible();await expect(receipt).toContainText('Payment: Split (Paid)');
 assert.ok(await receipt.evaluate(node=>[...node.querySelectorAll('th,td')].every(cell=>cell.scrollWidth<=cell.clientWidth+1)),'80 mm preview columns must not overflow');
 await receipt.screenshot({path:path.join(evidence,'receipt-reference-preview-80.png')});
 await page.getByRole('button',{name:'Save receipt PDF',exact:true}).click();await expect(page.getByRole('status').last()).toContainText('Receipt PDF saved');
 await page.getByLabel('Receipt paper width',{exact:true}).selectOption('58');await expect(receipt).toHaveClass(/receipt-narrow/);assert.ok(await receipt.evaluate(node=>[...node.querySelectorAll('th,td')].every(cell=>cell.scrollWidth<=cell.clientWidth+1)),'58 mm preview columns must not overflow');await receipt.screenshot({path:path.join(evidence,'receipt-reference-preview-58.png')});await page.getByRole('button',{name:'Save receipt PDF',exact:true}).click();
 await page.getByRole('button',{name:'Print / reprint receipt',exact:true}).click();await expect(page.getByRole('alert').last()).toContainText('Select a receipt printer');
 await page.screenshot({path:path.join(evidence,'stage3-receipt.png'),fullPage:false});
 const history=await call(page,'GET','/billing/invoices?search=9999900011');assert.equal(history.length,1);const invoiceId=history[0].id;assert.equal(history[0].total_paise,23500);assert.equal((await call(page,'GET','/variants?search=TEST-POS-M'))[0].current_stock,3);
 const pdf=path.join(profile,'receipt-'+invoiceId+'.pdf');assert.equal(fs.readFileSync(pdf).subarray(0,4).toString(),'%PDF');
 await page.getByRole('button',{name:'Close receipt',exact:true}).click();await page.getByRole('button',{name:'Invoice history',exact:true}).click();await page.getByLabel('Invoice / customer / mobile',{exact:true}).fill('9999900011');await page.getByRole('button',{name:'Search invoices',exact:true}).click();await expect(page.getByRole('cell',{name:'TEST ONLY Customer +919999900011',exact:true})).toBeVisible();
 await page.screenshot({path:path.join(evidence,'stage3-invoice-history.png'),fullPage:false});
 // Simulate a lost checkout response: persist a request which completed before the app was interrupted.
 const owner=(await call(page,'GET','/auth/status'));const userId=(await page.evaluate(()=>window.catalog.request('POST','/auth/login',{username:'test-owner',password:'test-only-stage3-password'}))).user.id;
 const cart={lines:[{variantId:created.id,quantity:1,discountPaise:0}],billDiscountPaise:0,customerId:null};
 const quote=await call(page,'POST','/billing/quote',cart);const request={requestKey:require('node:crypto').randomUUID(),cart,quoteToken:quote.token,payments:[{method:'CASH',amountPaise:12500}]};
 await call(page,'POST','/billing/checkout',request);await page.evaluate(({userId,request})=>localStorage.setItem('eot-pending-checkout-'+userId,JSON.stringify(request)),{userId,request});
 await app.close();app=await launch();page=await app.firstWindow();await login(page);await page.getByRole('button',{name:'New bill',exact:true}).click();await expect(page.getByRole('heading',{name:'Checkout awaiting confirmation'})).toBeVisible();await page.getByRole('button',{name:'Check checkout status',exact:true}).click();await expect(page.getByRole('heading',{name:'Saved invoice',exact:true})).toBeVisible();assert.equal((await call(page,'GET','/variants?search=TEST-POS-M'))[0].current_stock,2);
 await page.getByRole('button',{name:'Close receipt',exact:true}).click();await page.getByRole('button',{name:'Billing settings',exact:true}).click();await expect(page.getByRole('heading',{name:'Billing settings',exact:true})).toBeVisible();await page.screenshot({path:path.join(evidence,'stage3-billing-settings.png'),fullPage:false});
 console.log('PASS: Stage 3 real Electron scanner entry/repeated scans, discounts, customer creation, split payment, saved invoice, 58/80 mm PDF, printer failure, invoice search, stock, restart and uncertain checkout recovery.');console.log('Isolated profile: '+profile);
}finally{if(app)await app.close();}})().catch(e=>{console.error(e);process.exitCode=1;});
