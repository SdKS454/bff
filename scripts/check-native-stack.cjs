'use strict';
// Creates test accounts/rides/documents on the running local stand; never calls Docker.
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const base = process.env.BFF_BASE_URL || 'http://localhost:8080';
assert.ok(['localhost', '127.0.0.1'].includes(new URL(base).hostname), 'Run against a local stand');
const password = 'Native-check-password-2026!';
const suffix = randomUUID().slice(0, 12);
async function api(route, token, method = 'GET', body) {
  const response = await fetch(base + route, {
    method, signal: AbortSignal.timeout(15000),
    headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  let data; try { data = JSON.parse(bytes.toString()); } catch {}
  return { status: response.status, bytes, data, headers: response.headers };
}
async function account(kind) {
  const login = 'native.' + kind + '.' + suffix;
  const registration = await api('/api/auth/register', null, 'POST', { login, password, fullName: 'Проверка без Docker', birthDate: '2000-01-01' });
  assert.equal(registration.status, 201, JSON.stringify(registration.data));
  const loginResult = await api('/api/auth/login', null, 'POST', { login, password });
  assert.equal(loginResult.status, 200, JSON.stringify(loginResult.data));
  return loginResult.data;
}
function pdf(result) {
  assert.equal(result.status, 200, JSON.stringify(result.data));
  assert.match(result.headers.get('Content-Type'), /^application\/pdf/);
  assert.equal(result.bytes.subarray(0, 5).toString(), '%PDF-');
}
(async () => {
  const owner = await account('owner'), other = await account('other');
  const token = owner.accessToken;
  assert.equal((await api('/api/users/me', token)).data.id, owner.user.id);
  const nearest = await api('/api/scooters/nearest', token, 'POST', { posX: 10, posY: 15 });
  assert.equal(nearest.status, 200); assert.ok(Array.isArray(nearest.data));
  const scooter = nearest.data.find(s => s.status === 'READY');
  assert.ok(scooter, 'A READY scooter is required');
  const started = await api('/api/rides', token, 'POST', { numberPlate: scooter.numberPlate, posX: 10, posY: 15 });
  assert.equal(started.status, 201, JSON.stringify(started.data));
  const id = started.data.id;
  assert.equal((await api('/api/rides/' + id + '/position', other.accessToken, 'PUT', { posX: 11, posY: 16 })).status, 403);
  assert.equal((await api('/api/rides/' + id + '/position', token, 'PUT', { posX: 11.125, posY: 16.75 })).status, 204);
  const finished = await api('/api/rides/' + id + '/finish', token, 'PUT', { posX: 12, posY: 17 });
  assert.equal(finished.status, 200, JSON.stringify(finished.data));
  const paid = await api('/api/rides/' + id + '/pay', token, 'POST', {});
  assert.equal(paid.status, 200, JSON.stringify(paid.data)); assert.equal(paid.data.status, 'PAID');
  assert.equal(paid.data.amount, finished.data.amount); assert.equal(paid.data.paymentMode, 'EDUCATIONAL');
  pdf(await api('/api/documents/generate', token, 'POST', { docType: 'RIDE_PAYMENT', sourceId: id }));
  const today = new Date().toISOString().slice(0, 10);
  pdf(await api('/api/documents/generate', token, 'POST', { docType: 'USER_HISTORY', from: today, to: today }));
  const documents = await api('/api/documents', token); assert.equal(documents.status, 200);
  const receipt = documents.data.find(d => d.sourceId === id);
  assert.ok(receipt?.snapshotAvailable);
  pdf(await api('/api/documents/' + receipt.id + '/download', token));
  assert.equal((await api('/api/documents/' + receipt.id + '/download', other.accessToken)).status, 403);
  assert.equal((await api('/api/documents/' + receipt.id, token, 'DELETE')).status, 204);
  assert.equal((await api('/api/documents/' + receipt.id + '/download', token)).status, 404);
  assert.equal((await api('/api/auth/logout', token, 'POST')).status, 204);
  assert.equal((await api('/api/users/me', token)).status, 401);
  await api('/api/auth/logout', other.accessToken, 'POST');
  console.log('PASS: native local registration, User/Moderation profile, nearest, owned position, finish/pay, receipt/history PDF, snapshots, ownership, soft delete and logout');
})().catch(error => { console.error(error.stack || error.message); process.exitCode = 1; });
