import {Sale} from './billingApi';

const amount=(paise:number)=>new Intl.NumberFormat('en-US',{minimumFractionDigits:2,maximumFractionDigits:2}).format(paise/100);
const rs=(paise:number)=>'Rs. '+amount(paise);

export function ReceiptPaper({sale,width}:{sale:Sale;width:number}){
 const {invoice,items,payments}=sale,shop=JSON.parse(invoice.shop_snapshot||'{}');
 const name=shop.shopName||"East of Town Men's Fashion",east=/^East of Town(?: Men's Fashion)?$/i.test(name.trim());
 const reversed=!!sale.reversals?.length;
 return <article className={'receipt-preview receipt-paper'+(width===58?' receipt-narrow':'')} aria-label="Sales receipt preview">
  <header className="receipt-brand"><h3>{east?'EAST OF TOWN':name.toUpperCase()}</h3>{east&&<p className="receipt-tagline">MEN'S WEAR &amp; FASHION</p>}{shop.address&&<p>{shop.address}</p>}{shop.phone&&<p>Tel: {shop.phone}</p>}</header>
  <h4 className="receipt-caption">SALES RECEIPT</h4>
  <div className="receipt-meta"><p>Bill: {invoice.invoice_number}</p><p>Date: {new Date(invoice.created_at).toLocaleString('en-GB',{timeZone:'Asia/Kolkata',day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit',hourCycle:'h12'}).toUpperCase()}</p><p>Customer: {invoice.customer_name||'Guest'}</p>{invoice.customer_phone&&<p>Mobile: {invoice.customer_phone}</p>}{reversed&&<p><strong>Returns / cancellation recorded</strong></p>}</div>
  <table className="receipt-items" aria-label="Receipt items"><colgroup><col className="receipt-item-column"/><col className="receipt-quantity-column"/><col className="receipt-amount-column"/></colgroup><thead><tr><th scope="col">ITEM</th><th scope="col">QTY</th><th scope="col">AMOUNT</th></tr></thead><tbody>{items.map(item=>{
   const discount=item.discount_paise+item.bill_discount_paise;
   return <tr key={item.id}><td><span className="receipt-item-name">{item.product_name}</span><small>{item.size} / {item.colour} | {item.sku}</small><small>Unit: {rs(item.unit_price_paise)}</small>{discount>0&&<small>Discount: - {rs(discount)}</small>}{item.tax_paise>0&&<small>Tax {item.tax_rate_bps/100}%: {rs(item.tax_paise)}</small>}{(discount>0||item.tax_paise>0)&&<small>Line total: {rs(item.line_total_paise)}</small>}</td><td>{item.quantity}</td><td>{amount(item.quantity*item.unit_price_paise)}</td></tr>;
  })}</tbody></table>
  <dl className="receipt-summary"><div><dt>Subtotal</dt><dd>{rs(invoice.subtotal_paise)}</dd></div>{invoice.discount_paise>0&&<div><dt>Discount</dt><dd>- {rs(invoice.discount_paise)}</dd></div>}{invoice.tax_paise>0&&<div><dt>Tax</dt><dd>{rs(invoice.tax_paise)}</dd></div>}</dl>
  <div className="receipt-grand-total"><strong>TOTAL</strong><strong>{rs(invoice.total_paise)}</strong></div>
  <div className="receipt-payments"><p>{reversed?'Original payment: ':'Payment: '}{payments.length===0?'No payment due':payments.length===1?payments[0].method+' (Paid)':'Split (Paid)'}</p>{payments.length>1&&payments.map(payment=><p className="receipt-payment-row" key={payment.id}><span>{payment.method}</span><span>{rs(payment.amount_paise)}</span></p>)}</div>
  {reversed&&<section className="receipt-reversals"><h5>RECORDED REVERSALS</h5>{sale.reversals?.map(reversal=><p key={reversal.id}>{reversal.kind}: {rs(reversal.total_paise)} | {reversal.reason}</p>)}{sale.refunds?.map(refund=><p className="receipt-payment-row" key={refund.id}><span>Refund {refund.method}</span><span>{rs(refund.amount_paise)}</span></p>)}<small>Original sale totals above are preserved.</small></section>}
  <footer className="receipt-footer">{shop.thankYou||''}</footer>
 </article>;
}
