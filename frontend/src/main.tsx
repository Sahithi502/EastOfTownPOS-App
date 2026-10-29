import {errorMessage} from './api';
import React,{useEffect,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {api,User} from './api';
import {Catalog} from './Catalog';
import {ImportScreen} from './ImportScreen';
import {Labels} from './Labels';
import {Billing} from './Billing';
import {Invoices} from './Invoices';
import {BillingSettings} from './BillingSettings';
import {Dashboard} from './Dashboard';
import {Customers} from './Customers';
import {Reports} from './Reports';
import {Controls} from './Controls';
import {Navigation,pageGroup} from './Navigation';
import './style.css';
import './stage2.css';
import './label.css';
import './billing.css';
import './reports.css';
import './navigation.css';

function App(){
 const [user,setUser]=useState<User|null>(null),[setup,setSetup]=useState(false),[ready,setReady]=useState(false),[page,setPage]=useState('Products');
 const [username,setUsername]=useState(''),[password,setPassword]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false),[recovering,setRecovering]=useState(false),[recoveryCode,setRecoveryCode]=useState(''),[newPassword,setNewPassword]=useState(''),[recoveryNotice,setRecoveryNotice]=useState('');
 useEffect(()=>{api('GET','/auth/status').then(s=>{setSetup(s.needsSetup);setReady(true);}).catch(e=>setError(errorMessage(e)));},[]);
 async function login(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');try{if(setup){const result=await api('POST','/auth/setup',{username,password});setRecoveryNotice(`Save this recovery code securely: ${result.recoveryCode}`);setSetup(false);}const result=await api('POST','/auth/login',{username,password});setUser(result.user);setPassword('');}catch(e){setError(errorMessage(e));}finally{setBusy(false);}}
 async function recover(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');try{await api('POST','/auth/recover',{username,recoveryCode,newPassword});setRecoveryNotice('Password reset successfully. Sign in with your new password.');setRecovering(false);setPassword('');setRecoveryCode('');setNewPassword('');}catch(e){setError(errorMessage(e));}finally{setBusy(false);}}
 const admin=user?.role==='ADMIN';
 if(!user)return <div className={`login ${recovering?'login-recovery':''}`}><form className="login-card" onSubmit={recovering?recover:login}><div className="brand">E / T</div><div className="eyebrow">{recovering?'SECURE ACCOUNT RECOVERY':'EAST OF TOWN MEN’S FASHION'}</div><h1>{setup?'Create your administrator':recovering?'Recover administrator password':'Welcome back'}</h1><p>{setup?'Set up the local owner account. Save the recovery code shown after setup.':recovering?'Use the administrator recovery code created during setup or generated in Business controls → User access.':'Sign in to your local showroom workspace.'}</p>{error&&<div className="error" role="alert">{error}</div>}{recoveryNotice&&<div className="notice" role="status">{recoveryNotice}</div>}<label>Username<input autoFocus required minLength={3} maxLength={40} autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)}/></label>{recovering?<><label>Administrator recovery code<input required minLength={16} autoComplete="one-time-code" value={recoveryCode} onChange={e=>setRecoveryCode(e.target.value)}/></label><label>New password<input type="password" required minLength={12} autoComplete="new-password" value={newPassword} onChange={e=>setNewPassword(e.target.value)}/></label></>:<><label>Password<input type="password" required minLength={setup?12:1} autoComplete={setup?'new-password':'current-password'} value={password} onChange={e=>setPassword(e.target.value)}/></label>{setup&&<small>Use at least 12 characters. No default password is provided.</small>}</>}<button className="primary" disabled={busy||!ready}>{busy?'Please wait…':setup?'Create administrator and sign in':recovering?'Reset password':'Sign in'}</button>{!setup&&<button type="button" className="link-button" onClick={()=>{setRecovering(!recovering);setError('');setRecoveryNotice('');}}>{recovering?'Back to sign in':'Forgot administrator password?'}</button>}</form></div>;
 return <div className="shell"><aside><div className="brand">E<span> / </span>T</div><h2>EAST OF TOWN</h2><small>MEN’S FASHION</small><Navigation admin={!!admin} page={page} onSelect={p=>{setPage(p);setError('');}}/><div className="sidebar-bottom"><span className="dot"/> Local database<br/><small>{user.username} · {user.role}</small><button onClick={async()=>{try{await api('POST','/auth/logout');setUser(null);setPage('Products');setError('');}catch(e){setError(errorMessage(e));}}}>Sign out</button></div></aside><main><header><span>SHOWROOM / {pageGroup(page).toUpperCase()} / {page.toUpperCase()}</span><span>INR · Offline desktop</span></header><section>{error&&<div role="alert" className="error">{error}</div>}{page==='Business controls'?<Controls/>:page==='Dashboard'?<Dashboard/>:page==='Customers'?<Customers admin={!!admin}/>:page==='Reports'?<Reports/>:page==='New bill'?<Billing userId={user.id}/>:page==='Invoice history'?<Invoices/>:page==='Billing settings'?<BillingSettings/>:page==='Excel import'?<ImportScreen/>:page==='Barcode labels'?<Labels admin={!!admin}/>:<Catalog key={page} page={page} admin={!!admin}/>}</section></main></div>;
}
createRoot(document.getElementById('root')!).render(<App/>);
