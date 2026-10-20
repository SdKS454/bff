const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function authPage(script, { role = 'USER', panel = 'https://moderation.example/admin/users.html', apiError = null } = {}) {
  const fields = Object.fromEntries(Object.entries({ login: 'ivan', password: 'password123', fullName: 'Иван Петров', birthDate: '2000-05-17' }).map(([name, value]) => [name, {
    value, validityMessage: '', listeners: {}, setCustomValidity(message) { this.validityMessage = message; },
    addEventListener(event, listener) { this.listeners[event] = listener; }
  }]));
  const submit = { disabled: false };
  const form = {
    listeners: {}, elements: { namedItem: name => fields[name] },
    addEventListener(event, listener) { this.listeners[event] = listener; },
    querySelector() { return submit; },
    reportValidity() { return !Object.values(fields).some(f => f.validityMessage); }
  };
  const err = { textContent: '' };
  const location = { assign(url) { location.destination = url; } };
  const calls = [];
  let ready;
  const context = vm.createContext({
    document: {
      body: { dataset: { ctx: '/scooter-bff' } },
      getElementById(id) { return id === 'f' ? form : err; },
      addEventListener(_, listener) { ready = listener; }
    },
    location, URL, TextEncoder, Date,
    FormData: class { constructor() { return Object.entries(fields).map(([key, field]) => [key, field.value]); } },
    fetch: async (url, options = {}) => {
      calls.push({ url, options });
      if (apiError) return { ok: false, status: 409, text: async () => JSON.stringify({ error: apiError }) };
      const data = url.endsWith('/api/config') ? { moderationPanelUrl: panel } : { accessToken: 'test.jwt+value/=', user: { role } };
      return { ok: true, json: async () => data };
    }
  });
  for (const file of ['api.js', script]) vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/webapp/js', file), 'utf8'), context, { filename: file });
  ready();
  return { fields, form, err, location, calls, submit, send: () => form.listeners.submit({ preventDefault() {} }) };
}

test('USER login stays on BFF and does not fetch Moderation config', async () => {
  const ui = authPage('login.js');
  await ui.send();
  assert.equal(ui.location.destination, '/scooter-bff/main.jsp');
  assert.equal(ui.calls.length, 1);
});
test('ADMIN login uses configured panel and passes JWT only in fragment', async () => {
  const ui = authPage('login.js', { role: 'ADMIN' });
  await ui.send();
  const target = new URL(ui.location.destination);
  assert.equal(target.origin, 'https://moderation.example');
  assert.equal(target.pathname, '/admin/users.html');
  assert.equal(target.search, '');
  assert.equal(new URLSearchParams(target.hash.slice(1)).get('accessToken'), 'test.jwt+value/=');
});
test('ADMIN without configured URL stays on login with actionable error', async () => {
  const ui = authPage('login.js', { role: 'ADMIN', panel: null });
  await ui.send();
  assert.equal(ui.location.destination, undefined);
  assert.match(ui.err.textContent, /ещё не настроен/);
});
test('unsafe Moderation destination is rejected', async () => {
  const ui = authPage('login.js', { role: 'ADMIN', panel: 'javascript:alert(1)' });
  await ui.send();
  assert.equal(ui.location.destination, undefined);
  assert.match(ui.err.textContent, /Некорректный адрес/);
});
test('registration enforces 72 UTF-8 bytes and clears custom error after editing', async () => {
  const ui = authPage('register.js');
  ui.fields.password.value = 'я'.repeat(37);
  await ui.send();
  assert.equal(ui.calls.length, 0);
  assert.match(ui.fields.password.validityMessage, /72 байта/);
  ui.fields.password.value = 'я'.repeat(36);
  ui.fields.password.listeners.input();
  assert.equal(ui.fields.password.validityMessage, '');
  await ui.send();
  assert.equal(ui.location.destination, '/scooter-bff/login.jsp');
});
test('registration validates fullName and bounds birthday to UTC today', async () => {
  const ui = authPage('register.js');
  assert.equal(ui.fields.birthDate.max, new Date().toISOString().slice(0, 10));
  ui.fields.fullName.value = 'Иван\nПетров';
  await ui.send();
  assert.equal(ui.calls.length, 0);
  assert.match(ui.fields.fullName.validityMessage, /управляющих/);
});
test('User registration API errors are displayed verbatim as text', async () => {
  const ui = authPage('register.js', { apiError: 'Логин уже занят' });
  await ui.send();
  assert.equal(ui.err.textContent, 'Логин уже занят');
  assert.equal(ui.location.destination, undefined);
  assert.equal(ui.submit.disabled, false);
});
