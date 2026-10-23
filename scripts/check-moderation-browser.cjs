'use strict';
// Invoked by check-moderation-integration.py on disposable databases only.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const bff = process.env.BFF_REAL_URL, user = process.env.USER_REAL_URL, mod = process.env.MODERATION_REAL_URL;
const password = 'Integration-password-123';
async function api(base, route, token, method = 'GET', body) {
  const response = await fetch(base + route, {method, headers:{...(token ? {Authorization:'Bearer ' + token} : {}), ...(body ? {'Content-Type':'application/json'} : {})}, body:body ? JSON.stringify(body) : undefined});
  let data; try { data = await response.json(); } catch {}
  return {status:response.status, data};
}
(async () => {
  const normal = await api(bff, '/api/auth/login', null, 'POST', {login:'integration.user', password});
  assert.equal(normal.status, 200);
  const token = normal.data.accessToken, id = normal.data.user.id;
  assert.equal(normal.data.user.blocked, false);
  const parts = token.split('.');
  const payload = JSON.parse(Buffer.from(parts[1], 'base64url'));
  payload.role = 'ADMIN';
  const forged = parts[0] + '.' + Buffer.from(JSON.stringify(payload)).toString('base64url') + '.' + parts[2];
  assert.equal((await api(mod, '/api/moderation/reasons', forged)).status, 401, 'Forged ADMIN must be rejected');
  assert.equal((await api(mod, '/api/moderation/reasons', token)).status, 403);
  assert.equal((await api(mod, '/api/moderation/users/00000000-0000-4000-8000-000000000000/status', token)).status, 403);
  console.log('PASS: real USER login, forged ADMIN rejected, permissions enforced');

  const browser = await chromium.launch({executablePath:process.env.CHROMIUM_EXECUTABLE, headless:true, args:['--no-sandbox','--disable-dev-shm-usage']});
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await page.goto(bff + '/login.jsp');
    await page.locator('[name="login"]').fill('integration.admin');
    await page.locator('[name="password"]').fill(password);
    const loginResponse = page.waitForResponse(r => r.url() === bff + '/api/auth/login' && r.request().method() === 'POST');
    await page.locator('#auth-form [type="submit"]').click();
    const adminAuth = await (await loginResponse).json(), admin = adminAuth.accessToken;
    assert.equal(adminAuth.user.role, 'ADMIN');
    await page.waitForURL(mod + '/admin/users.html'); // Panel consumes and erases the fragment.
    assert.equal(await page.evaluate(() => sessionStorage.getItem('accessToken')), admin);
    await page.locator('#users-tbody tr').filter({hasText:'integration.user'}).waitFor();
    assert.equal((await api(user, '/api/users/blocked?limit=500&offset=0', admin)).status, 200);
    assert.equal((await api(user, '/api/users/00000000-0000-4000-8000-000000000000/ban', admin, 'POST', {reason:'RIDE_DANGEROUS'})).status, 404);
    console.log('PASS: real browser ADMIN redirect, token handoff, User CORS, panel table');

    const row = () => page.locator('#users-tbody tr').filter({hasText:'integration.user'});
    await row().getByRole('button', {name:'Заблокировать', exact:true}).click();
    await page.locator('#ban-reason').selectOption('RIDE_DANGEROUS');
    await page.locator('#ban-confirm').click();
    await row().getByRole('button', {name:'Разблокировать', exact:true}).waitFor();
    const blocked = await api(mod, '/api/moderation/users/' + id + '/status', token);
    assert.equal(blocked.status, 200); assert.equal(blocked.data.blocked, true);
    assert.equal(blocked.data.reasonType, 'RIDE_DANGEROUS');
    assert.equal((await api(bff, '/api/users/me', token)).data.blocked, true);
    assert.equal((await api(bff, '/api/rides', token, 'POST', {numberPlate:'A123BC', posX:10, posY:20})).status, 403);
    const repeated = await api(mod, '/api/moderation/users/' + id + '/ban', admin, 'POST', {reason:'PAYMENT_DEBT'});
    assert.equal(repeated.status, 200); assert.deepEqual(repeated.data, blocked.data);
    console.log('PASS: panel ban → actual Moderation DB → User → BFF 403; repeat ban preserves dates/reason');

    await row().getByRole('button', {name:'Разблокировать', exact:true}).click();
    await page.locator('#unban-confirm').click();
    await row().getByRole('button', {name:'Заблокировать', exact:true}).waitFor();
    const unblocked = await api(mod, '/api/moderation/users/' + id + '/status', token);
    assert.equal(unblocked.data.blocked, false); assert.equal(unblocked.data.reasonType, null);
    assert.equal((await api(bff, '/api/rides', token, 'POST', {numberPlate:'A123BC', posX:10, posY:20})).status, 201);
    console.log('PASS: panel unban permits BFF ride request (Ride is an HTTP fixture)');

    await page.route(user + '/api/users/logout', async route => {
      if (route.request().method() === 'OPTIONS') return route.continue();
      await route.fulfill({status:503, contentType:'application/json',
        headers:{'Access-Control-Allow-Origin':mod}, body:JSON.stringify({error:'Injected User outage'})});
    });
    await page.locator('#logout-btn').click();
    await page.getByText(/Не удалось отозвать токен:/).waitFor();
    assert.equal(page.url(), mod + '/admin/users.html');
    assert.equal((await api(user, '/api/users/validate', admin)).status, 204);
    await page.unroute(user + '/api/users/logout');
    await page.locator('#logout-btn').click();
    await page.waitForURL(bff + '/?auth=login');
    await page.locator('#auth-form').waitFor();
    assert.equal((await api(user, '/api/users/validate', admin)).status, 401);
    assert.equal((await api(mod, '/api/moderation/reasons', admin)).status, 401);
    assert.equal((await api(bff, '/api/users/me', admin)).status, 401);
    assert.equal((await api(bff, '/api/auth/logout', token, 'POST')).status, 204);
    assert.equal((await api(mod, '/api/moderation/users/' + id + '/status', token)).status, 401);
    assert.deepEqual(errors, []);
    await context.close();
    console.log('PASS: panel logout and BFF logout revoke real JWTs; Moderation rejects both');
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
