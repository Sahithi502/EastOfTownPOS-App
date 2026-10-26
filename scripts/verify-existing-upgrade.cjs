// Optional diagnostic: npm packages/runtime and a built backend are required.
// node scripts/verify-existing-upgrade.cjs --source <existing database>
// Reads the source only; upgrades a consistent, separately retained SQLite backup.
const {DatabaseSync,backup}=require('node:sqlite');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),net=require('node:net');
const {randomBytes,createHash}=require('node:crypto'),{spawn}=require('node:child_process');
const root=path.resolve(__dirname,'..');let child;
const quote=x=>'"'+x.replaceAll('"','""')+'"';
function fingerprint(db,tables){return tables.map(t=>{const rows=db.prepare('SELECT '+t.columns.map(quote).join(',')+' FROM '+quote(t.name)).all();const serialized=rows.map(r=>JSON.stringify(r)).sort();return {table:t.name,count:rows.length,hash:createHash('sha256').update(JSON.stringify(serialized)).digest('hex')};});}
(async()=>{try{
 if(process.argv[2]!=='--source'||!process.argv[3])throw new Error('Usage: node scripts/verify-existing-upgrade.cjs --source <database path>');
 const sourcePath=path.resolve(process.argv[3]);if(!fs.statSync(sourcePath).isFile())throw new Error('Choose an existing database file');
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'eot-existing-upgrade-')),copyPath=path.join(directory,'eastoftown.db'),originalBackup=path.join(directory,'before-upgrade.db');
 const source=new DatabaseSync(sourcePath,{readOnly:true});try{await backup(source,originalBackup);}finally{source.close();}
 const snapshot=new DatabaseSync(originalBackup,{readOnly:true});try{await backup(snapshot,copyPath);}finally{snapshot.close();}
 let copy=new DatabaseSync(copyPath,{readOnly:true});
 if(copy.prepare('PRAGMA quick_check').get().quick_check!=='ok')throw new Error('Source backup failed health check');
 const tables=copy.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name<>'schema_migrations' AND name NOT LIKE 'sqlite_%' ORDER BY name").all().map(t=>({name:t.name,columns:copy.prepare('PRAGMA table_info('+quote(t.name)+')').all().map(c=>c.name)}));
 const before=fingerprint(copy,tables),versions=copy.prepare('SELECT version,checksum FROM schema_migrations ORDER BY version').all();copy.close();
 const port=await new Promise((resolve,reject)=>{const server=net.createServer();server.on('error',reject);server.listen(0,'127.0.0.1',()=>{const p=server.address().port;server.close(()=>resolve(p));});});
 const token=randomBytes(32).toString('hex'),log=fs.openSync(path.join(directory,'upgrade.log'),'a');
 const bundled=path.join(root,'runtime','bin','java.exe'),java=fs.existsSync(bundled)?bundled:process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin','java.exe'):'java';
 const version=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8')).version;
 child=spawn(java,['-jar',path.join(root,'backend','target','pos-'+version+'.jar')],{windowsHide:true,stdio:['ignore',log,log],env:{...process.env,EOT_DATA_DIR:directory,EOT_API_TOKEN:token,EOT_PORT:String(port)}});fs.closeSync(log);
 let failure,ready=false;child.on('error',e=>{failure=e;});
 for(let i=0;i<120;i++){if(failure)throw failure;if(child.exitCode!==null)throw new Error('Copied database upgrade failed; inspect '+path.join(directory,'upgrade.log'));try{const response=await fetch('http://127.0.0.1:'+port+'/api/health',{headers:{'X-EOT-Token':token},signal:AbortSignal.timeout(1000)});if(response.ok && (await response.json()).database==='ok'){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,500));}
 if(!ready)throw new Error('Copied database startup timed out');
 await new Promise(resolve=>{child.once('exit',resolve);child.kill();});child=null;
 copy=new DatabaseSync(copyPath,{readOnly:true});try{
   const after=fingerprint(copy,tables);if(JSON.stringify(before)!==JSON.stringify(after))throw new Error('Upgrade changed an existing business record');
   if(copy.prepare('SELECT COUNT(*) AS n FROM schema_migrations').get().n!==6)throw new Error('Stage 4 migration did not complete');
   for(const v of versions)if(copy.prepare('SELECT checksum FROM schema_migrations WHERE version=?').get(v.version).checksum!==v.checksum)throw new Error('Upgrade rewrote a historical checksum');
   if(copy.prepare('PRAGMA foreign_key_check').all().length)throw new Error('Foreign-key check failed');
   console.log('PASS: copied existing database starts; all original table rows/values and migration checksums preserved; V006 applied; health and foreign keys valid.');
   console.log('Record counts: '+JSON.stringify(after.map(x=>({table:x.table,count:x.count}))));console.log('Retained original backup: '+originalBackup);console.log('Retained upgraded test copy: '+copyPath);console.log('Source database was opened read-only.');
 }finally{copy.close();}
}finally{if(child&&child.exitCode===null)await new Promise(resolve=>{child.once('exit',resolve);child.kill();});}})().catch(e=>{console.error(e.message);process.exitCode=1;});
