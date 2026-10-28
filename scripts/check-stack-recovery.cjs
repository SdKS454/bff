'use strict';
// Additional destructive recovery checks, only on the disposable full-stack test project.
const assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const {randomUUID}=require('node:crypto');
const path=require('node:path');
const project=process.env.STACK_TEST_PROJECT||'kicksharing-full-check';
assert.ok(project.startsWith('kicksharing-full-check'));
const root=path.resolve(__dirname,'..'),password='Recovery-password-123',nonce=Date.now().toString(36);
function dc(...args){return execFileSync('docker',['compose','-p',project,...args],{cwd:root,encoding:'utf8',timeout:180000});}
function sql(db,query){return dc('exec','-T','postgres','psql','-U','kicksharing','-d',db,'-At','-v','ON_ERROR_STOP=1','-c',query).trim();}
async function api(port,route,token,method='GET',body){const r=await fetch('http://localhost:'+port+route,{method,signal:AbortSignal.timeout(12000),headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});let data;try{data=await r.json();}catch{}return{status:r.status,data};}
async function wait(check,message){const deadline=Date.now()+45000;while(Date.now()<deadline){try{if(await check())return;}catch{}await new Promise(r=>setTimeout(r,400));}throw new Error(message);}
async function user(suffix){const login='recovery.'+suffix+'.'+nonce;assert.equal((await api(8080,'/api/auth/register',null,'POST',{login,password,fullName:'Иван Петров',birthDate:'2000-01-01'})).status,201);return (await api(8080,'/api/auth/login',null,'POST',{login,password})).data;}
function internal(plate,operation,body){
 // The key is read and used inside its own container; only the status code leaves it.
 const script='curl -s -o /dev/null -w "%{http_code}" -H "Content-Type: application/json" -H "X-Service-Key: $(cat /service-key/service.key)" --data "$1" "$2"';
 return Number(dc('exec','-T','scooter','sh','-c',script,'_',JSON.stringify(body),'http://localhost:8080/api/scooters/'+plate+'/'+operation));
}
(async()=>{
 const owner=await user('owner'),observer=await user('observer');
 const fleet=(await api(8083,'/api/scooters',observer.accessToken)).data;
 const plate=fleet.find(s=>s.status==='READY').numberPlate;
 const cancelled=randomUUID(),live=randomUUID();
 assert.equal(internal(plate,'release',{rideId:cancelled}),200);
 assert.equal(internal(plate,'reserve',{rideId:cancelled,userId:owner.user.id}),409,'A delayed reserve cannot revive a cancellation');
 assert.equal(internal(plate,'reserve',{rideId:live,userId:owner.user.id}),200);
 assert.equal(internal(plate,'reserve',{rideId:live,userId:owner.user.id}),200);
 assert.equal(internal(plate,'release',{rideId:cancelled,posX:999,posY:999}),200);
 assert.equal((await api(8083,'/api/scooters/'+plate,observer.accessToken)).data.status,'IN_RIDE','Old release cannot free another reservation');
 assert.equal(internal(plate,'release',{rideId:live,posX:12.5,posY:13.5}),200);
 console.log('PASS: cancellation fence, idempotent reserve, stale release cannot free a newer ride');
 dc('stop','ride');
 const interrupted=randomUUID();
 sql('ride',`INSERT INTO rides(id,user_id,number_plate,rate_id,status,started_at,unlock_fee,price_per_minute,currency,payment_mode) VALUES('${interrupted}','${owner.user.id}','${plate}','6ba7b810-9dad-11d1-80b4-00c04fd430c8','STARTING',NOW()-INTERVAL '1 minute',50,8,'RUB','EDUCATIONAL')`);
 assert.equal(internal(plate,'reserve',{rideId:interrupted,userId:owner.user.id}),200);
 assert.equal((await api(8081,'/api/users/logout',owner.accessToken,'POST')).status,204);
 dc('start','ride');
 await wait(async()=>sql('ride',`SELECT count(*) FROM rides WHERE id='${interrupted}'`)==='0','Interrupted start must recover');
 assert.equal((await api(8083,'/api/scooters/'+plate,observer.accessToken)).data.status,'READY');
 assert.equal(internal(plate,'reserve',{rideId:interrupted,userId:owner.user.id}),409);
 console.log('PASS: interrupted STARTING recovers after Ride restart even after original JWT is revoked');
 const started=await api(8080,'/api/rides',observer.accessToken,'POST',{numberPlate:plate});assert.equal(started.status,201);
 assert.equal((await api(8080,'/api/rides/'+started.data.id+'/finish',observer.accessToken,'PUT',{posX:14.25,posY:15.75})).status,200);
 const paid=await api(8080,'/api/rides/'+started.data.id+'/pay',observer.accessToken,'POST',{});assert.equal(paid.status,200);
 dc('down');dc('up','--no-build','-d');
 await wait(async()=>{const r=await api(8081,'/api/users/validate',observer.accessToken);return r.status===204;},'Stored JWT survives complete stack restart');
 const saved=await api(8084,'/api/rides/'+started.data.id,observer.accessToken);assert.equal(saved.status,200);assert.equal(saved.data.status,'PAID');assert.equal(saved.data.paidAt,paid.data.paidAt);assert.equal(saved.data.amount,paid.data.amount);
 assert.equal((await api(8085,'/api/documents/generate',observer.accessToken,'POST',{userId:observer.user.id,docType:'RIDE_PAYMENT',sourceId:started.data.id})).status,200);
 console.log('PASS: docker compose down/up preserves databases, RSA keys, valid tokens, paid rides and PDF generation');
})().catch(error=>{console.error(error.stack||error.message);process.exitCode=1;});
