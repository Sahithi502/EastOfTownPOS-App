export type Category={id:string;name:string};
export type Variant={id:string;product_id:string;name:string;category_id:string;category:string;sku:string;size:string;colour:string;barcode:string;cost_paise:number|null;selling_paise:number;current_stock:number;reorder_level:number;active:number;version:number};
export type User={id:string;username:string;role:'ADMIN'|'BILLING'};
declare global {interface Window {catalog:{request:(method:string,route:string,body?:unknown)=>Promise<any>;upload:()=>Promise<any>;download:(input:unknown)=>Promise<{saved:boolean}>;maintenance:(input:unknown)=>Promise<any>}}}
export const api=(method:string,route:string,body?:unknown)=>window.catalog.request(method,route,body);
export const money=(n:number)=>new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}).format(n/100);
export const localDate=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Kolkata',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
