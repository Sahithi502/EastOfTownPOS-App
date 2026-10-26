import {useEffect,useState} from 'react';
import {api} from './api';

type Country={region:string;name:string;code:number};
export function PhoneInput({country,mobile,onCountry,onMobile}:{country:string;mobile:string;onCountry:(v:string)=>void;onMobile:(v:string)=>void}){
 const [countries,setCountries]=useState<Country[]>([{region:'IN',name:'India',code:91}]),[error,setError]=useState('');
 useEffect(()=>{api('GET','/customers/countries').then(setCountries).catch(e=>setError(String(e)))},[]);
 return <><label>Phone country / code<select aria-label="Phone country / code" value={country} onChange={e=>onCountry(e.target.value)}>{countries.map(c=><option key={c.region} value={c.region}>{c.name} (+{c.code})</option>)}</select></label><label>Mobile number<input required aria-label="Mobile number" inputMode="tel" maxLength={15} pattern={country==='IN'?'[6-9][0-9]{9}':'[0-9]{4,15}'} title={country==='IN'?'Enter exactly 10 digits, starting with 6, 7, 8 or 9.':'Enter the national mobile number, without its country code.'} value={mobile} onChange={e=>onMobile(e.target.value)}/><small>{country==='IN'?'India: exactly 10 digits, starting with 6, 7, 8 or 9.':'Enter the national mobile number; the selected country code is added when saved.'}</small></label>{error&&<p role="alert" className="error">Country list unavailable: {error}</p>}</>;
}
