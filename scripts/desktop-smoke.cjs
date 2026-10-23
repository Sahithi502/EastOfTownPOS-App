const {_electron:electron,expect}=require('@playwright/test');
const fs=require('node:fs');
const path=require('node:path');
const os=require('node:os');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..');
const profile=fs.mkdtempSync(path.join(os.tmpdir(),'eot-desktop-stage2-'));
const evidence=path.join(root,'docs','screenshots');
const fixtures=path.join(root,'backend','target','test-fixtures');
fs.mkdirSync(evidence,{recursive:true});
async function launch(){return electron.launch({args:[root],env:{...process.env,EOT_TEST_PROFILE:profile},timeout:60000});}
async function login(page){await page.getByLabel('Username',{exact:true}).fill('test-owner');await page.getByLabel('Password',{exact:true}).fill('test-only-desktop-password');await page.getByRole('button',{name:/Create administrator and sign in|^Sign in$/}).click();await expect(page.getByRole('heading',{name:'Product catalog',exact:true})).toBeVisible({timeout:30000});}
async function dialogs(app,upload){await app.evaluate(({dialog},{upload,profile})=>{dialog.showOpenDialog=async()=>({canceled:false,filePaths:[upload]});dialog.showSaveDialog=async(_window,options)=>({canceled:false,filePath:profile+'/'+options.defaultPath.split(/[\\/]/).pop()});},{upload,profile});}
async function screenshot(page,name){await page.screenshot({path:path.join(evidence,name),fullPage:true});}
(async()=>{
 let app;
 try{
  assert.ok(fs.existsSync(path.join(fixtures,'desktop-valid.xlsx')),'Run mvn package first to generate isolated test fixtures');
  app=await launch();let page=await app.firstWindow();
  await login(page);
  await page.getByRole('button',{name:'Excel import',exact:true}).click();
  await dialogs(app,path.join(fixtures,'desktop-invalid.xlsx'));
  await page.getByRole('button',{name:'Download template',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('Excel file saved');
  assert.ok(fs.statSync(path.join(profile,'east-of-town-stock-template.xlsx')).size>100);
  await page.getByRole('button',{name:'Choose Excel file',exact:true}).click();
  await expect(page.getByRole('heading',{name:/Column mapping/})).toBeVisible();
  await page.getByLabel(/Stock policy/).selectOption('OPENING_NEW');
  await page.getByRole('button',{name:'Validate and preview',exact:true}).click();
  await expect(page.getByText(/Correct all row errors in Excel/)).toBeVisible();
  await page.getByRole('button',{name:'Export errors / warnings',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('Excel file saved');
  assert.ok(fs.statSync(path.join(profile,'import-validation.xlsx')).size>100);
  await screenshot(page,'stage2-import-errors.png');
  await dialogs(app,path.join(fixtures,'desktop-valid.xlsx'));
  await page.getByRole('button',{name:'Choose Excel file',exact:true}).click();
  await expect(page.getByRole('heading',{name:/Column mapping · 2 rows/})).toBeVisible();
  await page.getByLabel(/Stock policy/).selectOption('OPENING_NEW');
  await page.getByRole('button',{name:'Validate and preview',exact:true}).click();
  await expect(page.getByRole('button',{name:'Confirm and save import',exact:true})).toBeDisabled();
  await screenshot(page,'stage2-import-preview.png');
  await page.getByRole('checkbox',{name:/I reviewed all rows/}).check();
  await page.getByRole('button',{name:'Confirm and save import',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('2 created, 0 updated, 0 skipped, 0 failed');
  await page.getByRole('button',{name:'Inventory',exact:true}).click();
  const row=page.getByRole('row').filter({hasText:'TEST-UI-M'});
  await expect(row).toContainText('7');
  await row.getByRole('button',{name:'Adjust stock',exact:true}).click();
  await page.getByLabel('Units to add (+) or remove (-)',{exact:true}).fill('2');
  await page.getByLabel('Reason',{exact:true}).fill('TEST ONLY received stock');
  await page.getByRole('checkbox',{name:/I checked the variant/}).check();
  await screenshot(page,'stage2-stock-adjustment.png');
  await page.getByRole('button',{name:'Confirm adjustment',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('Stock adjustment recorded');
  await expect(row.getByText('9',{exact:true})).toBeVisible();
  await row.getByRole('button',{name:'History',exact:true}).click();
  await expect(page.getByRole('cell',{name:'TEST ONLY received stock test-owner',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Close',exact:true}).click();
  await page.getByRole('button',{name:'Barcode labels',exact:true}).click();
  await page.getByRole('checkbox',{name:'Select TEST-UI-M',exact:true}).check();
  await page.getByRole('button',{name:'Preview labels',exact:true}).click();
  await expect(page.getByRole('img',{name:/Code 128:/})).toBeVisible();
  await screenshot(page,'stage2-barcode-labels.png');
  await page.getByRole('button',{name:'Save PDF',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('PDF saved');
  assert.ok(fs.readFileSync(path.join(profile,'barcode-labels.pdf')).subarray(0,4).equals(Buffer.from('%PDF')));
  await app.close();app=await launch();page=await app.firstWindow();await login(page);
  await page.getByRole('button',{name:'Inventory',exact:true}).click();
  await expect(page.getByRole('row').filter({hasText:'TEST-UI-M'}).getByText('9',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Excel import',exact:true}).click();await expect(page.getByRole('cell').filter({hasText:'desktop-valid.xlsx'})).toBeVisible();
  console.log('PASS: Electron setup/login, native-dialog Excel upload, template/error export, invalid rows, confirmed import, stock adjustment/history, label preview/PDF, restart persistence.');
  console.log('Isolated profile: '+profile);console.log('Screenshots: '+evidence);
 }finally{if(app)await app.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
