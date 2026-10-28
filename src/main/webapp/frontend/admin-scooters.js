(() => {
 'use strict';
 const ctx=document.body.dataset.ctx||'', base=ctx+'/api/admin/scooters';
 const message=document.getElementById('message'), rows=document.getElementById('scooters');
 let busy=false;
 function notify(text,error=false){message.textContent=text;message.dataset.error=String(error);}
 function setBusy(value){busy=value;document.querySelectorAll('button').forEach(b=>{b.disabled=value||b.dataset.locked==='true';});}
 async function request(path='',method='GET',body){
  const response=await fetch(base+path,{method,credentials:'same-origin',headers:{Accept:'application/json',...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
  const data=response.status===204?null:await response.json();
  if(!response.ok){if(response.status===401)location.assign(ctx+'/login.jsp');throw new Error(data?.error||'Не удалось выполнить запрос');}return data;
 }
 function cell(row,value){const td=document.createElement('td');td.textContent=String(value);row.append(td);return td;}
 const labels={READY:'Свободен',IN_RIDE:'В поездке',CHARGING:'На зарядке',REPAIR:'В ремонте'};
 async function load(){
  const fleet=await request();rows.replaceChildren();
  if(!fleet.length){const tr=document.createElement('tr'),td=cell(tr,'Самокатов пока нет');td.colSpan=6;rows.append(tr);}
  for(const scooter of fleet){
   const row=document.createElement('tr');cell(row,scooter.numberPlate);cell(row,scooter.charge+'%');cell(row,labels[scooter.status]||scooter.status);cell(row,scooter.posX);cell(row,scooter.posY);
   const actions=document.createElement('td');row.append(actions);
   for(const [title,method,path] of [['Зарядить до 100%','POST','/charge'],['Удалить','DELETE','']]){
    const button=document.createElement('button');button.type='button';button.textContent=title;button.dataset.locked=String(scooter.status==='IN_RIDE');button.disabled=scooter.status==='IN_RIDE'||busy;
    if(scooter.status==='IN_RIDE')button.title='Дождитесь завершения поездки';
    button.addEventListener('click',()=>execute(async()=>{
     if(method==='DELETE'&&!confirm('Удалить самокат '+scooter.numberPlate+'?'))return;
     await request('/'+encodeURIComponent(scooter.numberPlate)+path,method);await load();notify(method==='DELETE'?'Самокат удалён':'Самокат заряжен');
    }));actions.append(button);
   }
   rows.append(row);
  }
 }
 async function execute(action){if(busy)return;setBusy(true);try{await action();}catch(e){notify(e.message||'Нет связи с сервером',true);}finally{setBusy(false);}}
 document.getElementById('add-scooter').addEventListener('submit',event=>{event.preventDefault();const form=event.currentTarget;execute(async()=>{
  const fields=new FormData(form);await request('','POST',{numberPlate:fields.get('numberPlate'),posX:Number(fields.get('posX')),posY:Number(fields.get('posY'))});form.reset();await load();notify('Самокат добавлен');
 });});
 document.getElementById('refresh').addEventListener('click',()=>execute(async()=>{await load();notify('Список обновлён');}));
 execute(load);
})();
