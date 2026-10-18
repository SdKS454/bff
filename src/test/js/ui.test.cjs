const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

// Small DOM double: writing innerHTML throws, so regression to unsafe interpolation fails.
class Element {
  constructor(tag = 'div') { this.tag = tag; this.children = []; this.listeners = {}; this.dataset = {}; this.disabled = false; this._text = ''; }
  set textContent(value) { this._text = String(value); this.children = []; }
  get textContent() { return this._text + this.children.map(c => c.textContent).join(''); }
  set innerHTML(_) { throw new Error('Unsafe innerHTML write'); }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; this._text = ''; }
  addEventListener(event, listener) { this.listeners[event] = listener; }
  remove() {}
  click() { return this.listeners.click?.({ currentTarget: this }); }
  querySelector(tag) { return this.children.find(c => c.tag === tag); }
}
function buttons(element) { return element.children.flatMap(c => c.tag === 'button' ? [c] : buttons(c)); }
async function until(predicate) {
  for (let i = 0; i < 30; i++) { if (predicate()) return; await new Promise(setImmediate); }
  assert.fail('UI did not reach expected state');
}
function setup({ failPayment = false, initialState = 'ACTIVE' } = {}) {
  const ids = Object.fromEntries(['list', 'rides', 'coords', 'message', 'btn-refresh', 'documents', 'blocked-document'].map(id => [id, new Element()]));
  const body = new Element('body'); body.dataset.ctx = '/scooter-bff';
  const calls = []; const timers = []; const downloads = [];
  const hostilePlate = '<img src=x onerror=alert(1)>';
  const rideId = '7c9e6679-7425-40de-944b-e07fc1f90ae7';
  let state = initialState;
  let onReady;
  const document = {
    body,
    getElementById: id => ids[id],
    createElement: tag => {
      const element = new Element(tag);
      if (tag === 'a') element.click = () => downloads.push(element.download);
      return element;
    },
    addEventListener: (event, fn) => { onReady = fn; }
  };
  const location = { assign: url => { location.assigned = url; } };
  const context = vm.createContext({
    document, location, console, FormData: class { constructor() { return new Map(); } },
    getUserCoords: async () => ({ posX: 37.1, posY: 55.7 }),
    setTimeout: fn => timers.push(fn),
    URL: { createObjectURL: () => 'blob:fixture', revokeObjectURL: () => {} },
    fetch: async (url, options = {}) => {
      calls.push({ url, options });
      const requestPath = url.replace('/scooter-bff', '');
      let status = 200;
      let data;
      if (requestPath === '/api/scooters/nearest') data = [{ numberPlate: hostilePlate, status: 'READY', distance: 12.5 }];
      else if (requestPath === '/api/rides') {
        if (options.method === 'POST') state = 'ACTIVE';
        data = [{ id: rideId, numberPlate: hostilePlate, status: state }];
      } else if (requestPath.endsWith('/finish')) { state = 'FINISHED'; data = { status: state }; }
      else if (requestPath.endsWith('/pay')) {
        if (failPayment) { status = 503; data = { error: 'Оплата недоступна' }; }
        else { state = 'PAID'; data = { status: state }; }
      } else data = {};
      return { ok: status >= 200 && status < 300, status, json: async () => data, text: async () => JSON.stringify(data), blob: async () => ({}) };
    }
  });
  const jsDir = path.join(__dirname, '../../main/webapp/js');
  for (const file of ['api.js', 'main.js']) vm.runInContext(fs.readFileSync(path.join(jsDir, file), 'utf8'), context, { filename: file });
  onReady();
  return { ids, calls, context, location, hostilePlate, downloads, timers };
}

test('service text is rendered safely and distance has no invented metres', async () => {
  const ui = setup();
  await until(() => buttons(ui.ids.list).length > 0);
  assert.ok(ui.ids.list.textContent.includes(ui.hostilePlate));
  assert.ok(ui.ids.list.textContent.includes('12.5'));
  assert.ok(!ui.ids.list.textContent.includes('12.5 м'));
  assert.ok(ui.ids.rides.textContent.includes(ui.hostilePlate));
});
test('finish calls finish then pay and makes receipt available', async () => {
  const ui = setup();
  await until(() => buttons(ui.ids.rides).some(b => b.textContent.includes('Завершить')));
  await buttons(ui.ids.rides)[0].click();
  const operations = ui.calls.filter(c => /\/(finish|pay)$/.test(c.url));
  assert.equal(operations.length, 2);
  assert.ok(operations[0].url.endsWith('/finish'));
  assert.equal(operations[0].options.method, 'PUT');
  assert.ok(operations[1].url.endsWith('/pay'));
  assert.equal(operations[1].options.method, 'POST');
  assert.ok(buttons(ui.ids.rides).some(b => b.textContent === 'Скачать чек'));
});
test('failed payment leaves FINISHED ride retryable and shows service error', async () => {
  const ui = setup({ failPayment: true });
  await until(() => buttons(ui.ids.rides).length > 0);
  await buttons(ui.ids.rides)[0].click();
  assert.ok(buttons(ui.ids.rides).some(b => b.textContent === 'Оплатить'));
  assert.equal(ui.ids.message.textContent, 'Оплата недоступна');
  assert.ok(buttons(ui.ids.rides).every(b => !b.disabled));
});
test('receipt uses RIDE_PAYMENT and sourceId, no browser-supplied userId', async () => {
  const ui = setup({ initialState: 'PAID' });
  await until(() => buttons(ui.ids.rides).length > 0);
  await buttons(ui.ids.rides)[0].click();
  const call = ui.calls.find(c => c.url.endsWith('/api/documents/generate'));
  const payload = JSON.parse(call.options.body);
  assert.equal(payload.docType, 'RIDE_PAYMENT');
  assert.equal(payload.sourceId, '7c9e6679-7425-40de-944b-e07fc1f90ae7');
  assert.equal(payload.userId, undefined);
  assert.deepEqual(ui.downloads, ['document.pdf']);
});
test('API 401 redirects to login; non-JSON failures are visible', async () => {
  const ui = setup();
  await until(() => buttons(ui.ids.rides).length > 0);
  ui.context.fetch = async () => ({ ok: false, status: 401, text: async () => '{"error":"Истёк токен"}' });
  await assert.rejects(vm.runInContext("apiRequest('/api/rides')", ui.context), /Истёк токен/);
  assert.equal(ui.location.assigned, '/scooter-bff/login.jsp');
  ui.context.fetch = async () => ({ ok: false, status: 503, text: async () => 'service unavailable' });
  await assert.rejects(vm.runInContext("apiRequest('/api/rides')", ui.context), /service unavailable/);
});
