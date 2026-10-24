'use strict';
// Real Documents/User/Moderation and BFF. Only Ride payloads/failures are fixtures.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const {execFileSync} = require('node:child_process');
const fs = require('node:fs/promises');
const path = require('node:path');
const bff=process.env.BFF_REAL_URL, user=process.env.USER_REAL_URL, mod=process.env.MODERATION_REAL_URL;
const docs=process.env.DOCUMENTS_REAL_URL, fixture=process.env.FIXTURE_REAL_URL;
const password='Integration-password-123', ride='7c9e6679-7425-40de-944b-e07fc1f90ae7';
async function api(base, route, token, method='GET', body, extra={}) {
  return fetch(base+route,{method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{}),...extra},body:body?JSON.stringify(body):undefined});
}
async function mode(value) { assert.equal((await api(fixture,'/__document-fixture',null,'POST',{mode:value})).status,200); }
async function pdf(response, text) {
  assert.equal(response.status,200,await response.clone().text());
  assert.match(response.headers.get('Content-Type'),/^application\/pdf/);
  assert.equal(response.headers.get('Cache-Control'),'no-store');
  const bytes=Buffer.from(await response.arrayBuffer()); assert.equal(bytes.subarray(0,5).toString(),'%PDF-');
  const result=execFileSync('pdftotext',['-','-'],{input:bytes}).toString();
  assert.ok(result.includes(text),'PDF text must contain '+text);
  assert.ok(result.includes('Иван Петров'),'Cyrillic profile must survive PDF rendering');
  return result;
}
(async()=>{
  assert.equal((await api(user,'/api/users/register',null,'POST',{login:'documents.user',password,fullName:'Иван Петров',birthDate:'2000-01-01'})).status,201);
  const auth=await (await api(bff,'/api/auth/login',null,'POST',{login:'documents.user',password})).json();
  const token=auth.accessToken,id=auth.user.id;
  const admin=(await (await api(user,'/api/users/login',null,'POST',{login:'integration.admin',password})).json()).accessToken;
  const own={userId:id,docType:'USER_HISTORY',from:'2026-10-01',to:'2026-10-31'};
  const history=await pdf(await api(bff,'/api/documents/generate',token,'POST',own),'История поездок');
  assert.ok(history.includes('A123BC'));assert.ok(!history.includes('OUTSIDE'));
  await pdf(await api(bff,'/api/documents/generate',token,'POST',{docType:'RECEIPT',sourceId:ride}),'Чек об оплате поездки');
  assert.equal((await api(docs,'/api/documents/generate',token,'POST',{...own,userId:'00000000-0000-4000-8000-000000000000'})).status,403);
  assert.equal((await api(bff,'/api/documents/generate',token,'POST',{docType:'USER_BLOCKED'})).status,409);
  for(const [value,status] of [['unpaid',409],['foreign',403],['wrong-id',502],['missing-payment',502],['malformed',502],['404',404],['500',503]]) {
    await mode(value);
    assert.equal((await api(bff,'/api/documents/generate',token,'POST',{docType:'RIDE_PAYMENT',sourceId:ride})).status,status,value);
  }
  await mode('foreign');assert.equal((await api(bff,'/api/documents/generate',token,'POST',own)).status,502);
  await mode('valid');
  assert.equal((await api(docs,'/api/documents/generate',null,'POST',own)).status,401);
  assert.equal((await api(docs,'/api/documents/generate',token)).status,405);
  assert.equal((await api(docs,'/api/documents/generate',token,'POST',own,{'Content-Type':'text/plain'})).status,415);
  const bad=await fetch(docs+'/api/documents/generate',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:'{"userId":"'+id+'","docType":"USER_HISTORY","docType":"USER_BLOCKED"}'});
  assert.equal(bad.status,400);
  const huge=await fetch(docs+'/api/documents/generate',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:' '.repeat(65537)});
  assert.equal(huge.status,413);
  console.log('PASS: actual receipt/history PDFs contain Russian text; ownership, unpaid ride, malformed payloads and downstream errors enforced');

  assert.equal((await api(user,'/api/users/'+id+'/ban',admin,'POST',{reason:'RIDE_DANGEROUS'})).status,200);
  await pdf(await api(bff,'/api/documents/generate',token,'POST',{docType:'USER_BLOCKED'}),'Агрессивное / опасное вождение');
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_EXECUTABLE,headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
  try {
    const context=await browser.newContext({acceptDownloads:true});const page=await context.newPage();const errors=[];
    page.on('pageerror',err=>errors.push(err.message));
    await page.goto(bff+'/login.jsp');
    await page.locator('[name="login"]').fill('documents.user');await page.locator('[name="password"]').fill(password);
    await page.locator('#auth-form [type="submit"]').click();await page.locator('.profile-data').waitFor();
    await page.locator('[data-account-tab="documents"]').click();
    const dir=path.join('target','documents-downloads');await fs.mkdir(dir,{recursive:true});
    async function download(action,name,expected) {
      const waiting=page.waitForEvent('download');await action();const file=await waiting;
      const target=path.join(dir,name);await file.saveAs(target);
      const text=execFileSync('pdftotext',[target,'-']).toString();
      assert.ok(text.includes(expected));assert.ok(text.includes('Иван Петров'));
    }
    await download(()=>page.locator('[data-blocked-document]').click(),'blocked.pdf','Уведомление о блокировке');
    await page.locator('[name="from"]').fill('2026-10-01');await page.locator('[name="to"]').fill('2026-10-31');
    await download(()=>page.locator('#history-document-form [type="submit"]').click(),'history.pdf','История поездок');
    await page.locator('[data-account-tab="rides"]').click();
    await download(()=>page.locator('[data-receipt="'+ride+'"]').click(),'receipt.pdf','Чек об оплате поездки');
    assert.deepEqual(errors,[]);await context.close();
  } finally {await browser.close();}
  assert.equal((await api(user,'/api/users/'+id+'/unban',admin,'POST')).status,200);
  assert.equal((await api(bff,'/api/documents/generate',token,'POST',{docType:'USER_BLOCKED'})).status,409);
  assert.equal((await api(user,'/api/users/logout',token,'POST')).status,204);
  assert.equal((await api(docs,'/api/documents/generate',token,'POST',own)).status,401);
  assert.equal((await api(bff,'/api/documents/generate',token,'POST',own)).status,401);
  console.log('PASS: real Moderation ban/unban changes notice PDF; all three PDFs download through ScootX; direct Documents rejects revoked JWT');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
