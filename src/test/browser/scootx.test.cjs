const { test, before, after, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
let server, browser, context, page, base, fixture, errors, resourceFailures;

before(async () => {
  const cp = ['target/test-classes','target/classes',fs.readFileSync('target/test-classpath.txt','utf8').trim()].join(path.delimiter);
  const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME,'bin/java') : 'java';
  server = spawn(java, ['--add-opens=java.base/java.io=ALL-UNNAMED','--add-opens=java.base/java.lang=ALL-UNNAMED','--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED','-Dbff.test.context=' + (process.env.BFF_TEST_CONTEXT ?? '/scooter-bff'),'-cp',cp,'bff.BrowserTestServer']);
  const log = fs.createWriteStream('target/browser-server.log'); server.stderr.pipe(log);
  await new Promise((resolve,reject) => {
    const timer = setTimeout(() => reject(new Error('Browser WAR did not start; see target/browser-server.log')), 45000);
    let pending = '';
    server.stdout.on('data', chunk => {
      pending += chunk.toString();
      const lines = pending.split('\n'); pending = lines.pop();
      for (const line of lines) {
        if (line.startsWith('SCOOTX_TEST_URL=')) base = line.slice('SCOOTX_TEST_URL='.length).trim();
        if (line.startsWith('SCOOTX_FIXTURE_URL=')) fixture = line.slice('SCOOTX_FIXTURE_URL='.length).trim();
      }
      if (base && fixture) { clearTimeout(timer); resolve(); }
    });
    server.once('exit', code => { clearTimeout(timer); reject(new Error(`Browser server exited ${code}; see target/browser-server.log`)); });
  });
  browser = await chromium.launch({headless:true, ...(process.env.CHROMIUM_EXECUTABLE ? {executablePath:process.env.CHROMIUM_EXECUTABLE} : {}), args:['--no-sandbox','--disable-dev-shm-usage']});
});
after(async () => {
  if (browser) await browser.close();
  if (server && server.exitCode === null) {
    server.kill('SIGTERM');
    await Promise.race([new Promise(resolve => server.once('exit',resolve)),new Promise(resolve => setTimeout(() => {server.kill('SIGKILL');resolve();},8000))]);
  }
});
async function settings(value, patch = false) {
  const response = await fetch(fixture + (patch ? '/__patch' : '/__fixture'), {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(value)});
  assert.equal(response.status,204);
}
async function calls() { return (await fetch(fixture + '/__calls')).json(); }
beforeEach(async () => {
  await settings({});
  context = await browser.newContext({viewport:{width:1440,height:1000},acceptDownloads:true,permissions:['geolocation'],geolocation:{longitude:10.5,latitude:24.5}});
  page = await context.newPage(); errors = []; resourceFailures = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.url().includes('/frontend/') && response.status() >= 400) resourceFailures.push(response.url()); });
});
afterEach(async () => {
  try { assert.deepEqual(errors,[], 'No uncaught browser exceptions'); assert.deepEqual(resourceFailures,[], 'All static resources must load'); }
  finally { if (context) await context.close(); }
});
async function openAuth(mode = 'login') {
  await page.goto(base + '?auth=' + mode);
  await page.locator('#auth-form').waitFor();
}
async function login() {
  await openAuth();
  await page.locator('[name="login"]').fill('ivan');
  await page.locator('[name="password"]').fill('password123');
  await page.locator('#auth-form [type="submit"]').click();
  await page.locator('.profile-data').waitFor();
}
async function checkAutomaticCoordinates(form) {
  assert.equal(await page.locator(form+' [name="posX"],'+form+' [name="posY"]').count(),0);
}
async function startRide() {
  await page.locator('.dialog-close').click();
  await page.locator('[data-scooter]').first().click();
  await page.locator('[data-start-ride]').click();
  await checkAutomaticCoordinates('#start-ride-form');
  await page.locator('#start-ride-form [type="submit"]').click();
  await page.locator('[data-finish-ride]').waitFor();
}
async function finishRide() {
  await page.locator('[data-finish-ride]').click();
  await checkAutomaticCoordinates('#finish-ride-form');
  await page.locator('#finish-ride-form [type="submit"]').click();
  await page.locator('[data-pay-ride]').waitFor();
}

test('anonymous landing loads branding, fonts and assets without fabricated catalog', async () => {
  await page.goto(base);
  await page.waitForFunction(() => document.querySelector('#catalog-message').textContent.includes('Войдите'));
  assert.equal(await page.title(),'ScootX — аренда самокатов');
  assert.equal(await page.locator('.product').count(),0);
  assert.equal(await page.locator('script[src*="demo-storage"]').count(),0);
  await page.screenshot({path:'target/scootx-home.png',fullPage:true});
});
test('real DOM registration → login → start → finish → pay → receipt → logout through BFF', async () => {
  await openAuth('register');
  for (const [name,value] of Object.entries({login:'ivan',password:'password123',confirm:'password123',fullName:'Иван Петров',birthDate:'2000-05-17'})) await page.locator(`[name="${name}"]`).fill(value);
  await page.locator('#auth-form [type="submit"]').click();
  await page.locator('.profile-data').waitFor();
  assert.ok((await context.cookies()).some(c => c.name === 'JSESSIONID' && c.httpOnly));
  await startRide();
  assert.match(await page.locator('.ride-item').innerText(),/Сумма ещё не рассчитана/);
  await finishRide();
  assert.match(await page.locator('.ride-item').innerText(),/123[,.]45/);
  await page.locator('[data-pay-ride]').click();
  await page.locator('#pay-ride-form [type="submit"]').click();
  await page.locator('[data-receipt]').waitFor();
  const downloaded = page.waitForEvent('download'); await page.locator('[data-receipt]').click();
  assert.equal((await downloaded).suggestedFilename(),'document.pdf');
  await page.screenshot({path:'target/scootx-account.png'});
  await page.locator('[data-logout]').click();
  await page.locator('#auth-form').waitFor();
  assert.equal(await page.locator('.product').count(),0);
  const operations = await calls();
  for (const [method,route] of [['POST','/api/users/register'],['POST','/api/users/login'],['POST','/api/rides'],['PUT','/finish'],['POST','/pay'],['POST','/api/documents/generate'],['POST','/api/users/logout']])
    assert.ok(operations.some(c => c.method === method && c.path.endsWith(route)),method + ' ' + route);
});
test('UTF-8 password limit blocks registration before any API request', async () => {
  await openAuth('register');
  for (const [name,value] of Object.entries({login:'ivan',password:'я'.repeat(37),confirm:'я'.repeat(37),fullName:'Иван Петров',birthDate:'2000-05-17'})) await page.locator(`[name="${name}"]`).fill(value);
  await page.locator('#auth-form [type="submit"]').click();
  await page.locator('.form-error:not([hidden])').waitFor();
  assert.match(await page.locator('.form-error').innerText(),/72 байта/);
  assert.ok(!(await calls()).some(c => c.path === '/api/users/register'));
});
test('registration displays User API duplicate login error', async () => {
  await settings({registerStatus:409}); await openAuth('register');
  for (const [name,value] of Object.entries({login:'ivan',password:'password123',confirm:'password123',fullName:'Иван Петров',birthDate:'2000-05-17'})) await page.locator(`[name="${name}"]`).fill(value);
  await page.locator('#auth-form [type="submit"]').click();
  await page.locator('.form-error:not([hidden])').waitFor();
  assert.equal(await page.locator('.form-error').innerText(),'Логин уже занят');
});
test('failed payment remains retryable and cannot produce a paid receipt', async () => {
  await settings({payStatus:503}); await login(); await startRide(); await finishRide();
  await page.locator('[data-pay-ride]').click();
  await page.locator('#pay-ride-form [type="submit"]').click();
  await page.locator('.form-error:not([hidden])').waitFor();
  assert.match(await page.locator('.form-error').innerText(),/503/);
  assert.equal(await page.locator('[data-receipt]').count(),0);
  assert.equal(await page.locator('#pay-ride-form [type="submit"]').isEnabled(),true);
  await settings({payStatus:200},true);
  await page.locator('#pay-ride-form [type="submit"]').click();
  await page.locator('[data-receipt]').waitFor();
});
test('expired session clears private views and reopens authentication', async () => {
  await login(); await settings({validateStatus:401},true);
  await page.locator('[data-account-tab="rides"]').click();
  await page.locator('#auth-form').waitFor();
  assert.equal(await page.locator('.profile-data').count(),0);
  assert.equal(await page.locator('.product').count(),0);
});
test('catalog failure is visible and never replaced by demo scooters', async () => {
  await settings({catalogStatus:503}); await login();
  assert.equal(await page.locator('.product').count(),0);
  assert.match(await page.locator('#catalog-message').innerText(),/503/);
  await settings({catalogStatus:200},true); await page.locator('.dialog-close').click();
  await page.locator('#refresh-fleet').click(); await page.locator('.product').waitFor();
});
test('blocked user may login but cannot start a ride', async () => {
  await settings({blocked:true}); await login();
  await page.locator('.dialog-close').click(); await page.locator('[data-scooter]').click();
  await page.locator('[data-start-ride]').click();
  await page.locator('.profile-data').waitFor();
  assert.match(await page.locator('.profile-data').innerText(),/заблокирован/);
  assert.ok(!(await calls()).some(c => c.method === 'POST' && c.path === '/api/rides'));
});
test('malicious server strings are escaped, optional numeric fields can be absent', async () => {
  await settings({plate:'A" onmouseover="window.__xss=1',missingFields:true}); await login();
  assert.equal(await page.locator('[onmouseover]').count(),0);
  assert.equal(await page.evaluate(() => window.__xss),undefined);
  assert.match(await page.locator('.product').innerText(),/Нет данных/);
  await page.locator('.dialog-close').click(); await page.locator('[data-scooter]').click();
  await page.locator('.dialog-product').waitFor();
  assert.equal(await page.locator('[onmouseover]').count(),0);
});
test('ADMIN goes to configured Moderation panel with token in fragment only', async () => {
  await settings({role:'ADMIN'}); await openAuth();
  await page.locator('[name="login"]').fill('admin'); await page.locator('[name="password"]').fill('password123');
  await page.locator('#auth-form [type="submit"]').click();
  await page.waitForURL('**/admin/users.html#accessToken=*');
  const url = new URL(page.url()); assert.equal(url.search,''); assert.equal(url.hash,'#accessToken=browser-test-token');
});
test('favorites stay on device and never contain credentials', async () => {
  await login(); await page.locator('.dialog-close').click(); await page.locator('[data-favorite]').click();
  assert.equal(await page.locator('[data-favorite]').getAttribute('aria-pressed'),'true');
  const stored = await page.evaluate(() => Object.entries(localStorage));
  assert.equal(stored.length,1); assert.ok(stored[0][0].startsWith('scootx:favorites:')); assert.equal(stored[0][1],'["A123BC"]');
  await page.reload(); await page.waitForFunction(() => document.querySelector('[data-favorite]')?.getAttribute('aria-pressed') === 'true');
});
test('history PDF and nearest search use actual API paths with automatic GPS', async () => {
  await login(); await page.locator('[data-account-tab="documents"]').click();
  await page.locator('[name="from"]').fill('2026-10-01'); await page.locator('[name="to"]').fill('2026-10-20');
  const downloaded = page.waitForEvent('download'); await page.locator('#history-document-form [type="submit"]').click();
  assert.equal((await downloaded).suggestedFilename(),'document.pdf');
  await page.locator('.dialog-close').click();
  assert.equal(await page.locator('#nearest-fleet').count(), 0);
  await page.locator('#refresh-fleet').click();
  await page.waitForFunction(() => !document.querySelector('#details').open);
  assert.match(await page.locator('#catalog-message').innerText(),/сначала ближайшие/);
  assert.ok((await calls()).some(c => c.method === 'POST' && c.path === '/api/scooters/nearest'));
});
test('registered account is not registered twice when subsequent login fails', async () => {
  await settings({loginStatus:503}); await openAuth('register');
  for (const [name,value] of Object.entries({login:'ivan',password:'password123',confirm:'password123',fullName:'Иван Петров',birthDate:'2000-05-17'})) await page.locator(`[name="${name}"]`).fill(value);
  await page.locator('#auth-form [type="submit"]').click();
  await page.waitForFunction(() => document.querySelector('#auth-form')?.dataset.mode === 'login');
  assert.match(await page.locator('.form-error').innerText(),/Аккаунт создан/);
  assert.equal((await calls()).filter(c => c.path === '/api/users/register').length,1);
});
test('failed token revocation clears private UI but shows an error, never success', async () => {
  await settings({logoutStatus:503}); await login();
  await page.locator('[data-logout]').click(); await page.locator('#auth-form').waitFor();
  assert.match(await page.locator('#toast').innerText(),/не подтвердил отзыв/);
  assert.equal(await page.locator('.profile-data').count(),0);
  assert.equal(await page.locator('.product').count(),0);
});
test('successful login clears auth URL and session survives reload', async () => {
  await login();
  assert.equal(new URL(page.url()).searchParams.has('auth'),false);
  await page.reload();await page.locator('.product').first().waitFor();
  assert.equal(await page.locator('#auth-form').count(),0);
  await page.locator('#account-button').click();await page.locator('.profile-data').waitFor();
});
test('late document history cannot reopen a closed account dialog', async () => {
  await login();
  await page.route(/\/api\/documents\?/,async route=>{await new Promise(resolve=>setTimeout(resolve,300));await route.continue();});
  const loading=page.waitForResponse(response=>response.url().includes('/api/documents?'));
  await page.locator('[data-account-tab="documents"]').click();await page.locator('#history-document-form').waitFor();await page.locator('.dialog-close').click();
  await (await loading).finished();await page.waitForTimeout(100);
  assert.equal(await page.locator('#details').evaluate(dialog=>dialog.open),false);
});
test('closing the account while navigation refreshes cancels the late navigation', async () => {
  await login();
  await page.route(/\/api\/users\/me$/,async route=>{await new Promise(resolve=>setTimeout(resolve,300));await route.continue();});
  const loading=page.waitForResponse(response=>response.url().endsWith('/api/users/me'));
  await page.locator('[data-account-tab="rides"]').click();await page.locator('.dialog-close').click();
  await (await loading).finished();await page.waitForTimeout(100);
  assert.equal(await page.locator('#details').evaluate(dialog=>dialog.open),false);
});

test('GPS automatically queries nearest with longitude X and latitude Y, no manual coordinate fields', async()=>{
 await context.setGeolocation({longitude:37.618423,latitude:55.751244});
 const positions=[];
 await page.route('**/api/scooters/nearest',route=>{positions.push(route.request().postDataJSON());return route.continue();});
 await login();assert.ok(positions.length>=1);assert.deepEqual(positions[0],{posX:37.618423,posY:55.751244});
 assert.equal(await page.locator('[name="posX"],[name="posY"]').count(),0);
 await startRide();assert.doesNotMatch(await page.locator('body').innerText(),/Учебный режим|Учебная оплата/);
});
test('denied geolocation shows real catalog without distance or invented nearest requests',async()=>{
 await context.addInitScript(()=>Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition(ok,fail){fail({code:1});}}}));
 await login();
 assert.match(await page.locator('#catalog-message').innerText(),/Разрешите доступ/);
 assert.equal((await calls()).filter(c=>c.path==='/api/scooters/nearest').length,0);
 assert.equal(await page.locator('.product').count(),1);
 assert.equal(await page.locator('.range-row').count(),0);
 assert.equal(await page.locator('#retry-location').isVisible(),true);
 assert.ok((await calls()).some(c=>c.path==='/api/scooters' && c.method==='GET'));
});
test('late GPS cannot start a ride after its form is closed',async()=>{
 await context.addInitScript(()=>Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition(ok,fail,opts){setTimeout(()=>ok({coords:{longitude:37.6,latitude:55.7}}),opts.maximumAge===0?500:0);}}}));
 await login();await page.locator('.dialog-close').click();await page.locator('[data-scooter]').first().click();await page.locator('[data-start-ride]').click();await page.locator('#start-ride-form').waitFor();
 await page.locator('#start-ride-form [type="submit"]').click();await page.locator('.dialog-close').click();await page.waitForTimeout(700);
 assert.equal((await calls()).filter(c=>c.path==='/api/rides'&&c.method==='POST').length,0);
 assert.equal(await page.locator('#details').evaluate(d=>d.open),false);
});
test('finish retries preserve the first GPS position after a service error',async()=>{
 await login();await startRide();await page.locator('[data-finish-ride]').click();await page.locator('#finish-ride-form').waitFor();
 await settings({finishStatus:503},true);
 const positions=[];
 await page.route('**/api/rides/*/finish',route=>{positions.push(route.request().postDataJSON());return route.continue();});
 await page.locator('#finish-ride-form [type="submit"]').click();await page.locator('#finish-ride-form .form-error:not([hidden])').waitFor();
 await context.setGeolocation({longitude:37.6,latitude:55.7});await settings({finishStatus:200},true);
 await page.locator('#finish-ride-form [type="submit"]').click();await page.locator('[data-pay-ride]').waitFor();
 assert.equal(positions.length,2);assert.deepEqual(positions[0],positions[1]);
});

test('finished ride review survives refresh, can be edited and renders comments as text', async () => {
  await login();
  // Start and finish through real UI handlers and the packaged BFF proxy.
  await page.locator('.dialog-close').click();
  await page.locator('[data-scooter]').first().click();
  await page.locator('[data-start-ride]').click();
  await page.locator('#start-ride-form [type="submit"]').click();
  await page.locator('[data-finish-ride]').click();
  await page.locator('#finish-ride-form [type="submit"]').click();
  await page.locator('[data-review-ride]').click();
  await page.locator('[name="rideRating"]').selectOption('5');
  await page.locator('[name="scooterRating"]').selectOption('4');
  await page.locator('[name="rideComment"]').fill('Отличная поездка');
  await page.locator('[name="scooterComment"]').fill('<img src=x onerror=alert(1)>');
  await page.locator('#review-ride-form [type="submit"]').click();
  await page.locator('.ride-review').waitFor();
  assert.match(await page.locator('.ride-review').innerText(), /Отличная поездка/);
  assert.equal(await page.locator('.ride-review img').count(),0);
  const request = (await calls()).find(c => c.method === 'PUT' && c.path.endsWith('/review'));
  assert.ok(request);
  assert.match(await page.locator('.ride-review').innerText(), /Оценка поездки: 5\/5/);
  await page.locator('[data-review-ride]').click();
  assert.equal(await page.locator('[name="scooterRating"]').inputValue(),'4');
  await page.locator('[name="rideComment"]').fill('Обновлённый отзыв');
  await page.locator('#review-ride-form [type="submit"]').click();
  assert.match(await page.locator('.ride-review').innerText(), /Обновлённый отзыв/);
});

test('location denial still permits a ride using the selected scooter position and confirmed saved finish', async () => {
  await context.addInitScript(() => Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition(ok,fail){fail({code:1});}}}));
  await login();
  const starts=[], finishes=[];
  await page.route(/\/api\/rides$/,route=>{if(route.request().method()==='POST')starts.push(route.request().postDataJSON());return route.continue();});
  await page.route('**/api/rides/*/finish',route=>{finishes.push(route.request().postDataJSON());return route.continue();});
  await startRide();
  assert.equal(starts.length,1);assert.deepEqual({posX:starts[0].posX,posY:starts[0].posY},{posX:10,posY:24});
  await page.locator('[data-finish-ride]').click();
  await page.locator('#finish-ride-form [type="submit"]').click();
  await page.locator('[data-finish-saved]').waitFor();
  assert.equal(finishes.length,0);
  page.once('dialog',dialog=>dialog.dismiss());
  await page.locator('[data-finish-saved]').click();
  assert.equal(finishes.length,0);
  page.once('dialog',dialog=>dialog.accept());
  await page.locator('[data-finish-saved]').click();
  await page.locator('[data-pay-ride]').waitFor();
  assert.deepEqual(finishes,[{posX:10,posY:24}]);
});
test('location retry recovers nearby catalog after permission is granted', async () => {
  await context.addInitScript(() => {
    window.geoEnabled=false;
    Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition(ok,fail){window.geoEnabled ? ok({coords:{longitude:37.6,latitude:55.7}}) : fail({code:1});}}});
  });
  await login();await page.locator('.dialog-close').click();
  await page.locator('#retry-location').waitFor();
  await page.evaluate(()=>{window.geoEnabled=true;});
  await page.locator('#retry-location').click();
  await page.waitForFunction(()=>document.querySelector('#retry-location').hidden);
  assert.match(await page.locator('#catalog-message').innerText(),/сначала ближайшие/);
  assert.ok((await calls()).some(c=>c.path==='/api/scooters/nearest'));
});
test('unavailable location falls back to catalog while a catalog outage remains an error', async () => {
  await context.addInitScript(() => Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition(ok,fail){fail({code:3});}}}));
  await login();
  assert.equal(await page.locator('.product').count(),1);
  assert.match(await page.locator('#catalog-message').innerText(),/Истекло время/);
  await settings({catalogStatus:503},true);
  await page.locator('.dialog-close').click();await page.locator('#refresh-fleet').click();
  await page.waitForFunction(()=>document.querySelector('#catalog-message').textContent.includes('503'));
  assert.equal(await page.locator('.product').count(),0);
});

test('public homepage uses saved reviews, including edits, and escapes their comments', async () => {
  await login();await startRide();await finishRide();
  await page.locator('[data-review-ride]').click();
  await page.locator('[name="rideRating"]').selectOption('3');
  await page.locator('[name="scooterRating"]').selectOption('4');
  await page.locator('[name="rideComment"]').fill('Публичный отзыв <img src=x onerror=alert(1)>');
  await page.locator('[name="scooterComment"]').fill('Хороший самокат');
  await page.locator('#review-ride-form [type="submit"]').click();await page.locator('.ride-review').waitFor();
  await page.locator('.dialog-close').click();
  assert.match(await page.locator('#review-grid').innerText(),/Публичный отзыв/);
  assert.match(await page.locator('#review-grid').innerText(),/Поездка: 3\/5/);
  assert.equal(await page.locator('#review-grid img').count(),0);
  await page.locator('#account-button').click();await page.locator('.profile-data').waitFor();await page.locator('.account-nav [data-account-tab="rides"]').click();
  await page.locator('[data-review-ride]').click();await page.locator('[name="rideComment"]').fill('Изменённый публичный отзыв');
  await page.locator('#review-ride-form [type="submit"]').click();await page.locator('.ride-review').waitFor();await page.locator('.dialog-close').click();
  assert.match(await page.locator('#review-grid').innerText(),/Изменённый публичный отзыв/);
  await page.locator('#account-button').click();await page.locator('[data-logout]').click();
  await page.locator('#auth-form').waitFor();await page.reload();
  await page.waitForFunction(()=>document.querySelector('#review-grid').textContent.includes('Изменённый публичный отзыв'));
  assert.doesNotMatch(await page.locator('#review-grid').innerText(),/Иван Петров|Марк Рид|Даниил Картер/);
});
test('public reviews have empty and failure states with retry, not demonstration content', async () => {
  await page.goto(base);await page.waitForFunction(()=>document.querySelector('#reviews-message').textContent.includes('Отзывов пока нет'));
  assert.equal(await page.locator('#review-grid .review').count(),0);
  await settings({reviewsStatus:503},true);await page.locator('#refresh-reviews').click();
  await page.waitForFunction(()=>document.querySelector('#reviews-message').textContent.includes('Не удалось'));
  await settings({reviewsStatus:200,publicReviews:[{rideRating:4,scooterRating:5,rideComment:'После повтора',scooterComment:'',reviewedAt:'2026-10-28T10:00:00Z'}]},true);
  await page.locator('#refresh-reviews').click();await page.locator('#review-grid .review').waitFor();
  assert.match(await page.locator('#review-grid').innerText(),/После повтора/);
});
test('all reviews paginate and a late response cannot reopen a closed dialog', async () => {
  const publicReviews=Array.from({length:21},(_,i)=>({rideRating:5,scooterRating:4,rideComment:'Отзыв '+i,scooterComment:'',reviewedAt:'2026-10-28T10:00:00Z'}));
  await settings({publicReviews});await page.goto(base);
  await page.locator('#all-reviews').click();await page.waitForFunction(()=>document.querySelector('#public-review-list')?.children.length===20);
  await page.locator('[data-more-reviews]').click();await page.waitForFunction(()=>document.querySelector('#public-review-list')?.children.length===21);
  assert.equal(await page.locator('[data-more-reviews]').isHidden(),true);
  await page.locator('.dialog-close').click();
  await page.route('**/api/reviews?limit=20&offset=0',async route=>{await new Promise(resolve=>setTimeout(resolve,300));await route.continue();});
  await page.locator('#all-reviews').click();await page.locator('.dialog-close').click();await page.waitForTimeout(500);
  assert.equal(await page.locator('#details').evaluate(d=>d.open),false);
});

test('opening login.jsp with a valid USER session preserves login and shows profile', async () => {
  await login();await page.goto(base+'login.jsp');await page.locator('.profile-data').waitFor();
  assert.equal(await page.locator('#auth-form').count(),0);
  assert.equal((await calls()).filter(c=>c.path==='/api/users/logout').length,0);
  await page.goto(base+'?auth=login');await page.locator('.profile-data').waitFor();
  assert.equal(await page.locator('#auth-form').count(),0);
});
test('ADMIN entry reuses the BFF session to enter Moderation after its local token is cleared', async () => {
  await settings({role:'ADMIN'});await openAuth();
  await page.locator('[name="login"]').fill('admin');await page.locator('[name="password"]').fill('password123');
  await page.locator('#auth-form [type="submit"]').click();await page.waitForURL('**/admin/users.html#accessToken=*');
  await page.goto(base+'login.jsp');await page.waitForURL('**/admin/users.html#accessToken=*');
  assert.equal((await calls()).filter(c=>c.path==='/api/users/logout').length,0);
});

test('address bar API navigation shows a service page and never user JSON', async () => {
  await login();
  const response=await page.goto(base+'api/users/me');
  assert.equal(response.status(),403);
  assert.match(await page.locator('h1').innerText(),/служебный адрес/i);
  assert.doesNotMatch(await page.locator('body').innerText(),/Иван Петров|birthDate|accessToken/);
  await page.goto(base);await page.locator('.product').first().waitFor();
  assert.equal(await page.evaluate(async()=>{const r=await fetch(document.body.dataset.ctx+'/api/users/me');return r.status;}),200);
});
