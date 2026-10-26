'use strict';
// Destructive fault checks run ONLY against a disposable Compose test project.
const assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const {randomUUID}=require('node:crypto');
const fs=require('node:fs/promises');
const path=require('node:path');
const {chromium}=require('playwright');
const project=process.env.STACK_TEST_PROJECT||'kicksharing-full-check';
assert.ok(project.startsWith('kicksharing-full-check'),'Use a disposable kicksharing-full-check* Compose project');
const root=path.resolve(__dirname,'..'), nonce=Date.now().toString(36), password='Learning-password-123';
const urls={bff:'http://localhost:8080',user:'http://localhost:8081',mod:'http://localhost:8082',scooter:'http://localhost:8083',ride:'http://localhost:8084',docs:'http://localhost:8085'};
function dc(...args){return execFileSync('docker',['compose','-p',project,...args],{cwd:root,encoding:'utf8',timeout:60000});}
function sql(db,query){return dc('exec','-T','postgres','psql','-U','kicksharing','-d',db,'-At','-v','ON_ERROR_STOP=1','-c',query).trim();}
async function api(service,route,token,method='GET',body){
 const response=await fetch(urls[service]+route,{method,signal:AbortSignal.timeout(12000),headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
 const bytes=Buffer.from(await response.arrayBuffer());let data;try{data=JSON.parse(bytes.toString());}catch{}
 return {status:response.status,data,bytes,headers:response.headers};
}
async function wait(check,message,ms=35000){const deadline=Date.now()+ms;let last;while(Date.now()<deadline){try{const result=await check();if(result)return result;}catch(error){last=error;}await new Promise(r=>setTimeout(r,400));}throw new Error(message+(last?' ('+last.message+')':''));}
async function register(login,fullName='Иван Петров'){
 const r=await api('bff','/api/auth/register',null,'POST',{login,password,fullName,birthDate:'2000-01-01'});assert.equal(r.status,201,JSON.stringify(r.data));
 const auth=await api('bff','/api/auth/login',null,'POST',{login,password});assert.equal(auth.status,200);return {...auth.data,login};
}
function pdf(result,expected){assert.equal(result.status,200,JSON.stringify(result.data));assert.match(result.headers.get('Content-Type'),/^application\/pdf/);assert.equal(result.headers.get('Cache-Control'),'no-store');const text=execFileSync('pdftotext',['-','-'],{input:result.bytes}).toString();assert.ok(text.includes(expected),text);return text;}
(async()=>{
 await wait(async()=>{const r=await api('bff','/api/users/exists?login=healthcheck');return r.status===200;},'BFF and full stack startup');
 const a=await register('stack.a.'+nonce),b=await register('stack.b.'+nonce),admin=await register('stack.admin.'+nonce);
 dc('exec','-T','user','java','-cp','/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*','ru.scooter.user.cli.PromoteAdmin',admin.login);
 assert.equal((await api('user','/api/users/validate',admin.accessToken)).status,401,'Promotion revokes old tokens');
 const adminAuth=(await api('bff','/api/auth/login',null,'POST',{login:admin.login,password})).data;
 assert.equal(adminAuth.user.role,'ADMIN');
 const fleet=await api('bff','/api/scooters',a.accessToken);assert.equal(fleet.status,200);assert.ok(Array.isArray(fleet.data));
 assert.ok(fleet.data.every(s=>s.numberPlate&&Number.isFinite(s.posX)&&Number.isFinite(s.posY)));
 const nearest=await api('bff','/api/scooters/nearest',a.accessToken,'POST',{posX:10.25,posY:14.75});assert.equal(nearest.status,200);
 assert.ok(nearest.data.every(s=>s.status==='READY'));assert.ok(nearest.data.every((s,i,list)=>i===0||list[i-1].distance<=s.distance));
 const ready=fleet.data.filter(s=>s.status==='READY');assert.ok(ready.length>=2);
 const plate=ready[0].numberPlate,otherPlate=ready[1].numberPlate;
 assert.equal((await api('scooter','/api/scooters/'+plate+'/reserve',a.accessToken,'POST',{rideId:randomUUID(),userId:a.user.id})).status,403);
 const rates=await api('bff','/api/rates',a.accessToken);assert.equal(rates.status,200);assert.equal(rates.data.find(r=>r.default).paymentMode,'EDUCATIONAL');
 const rateId=randomUUID();sql('ride',`INSERT INTO rates VALUES('${rateId}','Integration tariff',50,8,'RUB',false,true)`);
 const startBody={numberPlate:plate,rateId,posX:10.125,posY:15.25};
 const started=await Promise.all([api('bff','/api/rides',a.accessToken,'POST',startBody),api('bff','/api/rides',b.accessToken,'POST',startBody)]);
 assert.deepEqual(started.map(r=>r.status).sort(),[201,409]);
 const winner=started[0].status===201?a:b,loser=winner===a?b:a;
 const ride=started.find(r=>r.status===201).data;
 assert.equal((await api('ride','/api/rides/'+ride.id,loser.accessToken)).status,403);
 assert.equal((await api('bff','/api/rides',winner.accessToken,'POST',{numberPlate:otherPlate})).status,409);
 assert.equal((await api('bff','/api/scooters/'+otherPlate,winner.accessToken)).data.status,'READY');
 assert.equal((await api('bff','/api/rides/'+ride.id+'/pay',winner.accessToken,'POST',{})).status,409);
 assert.equal((await api('bff','/api/documents/generate',winner.accessToken,'POST',{docType:'RIDE_PAYMENT',sourceId:ride.id})).status,409);
 sql('ride',`UPDATE rates SET unlock_fee=99,price_per_minute=19 WHERE id='${rateId}'`);
 const coords={posX:18.125,posY:24.75};
 const finished=await api('bff','/api/rides/'+ride.id+'/finish',winner.accessToken,'PUT',coords);assert.equal(finished.status,200);assert.equal(finished.data.amount,58,'Tariff snapshot is preserved');
 const repeated=await api('bff','/api/rides/'+ride.id+'/finish',winner.accessToken,'PUT',coords);assert.equal(repeated.status,200);assert.equal(repeated.data.finishedAt,finished.data.finishedAt);
 const released=await api('bff','/api/scooters/'+plate,winner.accessToken);assert.equal(released.data.status,'READY');assert.equal(released.data.posX,coords.posX);assert.equal(released.data.posY,coords.posY);assert.equal(released.data.charge,ready[0].charge);
 const paid=await Promise.all([api('bff','/api/rides/'+ride.id+'/pay',winner.accessToken,'POST',{}),api('bff','/api/rides/'+ride.id+'/pay',winner.accessToken,'POST',{})]);
 assert.ok(paid.every(r=>r.status===200&&r.data.status==='PAID'));assert.equal(paid[0].data.paidAt,paid[1].data.paidAt);
 const receipt=pdf(await api('bff','/api/documents/generate',winner.accessToken,'POST',{docType:'RIDE_PAYMENT',sourceId:ride.id}),'Учебная оплата');assert.ok(receipt.includes('Иван Петров'));assert.ok(receipt.includes('58'));
 console.log('PASS: real fleet/nearest, concurrent reservation, one active ride, tariff snapshot, decimal finish, idempotent educational payment and Russian receipt');

 // The ban applies to start, while finishing an existing ride stays possible.
 const active=(await api('bff','/api/rides',a.accessToken,'POST',{numberPlate:plate}));assert.equal(active.status,201);
 assert.equal((await api('user','/api/users/'+a.user.id+'/ban',adminAuth.accessToken,'POST',{reason:'RIDE_DANGEROUS'})).status,200);
 assert.equal((await api('ride','/api/rides',a.accessToken,'POST',{numberPlate:otherPlate})).status,403);
 pdf(await api('bff','/api/documents/generate',a.accessToken,'POST',{docType:'USER_BLOCKED'}),'Агрессивное / опасное вождение');
 assert.equal((await api('bff','/api/rides/'+active.data.id+'/finish',a.accessToken,'PUT',{posX:20.5,posY:25.5})).status,200);
 assert.equal((await api('bff','/api/rides/'+active.data.id+'/pay',a.accessToken,'POST',{})).status,200);
 assert.equal((await api('user','/api/users/'+a.user.id+'/unban',adminAuth.accessToken,'POST')).status,200);
 assert.equal((await api('bff','/api/documents/generate',a.accessToken,'POST',{docType:'USER_BLOCKED'})).status,409);
 console.log('PASS: actual User↔Moderation ban/unban, notice PDF, blocked user can finish/pay but cannot start');

 // Lost connection at finish freezes its time/amount and survives Ride restart.
 const recovery=(await api('bff','/api/rides',a.accessToken,'POST',{numberPlate:plate}));assert.equal(recovery.status,201);
 dc('stop','scooter');
 assert.equal((await api('bff','/api/rides/'+recovery.data.id+'/finish',a.accessToken,'PUT',{posX:30.125,posY:40.25})).status,503);
 const frozen=sql('ride',`SELECT amount FROM rides WHERE id='${recovery.data.id}' AND status='FINISHING'`);assert.ok(frozen);
 dc('restart','ride');dc('start','scooter');
 const recovered=await wait(async()=>{const r=await api('ride','/api/rides/'+recovery.data.id,a.accessToken);return r.status===200&&r.data.status==='FINISHED'?r.data:null;},'Durable finish recovery');
 assert.equal(String(recovered.amount),String(Number(frozen)));
 await wait(async()=>{const r=await api('bff','/api/scooters/'+plate,a.accessToken);return r.status===200&&r.data?.status==='READY';},'Scooter API recovers after network reconnect');
 assert.equal((await api('bff','/api/rides/'+recovered.id+'/pay',a.accessToken,'POST',{})).status,200);
 // Finishing must not depend on the availability of Moderation.
 const modDown=(await api('bff','/api/rides',a.accessToken,'POST',{numberPlate:plate}));assert.equal(modDown.status,201);
 dc('stop','moderation');
 assert.equal((await api('bff','/api/rides/'+modDown.data.id+'/finish',a.accessToken,'PUT',{posX:30,posY:40})).status,200);
 dc('start','moderation');
 await wait(async()=>{const r=await api('mod','/api/moderation/reasons',adminAuth.accessToken);return r.status===200;},'Moderation restart');
 assert.equal((await api('bff','/api/rides/'+modDown.data.id+'/pay',a.accessToken,'POST',{})).status,200);
 dc('restart','user');await wait(async()=>{const r=await api('user','/api/users/validate',a.accessToken);return r.status===204;},'User restart and token persistence');
 assert.equal((await api('ride','/api/rides/'+ride.id,winner.accessToken)).data.paidAt,paid[0].data.paidAt);
 console.log('PASS: outage and restart recovery preserves scooter ownership, fare, paid history and JWTs; finish works while Moderation is down');

 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_EXECUTABLE,headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
 try {
  const context=await browser.newContext({acceptDownloads:true,viewport:{width:1440,height:1000}});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  const uiLogin='stack.ui.'+nonce;await page.goto(urls.bff+'/?auth=register');
  for(const [name,value]of Object.entries({login:uiLogin,password,confirm:password,fullName:'Иван Петров',birthDate:'2000-01-01'}))await page.locator('#auth-form [name="'+name+'"]').fill(value);
  const loginWait=page.waitForResponse(r=>r.url()===urls.bff+'/api/auth/login'&&r.request().method()==='POST');
  await page.locator('#auth-form [type="submit"]').click();const uiAuth=await (await loginWait).json();
  await page.locator('.profile-data').waitFor();await page.locator('.dialog-close').click();
  await page.locator('[data-scooter="'+plate+'"]').first().click();await page.locator('[data-start-ride]').click();
  await page.locator('#start-ride-form').waitFor();assert.ok((await page.locator('.booking-notice').innerText()).includes('Учебный режим'));
  async function fillCoords(selector){await page.locator(selector+' [name="posX"]').fill('35.125');await page.locator(selector+' [name="posY"]').fill('45.25');}
  await fillCoords('#start-ride-form');await page.locator('#start-ride-form [type="submit"]').click();
  await page.locator('[data-finish-ride]').click();await fillCoords('#finish-ride-form');await page.locator('#finish-ride-form [type="submit"]').click();
  await page.locator('[data-pay-ride]').click();assert.ok((await page.locator('.booking-notice').innerText()).includes('реальные деньги не списываются'));
  await page.locator('#pay-ride-form [type="submit"]').click();await page.locator('[data-receipt]').waitFor();
  await fs.mkdir(path.join(root,'target','full-stack-downloads'),{recursive:true});
  async function download(action,name,text){const pending=page.waitForEvent('download');await action();const d=await pending;const file=path.join(root,'target','full-stack-downloads',name);await d.saveAs(file);assert.ok(execFileSync('pdftotext',[file,'-']).toString().includes(text));}
  await download(()=>page.locator('[data-receipt]').click(),'receipt.pdf','Учебная оплата');
  await page.locator('[data-account-tab="documents"]').click();
  const today=new Date().toISOString().slice(0,10);await page.locator('[name="from"]').fill(today);await page.locator('[name="to"]').fill(today);
  await download(()=>page.locator('#history-document-form [type="submit"]').click(),'history.pdf','История поездок');
  const adminContext=await browser.newContext();const panel=await adminContext.newPage();
  await panel.goto(urls.bff+'/login.jsp');await panel.locator('[name="login"]').fill(admin.login);await panel.locator('[name="password"]').fill(password);await panel.locator('#auth-form [type="submit"]').click();
  await panel.waitForURL(urls.mod+'/admin/users.html');assert.equal(await panel.evaluate(()=>location.hash),'');
  const row=panel.locator('#users-tbody tr').filter({hasText:uiLogin});await row.getByRole('button',{name:'Заблокировать',exact:true}).click();
  await panel.locator('#ban-reason').selectOption('RIDE_DANGEROUS');await panel.locator('#ban-confirm').click();await row.getByRole('button',{name:'Разблокировать',exact:true}).waitFor();
  await page.locator('[data-account-tab="documents"]').click();await download(()=>page.locator('[data-blocked-document]').click(),'blocked.pdf','вождение');
  await page.locator('[data-logout]').click();await page.locator('#auth-form').waitFor();
  for(const [service,route,method,body]of [['user','/api/users/validate','GET'],['mod','/api/moderation/users/'+uiAuth.user.id+'/status','GET'],['scooter','/api/scooters','GET'],['ride','/api/rides','GET'],['docs','/api/documents/generate','POST',{userId:uiAuth.user.id,docType:'USER_BLOCKED'}],['bff','/api/users/me','GET']])assert.equal((await api(service,route,uiAuth.accessToken,method,body)).status,401,service+' must reject revoked JWT');
  assert.deepEqual(errors,[]);await context.close();await adminContext.close();
  console.log('PASS: real browser registration → fleet → start → finish → educational pay → three Russian PDFs, ADMIN panel/CORS and revocation across all six apps');
 } finally {await browser.close();}
 const pending=sql('documents',"SELECT count(*) FROM documents WHERE status='PENDING'");assert.equal(pending,'0');
 console.log('PASS: FULL STACK, all five microservices and BFF real, no HTTP fixtures');
})().catch(error=>{console.error(error.stack||error.message);process.exitCode=1;});
