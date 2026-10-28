'use strict';
// Opt-in local demonstration accounts. Existing accounts with another password are not changed.
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const project=process.env.DEMO_PROJECT||'kicksharing';
const base=process.env.DEMO_BFF_URL||'http://localhost:8080';
const password=process.env.DEMO_PASSWORD||'Demo-password-2026!';
const user=process.env.DEMO_USER_LOGIN||'demo.user',admin=process.env.DEMO_ADMIN_LOGIN||'demo.admin';
async function request(route,body){const r=await fetch(base+route,{method:body?'POST':'GET',signal:AbortSignal.timeout(15000),headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined});let data;try{data=await r.json();}catch{}if(!r.ok)throw new Error(data?.error||'HTTP '+r.status);return data;}
async function account(login,name){
 if(!/^[a-z0-9_.-]{3,64}$/.test(login))throw new Error('Логин должен соответствовать правилам регистрации');
 const existing=await request('/api/users/exists?login='+encodeURIComponent(login));
 if(!existing.exists)await request('/api/auth/register',{login,password,fullName:name,birthDate:'2000-05-17'});
 try{return await request('/api/auth/login',{login,password});}
 catch(error){throw new Error('Не удалось войти как '+login+'. Существующий пароль не изменён; выберите другой DEMO_*_LOGIN или укажите подходящий DEMO_PASSWORD. '+error.message);}
}
(async()=>{
 if(user===admin)throw new Error('Для USER и ADMIN нужны разные логины');
 const normal=await account(user,'Иван Петров');
 if(normal.user.role!=='USER')throw new Error(user+' уже имеет другую роль; используйте другой DEMO_USER_LOGIN');
 const privileged=await account(admin,'Пётр Иванов');
 if(privileged.user.role!=='ADMIN'){
  execFileSync('docker',['compose','-p',project,'exec','-T','user','java','-cp','/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*','ru.scooter.user.cli.PromoteAdmin',admin],{cwd:path.resolve(__dirname,'..'),stdio:'pipe'});
  const check=await request('/api/auth/login',{login:admin,password});if(check.user.role!=='ADMIN')throw new Error('Роль ADMIN не подтверждена');
 }
 console.log('Готовы аккаунты: USER '+user+', ADMIN '+admin+'. Данные существующих пользователей не удалялись.');
 console.log(process.env.DEMO_PASSWORD?'Используется пароль из DEMO_PASSWORD.':'Пароль демонстрационных аккаунтов: Demo-password-2026!');
 console.log('Вход: '+base+'/login.jsp. Используйте разные профили/инкогнито для двух ролей.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
