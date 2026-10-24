import React,{useEffect,useState} from 'react';
import {api,Variant} from './api';

export function Labels({admin}:{admin:boolean}){
 const [variants,setVariants]=useState<Variant[]>([]),[search,setSearch]=useState('');
 const [selected,setSelected]=useState<Record<string,number>>({}),[width,setWidth]=useState('80'),[height,setHeight]=useState('40');
 const [printers,setPrinters]=useState<string[]>([]),[printer,setPrinter]=useState(''),[preview,setPreview]=useState<any[]>([]);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 const [thermal,setThermal]=useState(false);
 const request={items:Object.entries(selected).map(([variantId,copies])=>({variantId,copies})),widthMm:thermal?80:Number(width),heightMm:Number(height),thermal};
 async function load(){try{setVariants(await api('GET','/variants?search='+encodeURIComponent(search)));}catch(e){setError(String(e));}}
 useEffect(()=>{
  load();Promise.all([api('GET','/labels/printers'),api('GET','/labels/settings')]).then(([list,settings])=>{
   setPrinters(list);for(const s of settings){if(s.key==='label.width')setWidth(s.value);if(s.key==='label.height')setHeight(s.value);if(s.key==='label.printer')setPrinter(s.value);if(s.key==='label.thermal')setThermal(s.value==='true');}
  }).catch(e=>setError(String(e)));
 },[]);
 async function action(kind:'preview'|'pdf'|'print'|'test'|'settings'){
  setBusy(true);setError('');setMessage('');try{
   if(kind==='preview')setPreview(await api('POST','/labels/preview',request));
   else if(kind==='pdf'){
    const result=await window.catalog.download({kind:'labels',labels:request});
    if(result.saved)setMessage('Barcode PDF saved. Print at actual size (100%).');
   }else if(kind==='settings'){
    await api('POST','/labels/settings',{widthMm:Number(width),heightMm:Number(height),printer,thermal});setMessage('Label dimensions, layout and printer preference saved.');
   }else{
    const result=await api('POST','/labels/print',{labels:{...request,items:request.items.length?request.items:[{variantId:'test',copies:1}]},printer,test:kind==='test'});setMessage(result.message);
   }
  }catch(e){setError(String(e));}finally{setBusy(false);}
 }
 return <>
  <div className="eyebrow">PRINT & IDENTIFY</div><h1>Barcode labels</h1>
  <p>Select variants and quantities. Stored barcodes are rendered as Code 128 and are never replaced when printed.</p>
  {error&&<div className="error" role="alert">{error}</div>}
  {message&&<div className="notice" role="status">{message}</div>}
  <div className="panel">
   <div className="mapping-grid">
    <label>Print layout<select aria-label="Print layout" disabled={busy} value={thermal?'thermal':'labels'} onChange={e=>{setThermal(e.target.value==='thermal');setPreview([]);}}><option value="labels">Individual barcode labels</option><option value="thermal">Thermal POS roll — 80 mm × automatic height</option></select></label>
    <label>Label width (mm)<input disabled={busy||thermal} type="number" min="35" max="110" value={thermal?'80':width} onChange={e=>{setWidth(e.target.value);setPreview([]);}}/></label>
    <label>Label height (mm)<input disabled={busy||thermal} type="number" min="25" max="100" value={height} onChange={e=>{setHeight(e.target.value);setPreview([]);}}/>{thermal&&<small>Roll height is calculated from the content.</small>}</label>
    <label>Windows printer<select disabled={busy} value={printer} onChange={e=>setPrinter(e.target.value)}><option value="">Select a printer</option>{printers.map(p=><option key={p}>{p}</option>)}</select></label>
   </div>
   <div className="actions">
    <button disabled={busy||!printer} onClick={()=>action('test')}>Test print</button>
    {admin&&<button disabled={busy} onClick={()=>action('settings')}>Save preferences</button>}
    <button disabled={busy} onClick={async()=>{try{setPrinters(await api('GET','/labels/printers'));}catch(e){setError(String(e));}}}>Refresh printers</button>
   </div>
   <p className="footnote">Use matching stock in the printer driver and actual size / 100% scaling. Long legacy barcodes may require wider labels. A submitted job still requires checking the printer output.</p>
   <p className="footnote">Thermal roll uses 80 mm paper with automatic height and 4 mm side margins. Long barcodes rotate vertically to stay readable. Preview, printing and PDF use the same layout.</p>
  </div>
  <form className="toolbar" onSubmit={e=>{e.preventDefault();load();}}>
   <input placeholder="Search product, SKU or barcode" aria-label="Label product search" value={search} onChange={e=>setSearch(e.target.value)}/><button>Search</button>
   <span>{Object.keys(selected).length} selected · {Object.values(selected).reduce((a,b)=>a+b,0)} copies</span>
  </form>
  <div className="table-wrap"><table><thead><tr><th>SELECT</th><th>VARIANT</th><th>SKU / BARCODE</th><th>COPIES</th></tr></thead><tbody>
   {variants.map(v=><tr key={v.id}>
    <td><input type="checkbox" aria-label={'Select '+v.sku} disabled={busy||!v.active} checked={v.id in selected} onChange={e=>{const next={...selected};if(e.target.checked)next[v.id]=1;else delete next[v.id];setSelected(next);setPreview([]);}}/></td>
    <td>{v.name}<small>{v.size} / {v.colour}{!v.active?' · Inactive':''}</small></td><td>{v.sku}<small className="mono">{v.barcode}</small></td>
    <td>{v.id in selected&&<input disabled={busy} type="number" aria-label={'Copies '+v.sku} min="1" max="100" value={selected[v.id]} onChange={e=>{setSelected({...selected,[v.id]:Number(e.target.value)});setPreview([]);}}/>}</td>
   </tr>)}
  </tbody></table></div>
  <div className="toolbar">
   <button disabled={busy||!request.items.length} onClick={()=>action('preview')}>Preview labels</button>
   <button disabled={busy||!preview.length} onClick={()=>action('pdf')}>Save PDF</button>
   <button className="primary" disabled={busy||!preview.length||!printer} onClick={()=>action('print')}>Print labels</button>
   <button disabled={busy} onClick={()=>{setSelected({});setPreview([]);}}>Clear selection</button>
  </div>
  <div className="label-grid">{preview.map(p=><article className="label-preview" style={{width:(thermal?80:Number(width))+'mm'}} key={p.variantId}>
   <img className="pdf-label" src={p.image} alt={'Code 128: '+p.barcode}/><small>{thermal?Object.values(selected).reduce((a,b)=>a+b,0):p.copies} copies · {p.sku}</small>
  </article>)}</div>
 </>;
}
