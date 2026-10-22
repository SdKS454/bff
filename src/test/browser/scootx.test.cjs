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
  context = await browser.newContext({viewport:{width:1440,height:1000},acceptDownloads:true});
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
async function fillCoords(form) {
  await page.locator(form + ' [name="posX"]').fill('10.5');
  await page.locator(form + ' [name="posY"]').fill('24.5');
}
async function startRide() {
  await page.locator('.dialog-close').click();
  await page.locator('[data-scooter]').first().click();
  await page.locator('[data-start-ride]').click();
  await fillCoords('#start-ride-form');
  await page.locator('#start-ride-form [type="submit"]').click();
  await page.locator('[data-finish-ride]').waitFor();
}
async function finishRide() {
  await page.locator('[data-finish-ride]').click();
  await fillCoords('#finish-ride-form');
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
  for (const [name,value] of Object.entries({login:'ivan',password:'я'.repeat(37),confirm:'я'.repeat(37),fullName:'Иван',birthDate:'2000-05-17'})) await page.locator(`[name="${name}"]`).fill(value);
  await page.locator('#auth-form [type="submit"]').click();
  await page.locator('.form-error:not([hidden])').waitFor();
  assert.match(await page.locator('.form-error').innerText(),/72 байта/);
  assert.ok(!(await calls()).some(c => c.path === '/api/users/register'));
});
test('registration displays User API duplicate login error', async () => {
  await settings({registerStatus:409}); await openAuth('register');
  for (const [name,value] of Object.entries({login:'ivan',password:'password123',confirm:'password123',fullName:'Иван',birthDate:'2000-05-17'})) await page.locator(`[name="${name}"]`).fill(value);
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
test('history PDF and nearest search use actual API paths with entered coordinates', async () => {
  await login(); await page.locator('[data-account-tab="documents"]').click();
  await page.locator('[name="from"]').fill('2026-10-01'); await page.locator('[name="to"]').fill('2026-10-20');
  const downloaded = page.waitForEvent('download'); await page.locator('#history-document-form [type="submit"]').click();
  assert.equal((await downloaded).suggestedFilename(),'document.pdf');
  await page.locator('.dialog-close').click(); await page.locator('#nearest-fleet').click();
  await fillCoords('#nearest-form'); await page.locator('#nearest-form [type="submit"]').click();
  await page.waitForFunction(() => !document.querySelector('#details').open);
  assert.match(await page.locator('#catalog-message').innerText(),/Единицы расстояния/);
  assert.ok((await calls()).some(c => c.method === 'POST' && c.path === '/api/scooters/nearest'));
});
test('registered account is not registered twice when subsequent login fails', async () => {
  await settings({loginStatus:503}); await openAuth('register');
  for (const [name,value] of Object.entries({login:'ivan',password:'password123',confirm:'password123',fullName:'Иван',birthDate:'2000-05-17'})) await page.locator(`[name="${name}"]`).fill(value);
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
