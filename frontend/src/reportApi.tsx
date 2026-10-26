import {api} from './api';
export type Filter={preset:string;from:string;to:string};
export const initialFilter:Filter={preset:'today',from:'',to:''};
export const query=(f:Filter)=>new URLSearchParams({preset:f.preset,...(f.preset==='custom'?{from:f.from,to:f.to}:{})}).toString();
export function DateFilter({value,onChange,busy}:{value:Filter;onChange:(f:Filter)=>void;busy:boolean}){return <div className="date-controls"><label>Date range<select aria-label="Date range" disabled={busy} value={value.preset} onChange={e=>onChange({...value,preset:e.target.value})}>{[['today','Today'],['yesterday','Yesterday'],['week','This week (Monday)'],['month','This month'],['year','This calendar year'],['financial-year','Financial year (April–March)'],['custom','Custom range']].map(([key,name])=><option key={key} value={key}>{name}</option>)}</select></label>{value.preset==='custom'&&<><label>From date<input required type="date" value={value.from} onChange={e=>onChange({...value,from:e.target.value})}/></label><label>To date<input required type="date" value={value.to} onChange={e=>onChange({...value,to:e.target.value})}/></label></>}</div>}
export const dateTime=(value:string|null)=>value?new Date(value).toLocaleString('en-IN',{timeZone:'Asia/Kolkata'}):'—';
export async function exportReport(type:string,format:string,filter:Filter,customerId?:string){return window.catalog.download({kind:'report',type,format,...filter,customerId});}
export const loadDashboard=(f:Filter)=>api('GET','/reports/dashboard?'+query(f));
