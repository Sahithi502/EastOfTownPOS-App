const {_electron:electron,expect}=require('@playwright/test');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),profile=fs.mkdtempSync(path.join(os.tmpdir(),'eot-navigation-'));
const evidence=path.join(root,'docs/screenshots');
const call=(page,method,route,body)=>page.evaluate(({method,route,body})=>window.catalog.request(method,route,body),{method,route,body});
async function signIn(page,username,password,setup=false){
 await page.getByLabel('Username',{exact:true}).fill(username);await page.getByLabel('Password',{exact:true}).fill(password);
 await page.getByRole('button',{name:setup?'Create administrator and sign in':'Sign in',exact:true}).click();
}
(async()=>{let app;try{
 app=await electron.launch({args:[root],env:{...process.env,EOT_TEST_PROFILE:profile},timeout:60000});const page=await app.firstWindow();
 await signIn(page,'test-owner','test-only-navigation-owner',true);
 await expect(page.getByRole('heading',{name:'Product catalog',exact:true})).toBeVisible({timeout:30000});
 await page.evaluate(()=>{window.confirm=()=>true});const navigation=page.getByRole('navigation',{name:'Main navigation'});
 await expect(navigation.getByRole('button',{name:'Users',exact:true})).toHaveCount(0);
 assert.deepEqual(await navigation.locator('summary').allTextContents(),['Sales','Catalog and stock','Insights','Administration']);
 const sales=navigation.locator('summary').filter({hasText:/^Sales$/});await sales.focus();await page.keyboard.press('Space');
 await expect(navigation.getByRole('button',{name:'New bill',exact:true})).toBeHidden();await page.keyboard.press('Enter');
 await expect(navigation.getByRole('button',{name:'New bill',exact:true})).toBeVisible();
 await navigation.getByRole('button',{name:'Business controls',exact:true}).click();await page.getByRole('button',{name:'User access',exact:true}).click();
 await expect(navigation.getByRole('button',{name:'Business controls',exact:true})).toHaveAttribute('aria-current','page');
 await expect(page.getByRole('heading',{name:'User access and password recovery',exact:true})).toBeVisible();
 await page.getByLabel('Username',{exact:true}).fill('test-biller');await page.getByLabel('Password (at least 12 characters)',{exact:true}).fill('test-only-old-password');
 await page.getByRole('button',{name:'Create user',exact:true}).click();await expect(page.getByRole('status')).toContainText('User created.');
 const user=(await call(page,'GET','/auth/users')).find(u=>u.username==='test-biller');assert.equal(user.role,'BILLING');
 await page.getByRole('button',{name:'Deactivate test-biller',exact:true}).click();await expect(page.getByRole('button',{name:'Activate test-biller',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Activate test-biller',exact:true}).click();await expect(page.getByRole('button',{name:'Deactivate test-biller',exact:true})).toBeVisible();
 for(const expected of ['ADMIN','BILLING']){await page.getByRole('button',{name:'Change role for test-biller',exact:true}).click();await expect(page.locator('.account-row').filter({hasText:'test-biller'})).toContainText(expected);}
 await page.getByLabel('Billing user',{exact:true}).selectOption(user.id);await page.getByLabel('New billing password',{exact:true}).fill('test-only-new-password');
 await page.getByRole('button',{name:'Reset billing password',exact:true}).click();await expect(page.getByRole('status')).toContainText('Billing-user password reset.');
 await page.getByRole('button',{name:'Generate new recovery code',exact:true}).click();await expect(page.locator('.recovery-code')).toContainText('EOT-');
 // Sensitive code is not captured; leaving User access must clear it.
 await page.getByRole('button',{name:'Expenses',exact:true}).click();await page.getByRole('button',{name:'User access',exact:true}).click();await expect(page.locator('.recovery-code')).toHaveCount(0);
 await page.getByRole('button',{name:'Deactivate test-owner',exact:true}).click();
 await expect(page.getByRole('alert')).toHaveText('You cannot disable or demote your own administrator account');
 await page.getByRole('button',{name:'User access',exact:true}).click();
 fs.mkdirSync(evidence,{recursive:true});await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:path.join(evidence,'navigation-user-access.png'),fullPage:true});
 await navigation.getByRole('button',{name:'Customers',exact:true}).click();await page.getByRole('button',{name:'Add customer',exact:true}).click();
 await page.getByLabel('Customer name',{exact:true}).fill('TEST ONLY invalid phone');await page.getByLabel('Phone country / code',{exact:true}).selectOption('SG');
 await page.getByLabel('Mobile number',{exact:true}).fill('11111111');await page.getByRole('button',{name:'Save customer',exact:true}).click();
 await expect(page.getByRole('alert')).toHaveText('Enter a valid mobile number for the selected country.');
 assert.equal((await call(page,'GET','/customers?search=TEST%20ONLY%20invalid')).total,0);
 await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:path.join(evidence,'navigation-friendly-error.png'),fullPage:true});
 await navigation.getByRole('button',{name:'Invoice history',exact:true}).click();await page.getByLabel('Exact total (INR)',{exact:true}).fill('5%');
 await page.getByRole('button',{name:'Search invoices',exact:true}).click();await expect(page.getByRole('alert')).toHaveText('Enter a non-negative amount with up to two decimal places.');
 await app.evaluate(({BrowserWindow})=>BrowserWindow.getAllWindows()[0].setSize(1000,720));
 const signOut=page.getByRole('button',{name:'Sign out',exact:true});await expect(signOut).toBeInViewport();
 await signOut.click();await signIn(page,'test-biller','test-only-old-password');await expect(page.getByRole('alert')).toBeVisible();
 assert.doesNotMatch(await page.getByRole('alert').innerText(),/Error:|remote method|catalog:/);
 await signIn(page,'test-biller','test-only-new-password');await expect(page.getByRole('heading',{name:'Product catalog',exact:true})).toBeVisible();
 assert.deepEqual(await page.getByRole('navigation',{name:'Main navigation'}).locator('summary').allTextContents(),['Sales','Catalog and stock']);
 for(const name of ['Business controls','Users','Billing settings','Reports','Excel import'])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(0);
 await assert.rejects(()=>call(page,'GET','/auth/users'),/Administrator permission required/);
 await expect(page.getByRole('button',{name:'Sign out',exact:true})).toBeInViewport();
 console.log('PASS BUG-001: consolidated creation, reset, role/activation and recovery controls; old password rejected and billing permissions preserved.');
 console.log('PASS BUG-002: grouped navigation, keyboard collapse/expand, selected screens and short-window sign out.');
 console.log('PASS BUG-003: actual backend mobile/access errors, local money validation and login failure display without IPC prefixes.');
 console.log('Isolated profile: '+profile);
}finally{if(app)await app.close();}})().catch(error=>{console.error(error);process.exitCode=1;});
