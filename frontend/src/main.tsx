import React,{useEffect,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {api,User} from './api';
import {Catalog} from './Catalog';
import {ImportScreen} from './ImportScreen';
import {Labels} from './Labels';
import './style.css';
import './stage2.css';
import './label.css';

function App(){
 const [user,setUser]=useState<User|null>(null),[setup,setSetup]=useState(false),[ready,setReady]=useState(false),[page,setPage]=useState('Products');
 const [username,setUsername]=useState(''),[password,setPassword]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{api('GET','/auth/status').then(s=>{setSetup(s.needsSetup);setReady(true);}).catch(e=>setError(String(e)));},[]);
 async function login(e:React.FormEvent){e.preventDefault();setBusy(true);setError('');try{if(setup){await api('POST','/auth/setup',{username,password});setSetup(false);}const result=await api('POST','/auth/login',{username,password});setUser(result.user);setPassword('');}catch(e){setError(String(e));}finally{setBusy(false);}}
 const admin=user?.role==='ADMIN';
 if(!user)return <div className="login"><form className="login-card" onSubmit={login}><div className="brand">E / T</div><div className="eyebrow">EAST OF TOWN MEN’S FASHION</div><h1>{setup?'Create your administrator':'Welcome back'}</h1><p>{setup?'Set up the local owner account to authorize stock changes and imports. Keep the password in a safe place.':'Sign in to your local showroom workspace.'}</p>{error&&<div className="error" role="alert">{error}</div>}<label>Username<input autoFocus required minLength={3} maxLength={40} autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)}/></label><label>Password<input type="password" required minLength={setup?12:1} autoComplete={setup?'new-password':'current-password'} value={password} onChange={e=>setPassword(e.target.value)}/></label>{setup&&<small>Use at least 12 characters. No default password is provided.</small>}<button className="primary" disabled={busy||!ready}>{busy?'Please wait…':setup?'Create administrator and sign in':'Sign in'}</button></form></div>;
 return <div className="shell"><aside><div className="brand">E<span> / </span>T</div><h2>EAST OF TOWN</h2><small>MEN’S FASHION</small><div className="navlabel">WORKSPACE</div>{['Products','Categories','Inventory',...(admin?['Excel import']:[]),'Barcode labels',...(admin?['Users']:[])].map(p=><button key={p} className={page===p?'selected':''} onClick={()=>{setPage(p);setError('');}}>{p}</button>)}<div className="sidebar-bottom"><span className="dot"/> Local database<br/><small>{user.username} · {user.role}</small><button onClick={async()=>{await api('POST','/auth/logout');setUser(null);setPage('Products');}}>Sign out</button></div></aside><main><header><span>SHOWROOM / {page.toUpperCase()}</span><span>INR · Offline desktop</span></header><section>{page==='Excel import'?<ImportScreen/>:page==='Barcode labels'?<Labels admin={!!admin}/>:page==='Users'?<Users/>:<Catalog key={page} page={page} admin={!!admin}/>}</section></main></div>;
}
function Users(){
 const [username,setUsername]=useState(''),[password,setPassword]=useState(''),[role,setRole]=useState('BILLING'),[message,setMessage]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 return <><div className="eyebrow">ADMINISTRATION</div><h1>Local users</h1><p>Billing users can view the catalog, stock history and print labels. Administrators can import, edit products and adjust stock.</p><form className="panel user-form" onSubmit={async e=>{e.preventDefault();setBusy(true);setError('');try{await api('POST','/auth/users',{username,password,role});setMessage('User created. They can sign in on this computer.');setUsername('');setPassword('');}catch(e){setError(String(e));}finally{setBusy(false);}}}>{error&&<div className="error">{error}</div>}{message&&<div className="notice">{message}</div>}<label>Username<input required minLength={3} maxLength={40} value={username} onChange={e=>setUsername(e.target.value)}/></label><label>Password (at least 12 characters)<input required type="password" minLength={12} autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)}/></label><label>Role<select value={role} onChange={e=>setRole(e.target.value)}><option value="BILLING">Billing user</option><option value="ADMIN">Administrator</option></select></label><button disabled={busy} className="primary">Create user</button></form></>;
}
createRoot(document.getElementById('root')!).render(<App/>);
