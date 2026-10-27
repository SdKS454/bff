const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const code = fs.readFileSync('src/main/webapp/frontend/api.js', 'utf8');
function setup(fetch = async () => ({ok:true, status:200, json:async () => ({})})) {
  const store = new Map();
  const context = {
    window:{}, document:{body:{dataset:{ctx:'/scooter-bff'}}}, fetch,
    localStorage:{getItem:key => store.get(key),setItem:(key,value) => store.set(key,value)},
    TextEncoder, URL, Date, setTimeout,
    location:{replace: url => { context.destination = url; }}
  };
  vm.runInNewContext(code, context);
  return {api:context.window.ScootxApi, context, store};
}
test('requests use context path, session cookie and JSON, never demo authentication', async () => {
  let call;
  const {api} = setup(async (path, options) => { call = {path, options}; return {ok:true,status:204}; });
  assert.equal(await api.request('/api/auth/logout','POST'), null);
  assert.equal(call.path, '/scooter-bff/api/auth/logout');
  assert.equal(call.options.credentials, 'same-origin');
  assert.equal(call.options.method, 'POST');
  assert.equal(call.options.headers, undefined);
  await api.request('/api/rides','POST',{numberPlate:'A123BC'});
  assert.equal(call.options.headers['Content-Type'],'application/json');
  assert.equal(call.options.headers.Authorization,undefined);
});
test('API status and user message survive; HTML is not treated as a service message', async () => {
  const {api} = setup(async () => ({ok:false,status:409,text:async () => '{"error":"Логин уже занят"}'}));
  await assert.rejects(api.request('/api/auth/register','POST',{}), e => e.status === 409 && e.message === 'Логин уже занят');
  const html = setup(async () => ({ok:false,status:503,text:async () => '<html>internal error</html>'}));
  await assert.rejects(html.api.request('/api/rides'), e => e.status === 503 && !e.message.includes('<html>'));
});
test('network and malformed success responses are failures, not successful actions', async () => {
  const network = setup(async () => { throw new Error('offline'); });
  await assert.rejects(network.api.request('/api/rides'), e => e.status === 0);
  const invalid = setup(async () => ({ok:true,status:200,json:async () => {throw new Error('HTML');}}));
  await assert.rejects(invalid.api.request('/api/rides'), e => e.status === 502);
});
test('registration matches byte limit and rejects invalid dates and mismatched passwords', () => {
  const {api} = setup();
  const input = {login:'ivan',password:'я'.repeat(36),confirm:'я'.repeat(36),fullName:'Иван',birthDate:'2000-05-17'};
  assert.doesNotThrow(() => api.validateRegistration(input));
  assert.throws(() => api.validateRegistration({...input,password:'я'.repeat(37)}),/72 байта/);
  assert.throws(() => api.validateRegistration({...input,birthDate:'2026-02-30'}),/дату рождения/);
  assert.throws(() => api.validateRegistration({...input,confirm:'different'}),/не совпадают/);
  assert.throws(() => api.validateRegistration({...input,login:'иван'}),/Логин/);
});
test('favorites are per-user device data and still work when localStorage fails', () => {
  const {api,context,store} = setup();
  api.setFavorite('user-a','A123BC',true);
  assert.deepEqual(Array.from(api.favorites('user-a')),['A123BC']);
  assert.equal(api.favorites('user-b').length,0);
  assert.equal(store.size,1);
  assert.ok([...store.keys()][0].startsWith('scootx:favorites:'));
  context.localStorage.setItem = () => {throw new Error('quota');};
  api.setFavorite('user-b','B777OP',true);
  assert.deepEqual(Array.from(api.favorites('user-b')),['B777OP']);
});
test('ADMIN redirects with JWT only in fragment, USER never redirects', async () => {
  const {api,context,store} = setup(async () => ({ok:true,status:200,json:async () => ({moderationPanelUrl:'http://localhost:8082/admin/users.html'})}));
  assert.equal(await api.redirectAdmin({user:{role:'USER'}}),false);
  assert.equal(await api.redirectAdmin({user:{role:'ADMIN'},accessToken:'test.jwt+value/='}),true);
  const destination = new URL(context.destination);
  assert.equal(destination.search,'');
  assert.equal(new URLSearchParams(destination.hash.slice(1)).get('accessToken'),'test.jwt+value/=');
  assert.equal(store.size,0);
});
test('stored document download uses authenticated GET and rejects non-PDF data', async () => {
  let call, clicked=false;
  const {api,context}=setup(async (path,options)=>{call={path,options};return{ok:true,status:200,headers:{get:()=> 'application/pdf'},blob:async()=>({})};});
  context.URL={createObjectURL:()=> 'blob:test',revokeObjectURL:()=>{}};
  context.setTimeout=fn=>fn();
  context.document.createElement=()=>({click:()=>{clicked=true;},remove:()=>{}});
  context.document.body.append=()=>{};
  await api.downloadDocument(undefined,'7c9e6679-7425-40de-944b-e07fc1f90ae7');
  assert.equal(call.path,'/scooter-bff/api/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7/download');
  assert.equal(call.options.method,'GET');assert.equal(call.options.credentials,'same-origin');assert.equal(clicked,true);
  const bad=setup(async()=>({ok:true,status:200,headers:{get:()=> 'text/html'}}));
  await assert.rejects(bad.api.downloadDocument(undefined,'id'),e=>e.status===502);
});
