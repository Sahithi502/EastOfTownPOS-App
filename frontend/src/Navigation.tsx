export function Navigation({admin,page,onSelect}:{admin:boolean;page:string;onSelect:(page:string)=>void}){
 const groups=[
  {name:'Sales',pages:['New bill','Invoice history','Customers']},
  {name:'Catalog and stock',pages:['Products','Categories','Inventory',...(admin?['Excel import']:[]),'Barcode labels']},
  ...(admin?[
   {name:'Insights',pages:['Dashboard','Reports']},
   {name:'Administration',pages:['Business controls','Billing settings']}
  ]:[])
 ];
 return <nav className="sidebar-navigation" aria-label="Main navigation">{groups.map(group=><details className="navigation-group" key={group.name} open>
  <summary>{group.name}</summary>
  <div className="navigation-children">{group.pages.map(item=><button key={item} className={page===item?'selected':''} aria-current={page===item?'page':undefined} onClick={()=>onSelect(item)}>{item}</button>)}</div>
 </details>)}</nav>;
}

export function pageGroup(page:string){
 if(['New bill','Invoice history','Customers'].includes(page))return 'Sales';
 if(['Products','Categories','Inventory','Excel import','Barcode labels'].includes(page))return 'Catalog and stock';
 if(['Dashboard','Reports'].includes(page))return 'Insights';
 return 'Administration';
}
