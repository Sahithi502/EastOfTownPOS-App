import {api} from './api';
export type Customer={id:string;name:string;mobile:string;email?:string};
export type ReceiptConfig={shopName:string;address:string;phone:string;thankYou:string;invoicePrefix:string;billingDiscountBps:number;receiptWidthMm:number;printer:string;autoPrint:boolean};
export type Sale={invoice:any;items:any[];payments:any[];printError?:string};
export function paise(value:string):number{if(value.trim()==='')return 0;if(!/^\d{1,10}(\.\d{0,2})?$/.test(value))throw new Error('Enter a non-negative amount with up to two decimal places.');const [whole,fraction='']=value.split('.');return Number(whole+fraction.padEnd(2,'0'));}
export const rupees=(n:number)=>String(Math.floor(n/100))+'.'+String(n%100).padStart(2,'0');
export const invoiceApi=(search:string,from:string,to:string,amount:string)=>'/billing/invoices?search='+encodeURIComponent(search)+(from?'&from='+from:'')+(to?'&to='+to:'')+(amount?'&amount='+paise(amount):'');
export const receiptPrint=(id:string,config:ReceiptConfig)=>api('POST','/billing/invoices/'+id+'/print',{widthMm:config.receiptWidthMm,printer:config.printer});
