#!/usr/bin/env python3
"""Local six-Tomcat stand. Python 3 stdlib; no IDE or Docker. Run from any directory."""
import argparse
from contextlib import contextmanager
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LOCAL = ROOT / 'local-run'
RUN = LOCAL / 'stand'
STATE = RUN / 'processes.json'
SERVICES = {
 'user': (8081, 'services/user', 'user-service.war'),
 'moderation': (8082, 'services/moderation', 'moderation.war'),
 'scooter': (8083, 'services/scooter', 'scooter.war'),
 'ride': (8084, 'services/ride', 'ride-service.war'),
 'documents': (8085, 'services/documents', 'documents.war'),
 'bff': (8080, '.', 'scooter-bff.war'),
}

@contextmanager
def command_lock():
 LOCAL.mkdir(parents=True,exist_ok=True,mode=0o700)
 with (LOCAL/'.stand-command.lock').open('a+b') as handle:
  handle.seek(0,2)
  if handle.tell()==0:handle.write(b'0');handle.flush()
  handle.seek(0)
  try:
   if os.name=='nt':
    import msvcrt
    msvcrt.locking(handle.fileno(),msvcrt.LK_NBLCK,1)
   else:
    import fcntl
    fcntl.flock(handle.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
  except OSError:raise RuntimeError('Another stand command is running; wait for it to finish')
  try:yield
  finally:
   handle.seek(0)
   if os.name=='nt':msvcrt.locking(handle.fileno(),msvcrt.LK_UNLCK,1)
   else:fcntl.flock(handle.fileno(),fcntl.LOCK_UN)


def prepare(args):
 font=str((ROOT/'assets/DejaVuSans.ttf').resolve())
 if os.name=='nt':
  command=['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',str(ROOT/'scripts/windows/prepare-local.ps1'),'-PgHost',args.pg_host,'-PgPort',str(args.pg_port),'-PgUser',args.pg_user,'-DatabasePrefix',args.database_prefix,'-FontPath',font]
  if args.psql:command+=['-PsqlPath',args.psql]
  if args.java:command+=['-JavaPath',args.java]
 else:
  command=[sys.executable,str(ROOT/'scripts/linux/prepare-local.py'),'--pg-host',args.pg_host,'--pg-port',str(args.pg_port),'--admin-user',args.pg_user,'--database-prefix',args.database_prefix,'--font-path',font,'--socket-dir',args.socket_dir]
  if args.psql:command+=['--psql',args.psql]
  if args.java:command+=['--java',args.java]
  if args.no_sudo:command+=['--no-sudo']
  if args.password_auth:command+=['--password-auth']
 subprocess.run(command,check=True,cwd=ROOT)


def settings(name):
 path = LOCAL / (name + '.env.json')
 if not path.is_file(): raise RuntimeError('First run prepare-local for this machine: missing ' + str(path))
 data = json.loads(path.read_text(encoding='utf-8-sig'))
 if not isinstance(data, dict) or not all(isinstance(k,str) and isinstance(v,str) for k,v in data.items()): raise RuntimeError('Invalid environment file: ' + str(path))
 return data

def java_path(args, env):
 executable = args.java or (str(Path(env['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')) if env.get('JAVA_HOME') else shutil.which('java'))
 if not executable or not Path(executable).is_file(): raise RuntimeError('JDK 17 is required; specify --java /path/to/jdk-17/bin/java')
 result = subprocess.run([executable,'-version'],capture_output=True,text=True,check=True)
 match = re.search(r'version "(\d+)',result.stderr+result.stdout)
 if not match or int(match.group(1)) < 17: raise RuntimeError('Java 17+ required, JDK 17 recommended')
 return str(Path(executable).absolute())

def atomic_json(path, data):
 path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
 temp=path.with_suffix('.tmp')
 with temp.open('w',encoding='utf-8') as stream: json.dump(data,stream,ensure_ascii=False,indent=2)
 if os.name!='nt':temp.chmod(0o600)
 temp.replace(path)

def read_state():
 return json.loads(STATE.read_text(encoding='utf-8')) if STATE.exists() else {}

def command_line(pid):
 if os.name=='nt':
  result=subprocess.run(['powershell.exe','-NoProfile','-Command',f'[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new(); (Get-CimInstance Win32_Process -Filter "ProcessId = {int(pid)}").CommandLine'],capture_output=True,text=True)
  return result.stdout if result.returncode==0 else ''
 proc=Path('/proc')/str(pid)/'cmdline'
 if proc.exists():
  try:return proc.read_bytes().replace(b'\0',b' ').decode(errors='replace')
  except OSError:return ''
 result=subprocess.run(['ps','-p',str(pid),'-o','command='],capture_output=True,text=True)
 return result.stdout

def owned(name,record):
 line=command_line(record['pid'])
 return 'org.apache.catalina.startup.Bootstrap' in line and ('-Dcatalina.base='+str(RUN/name)) in line

def stop():
 state=read_state()
 for name in reversed(list(SERVICES)):
  record=state.get(name)
  if record and owned(name,record):
   if os.name=='nt': subprocess.run(['taskkill','/PID',str(record['pid']),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False)
   else: os.kill(record['pid'],signal.SIGTERM)
 deadline=time.monotonic()+20
 while time.monotonic()<deadline and any(owned(n,r)for n,r in state.items()):time.sleep(.2)
 for name,record in state.items():
  if owned(name,record):
   if os.name!='nt':os.kill(record['pid'],signal.SIGKILL)
   else:raise RuntimeError('Could not stop own process '+name)
 atomic_json(STATE,{})
 print('Stopped only processes managed by this package. Databases and keys preserved.')

def source_hashes():
 result={}
 for path in ROOT.rglob('*'):
  relative=path.relative_to(ROOT)
  if any(p in {'local-run','target','node_modules','.git','.idea','dist'}for p in relative.parts)or not path.is_file():continue
  name=relative.as_posix()
  if name=='pom.xml' or name.startswith('src/main/')or name.startswith('services/')and('/src/main/'in name or name.endswith('/pom.xml')):
   result[name]=hashlib.sha256(path.read_bytes()).hexdigest()
 return result

def build():
 maven=shutil.which('mvn.cmd' if os.name=='nt' else 'mvn')
 if not maven:raise RuntimeError('Install Maven to rebuild sources; prebuilt dist WARs need no Maven')
 for name,(_,module,war)in SERVICES.items():
  subprocess.run([maven,'-f',str(ROOT/module/'pom.xml'),'-DskipTests','package'],check=True,cwd=ROOT)
 (ROOT/'dist').mkdir(exist_ok=True)
 for name,(_,module,war)in SERVICES.items():shutil.copy2(ROOT/module/'target'/war,ROOT/'dist'/(name+'.war'))
 manifest={'sources':source_hashes(),'wars':{name:hashlib.sha256((ROOT/'dist'/(name+'.war')).read_bytes()).hexdigest()for name in SERVICES}}
 atomic_json(ROOT/'dist'/'build-info.json',manifest)
 print('Built all six WARs in dist. Tests skipped for this fast build; see acceptance checklist.')

def check_dist():
 path=ROOT/'dist/build-info.json'
 if not path.is_file():raise RuntimeError('Missing build manifest; run: python scripts/stand.py build')
 manifest=json.loads(path.read_text(encoding='utf-8'))
 if source_hashes()!=manifest['sources']:raise RuntimeError('Sources changed since the WAR build. Run: python scripts/stand.py build')
 for name in SERVICES:
  war=ROOT/'dist'/(name+'.war')
  if not war.is_file()or hashlib.sha256(war.read_bytes()).hexdigest()!=manifest['wars'].get(name):raise RuntimeError('Missing/changed WAR '+str(war)+'; rebuild dist')

def request(port,path,method='GET',body=None,token=None):
 headers={'Accept':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 if body is not None:headers['Content-Type']='application/json'
 req=urllib.request.Request(f'http://127.0.0.1:{port}'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
 try:response=urllib.request.urlopen(req,timeout=6)
 except urllib.error.HTTPError as error:response=error
 data=response.read()
 try:data=json.loads(data)
 except (ValueError,UnicodeError):pass
 return response.status,data

PROBES={'user':('/api/users/exists?login=stand.health',200),'moderation':('/admin/js/config.js',200),'scooter':('/api/scooters',401),'ride':('/api/reviews?limit=1',200),'documents':('/api/documents',401),'bff':('/api/reviews?limit=1',200)}

def probe(name):
 code,_=request(SERVICES[name][0],PROBES[name][0])
 ready=code==PROBES[name][1]
 if name=='bff' and ready:
  homepage,_=request(8080,'/');ready=homepage==200
 return code,ready


def status():
 state=read_state();ok=True
 for name,(port,_,_)in SERVICES.items():
  try:
   code,healthy=probe(name)
  except (OSError,urllib.error.URLError):code='unreachable';healthy=False
  managed=name in state and owned(name,state[name]);ok=ok and healthy and managed
  print(f'{name:12} port={port} HTTP={code} ready={healthy} managed={managed}')
 return ok

def start(args):
 RUN.mkdir(parents=True,exist_ok=True,mode=0o700)
 if any(owned(n,r)for n,r in read_state().items()):raise RuntimeError('Managed stand already running; use status or stop first')
 check_dist()
 home=Path(args.tomcat_home).expanduser().resolve()if args.tomcat_home else None
 if not home or not(home/'bin/bootstrap.jar').is_file()or not(home/'bin/tomcat-juli.jar').is_file():raise RuntimeError('Specify --tomcat-home pointing to an extracted Apache Tomcat 10.1 folder')
 description=(home/'RELEASE-NOTES').read_text(errors='replace')if(home/'RELEASE-NOTES').exists()else''
 if 'Tomcat Version 10.1.'not in description:raise RuntimeError('Use Apache Tomcat 10.1 for this tested stand')
 environments={name:os.environ|settings(name)for name in SERVICES}
 executable=java_path(args,environments['user'])
 for name,(port,_,_)in SERVICES.items():
  with socket.socket()as listener:
   try:listener.bind(('127.0.0.1',port))
   except OSError:raise RuntimeError(f'Port {port} is busy. Stop the old {name}/IDEA configuration before starting.')
 state={}
 try:
  for name,(port,_,_)in SERVICES.items():
   base=RUN/name
   for child in ['conf','logs','temp','webapps','work','cache']:(base/child).mkdir(parents=True,exist_ok=True)
   for path in(home/'conf').iterdir():
    if path.is_file():shutil.copy2(path,base/'conf'/path.name)
   server=ET.Element('Server',port='-1');service=ET.SubElement(server,'Service',name='Catalina')
   ET.SubElement(service,'Connector',port=str(port),address='127.0.0.1',protocol='HTTP/1.1',connectionTimeout='20000',maxParameterCount='1000')
   engine=ET.SubElement(service,'Engine',name='Catalina',defaultHost='localhost')
   ET.SubElement(engine,'Host',name='localhost',appBase='webapps',unpackWARs='true',autoDeploy='false',deployOnStartup='true')
   ET.ElementTree(server).write(base/'conf/server.xml',encoding='utf-8',xml_declaration=True)
   deployed=base/'webapps/ROOT'
   if deployed.is_symlink():raise RuntimeError('Refusing symlink deployment: '+str(deployed))
   if deployed.exists():shutil.rmtree(deployed)
   shutil.copy2(ROOT/'dist'/(name+'.war'),base/'webapps/ROOT.war')
   env=environments[name]
   env['XDG_CACHE_HOME']=str(base/'cache')
   defaults='-Xms32m -Xmx'+('512m' if name=='documents'else '256m')+' -Dfile.encoding=UTF-8 -Djava.awt.headless=true'
   catalina_options=env.get('CATALINA_OPTS','')
   if name=='bff' and not catalina_options:catalina_options=(LOCAL/'bff.vmoptions.txt').read_text(encoding='utf-8-sig').strip()
   options=shlex.split(env.get('JAVA_OPTS',defaults))+shlex.split(catalina_options)
   options+=['-Dpdfbox.fontcache='+str(base/'cache'),'-Djava.protocol.handler.pkgs=org.apache.catalina.webresources','--add-opens=java.base/java.lang=ALL-UNNAMED','--add-opens=java.base/java.io=ALL-UNNAMED','--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED',f'-Dcatalina.home={home}',f'-Dcatalina.base={base}',f'-Djava.io.tmpdir={base}/temp',f'-Djava.util.logging.config.file={base}/conf/logging.properties','-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager']
   command=[executable,*options,'-classpath',os.pathsep.join([str(home/'bin/bootstrap.jar'),str(home/'bin/tomcat-juli.jar')]),'org.apache.catalina.startup.Bootstrap','start']
   with(base/'logs/console.log').open('ab')as log:
    process=subprocess.Popen(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=os.name!='nt',creationflags=(subprocess.CREATE_NEW_PROCESS_GROUP|subprocess.CREATE_NO_WINDOW) if os.name=='nt'else 0)
   state[name]={'pid':process.pid};atomic_json(STATE,state)
  deadline=time.monotonic()+args.timeout
  while time.monotonic()<deadline:
   ready=True
   for name,record in state.items():
    if not owned(name,record):raise RuntimeError(name+' exited. See local-run/stand/'+name+'/logs/console.log')
    try:_,healthy=probe(name);ready=ready and healthy
    except (OSError,urllib.error.URLError):ready=False
   if ready:print('Ready: http://localhost:8080/ ; admin login: http://localhost:8080/login.jsp');return
   time.sleep(.5)
  raise RuntimeError('Startup timed out. See local-run/stand/<service>/logs/console.log; run status for HTTP details')
 except BaseException:
  stop();raise

def demo(args):
 state=read_state()
 if not all(name in state and owned(name,state[name])for name in SERVICES):raise RuntimeError('Start this package first; refusing to create accounts on an unrelated stand')
 args.user_login=args.user_login.strip().lower();args.admin_login=args.admin_login.strip().lower();args.other_login=args.other_login.strip().lower()
 if len({args.user_login,args.other_login,args.admin_login})!=3 or not all(re.fullmatch('[a-z0-9_.-]{3,64}',name)for name in [args.user_login,args.other_login,args.admin_login]):raise RuntimeError('Specify three different valid demo logins')
 password=os.environ.get('STAND_DEMO_PASSWORD')
 if not password:
  password=getpass.getpass('Password for demo users and admin (not saved): ')
  if password!=getpass.getpass('Repeat password: '):raise RuntimeError('Passwords do not match')
 for role,login in [('USER',args.user_login),('USER',args.other_login),('ADMIN',args.admin_login)]:
  code,data=request(8080,'/api/users/exists?login='+login)
  if code!=200:raise RuntimeError('User check failed; start the stand first')
  if not data['exists']:
   code,data=request(8080,'/api/auth/register','POST',{'login':login,'password':password,'fullName':'Пётр Иванов' if login==args.other_login else 'Иван Петров','birthDate':'2000-05-17'})
   if code!=201:raise RuntimeError('Registration failed: '+str(data))
  code,auth=request(8080,'/api/auth/login','POST',{'login':login,'password':password})
  if code!=200:raise RuntimeError('Existing demo account has another password; no password was changed. Use the correct password.')
  if role=='USER':
   if auth['user']['role']!='USER':raise RuntimeError('demo.user already has another role; no existing role changed')
   token=auth['accessToken']
   for port,path in [(8080,'/api/users/me'),(8081,'/api/users/me'),(8082,'/api/moderation/users/'+auth['user']['id']+'/status'),(8083,'/api/scooters'),(8084,'/api/rides'),(8085,'/api/documents')]:
    code,_=request(port,path,token=token)
    if code!=200:raise RuntimeError(f'Authenticated check failed for port {port}: HTTP {code}')
  elif auth['user']['role']!='ADMIN':
   env=os.environ|settings('user');exe=java_path(args,env);web=RUN/'user/webapps/ROOT/WEB-INF'
   cp=os.pathsep.join([str(web/'classes'),str(web/'lib/*')])
   subprocess.run([exe,'-cp',cp,'ru.scooter.user.cli.PromoteAdmin',login],env=env,check=True,cwd=ROOT)
   code,auth=request(8080,'/api/auth/login','POST',{'login':login,'password':password})
   if code!=200 or auth['user']['role']!='ADMIN':raise RuntimeError('ADMIN verification failed')
 print('Demo accounts ready: '+args.user_login+' / '+args.other_login+' / '+args.admin_login+'; password is the one you just entered. No real payment. Sign in at http://localhost:8080/login.jsp')

def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('action',choices=['prepare','build','start','stop','status','demo'])
 parser.add_argument('--pg-host',default='127.0.0.1');parser.add_argument('--pg-port',type=int,default=5432)
 parser.add_argument('--pg-user',default='postgres');parser.add_argument('--database-prefix',default='kicksharing_')
 parser.add_argument('--user-login',default='demo.user');parser.add_argument('--admin-login',default='demo.admin');parser.add_argument('--other-login',default='demo.other')
 parser.add_argument('--psql');parser.add_argument('--no-sudo',action='store_true');parser.add_argument('--socket-dir',default='/var/run/postgresql');parser.add_argument('--password-auth',action='store_true')
 parser.add_argument('--tomcat-home');parser.add_argument('--java');parser.add_argument('--timeout',type=int,default=90)
 args=parser.parse_args()
 try:
  if args.action=='status':return 0 if status()else 1
  with command_lock():
   if args.action=='prepare':prepare(args)
   elif args.action=='build':build()
   elif args.action=='start':start(args)
   elif args.action=='stop':stop()
   else:demo(args)
  return 0
 except (RuntimeError,OSError,subprocess.CalledProcessError)as error:print('ERROR:',error,file=sys.stderr);return 1
if __name__=='__main__':raise SystemExit(main())
