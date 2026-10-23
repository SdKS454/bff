#!/usr/bin/env python3
"""Real BFF + User + patched Moderation + two disposable PostgreSQL databases.
Only Scooter/Ride/Documents are fixtures. Requires Docker, Java 17, built WARs,
BFF target/test-classpath.txt and npm ci; never connects to an existing database.
"""
import argparse
import base64
import http.server
import json
import os
from pathlib import Path
import queue
import socket
import subprocess
import tempfile
import threading
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--user-project', required=True, type=Path)
    parser.add_argument('--moderation-project', required=True, type=Path)
    args = parser.parse_args()
    user = args.user_project.resolve()
    moderation = args.moderation_project.resolve()
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    classpath = (ROOT / 'target/test-classpath.txt').read_text().strip()
    for war in (ROOT / 'target/scooter-bff.war', user / 'target/user-service.war', moderation / 'target/moderation.war'):
        if not war.is_file():
            raise SystemExit(f'Build WAR first: {war}')
    name = 'bff-moderation-check-' + uuid.uuid4().hex[:12]
    processes = []
    logs = ROOT / 'target/moderation-integration'
    logs.mkdir(parents=True, exist_ok=True)
    handles = []
    ride_calls = []

    class Fixture(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            self.reply(200, [])

        def do_POST(self):
            self.rfile.read(int(self.headers.get('Content-Length', '0')))
            ride_calls.append(self.path)
            self.reply(201, {'id': str(uuid.uuid4()), 'status': 'ACTIVE'})

        def reply(self, status, data):
            body = json.dumps(data).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    fixture = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
    threading.Thread(target=fixture.serve_forever, daemon=True).start()

    def sql(database, command):
        return subprocess.run(['docker', 'exec', '-i', name, 'psql', '-U', 'postgres', '-d', database,
                               '-v', 'ON_ERROR_STOP=1'], input=command, text=True, check=True,
                              stdout=subprocess.DEVNULL)

    def start(label, source, war_args, env, properties=()):
        log = open(logs / (label + '.log'), 'w')
        handles.append(log)
        proc = subprocess.Popen([java, '--add-opens=java.base/java.io=ALL-UNNAMED',
                                 '--add-opens=java.base/java.lang=ALL-UNNAMED',
                                 '--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED',
                                 *properties, '-cp', classpath, str(ROOT / 'scripts' / source),
                                 *map(str, war_args)], env=env, stdout=subprocess.PIPE, stderr=log, text=True)
        processes.append(proc)
        lines = queue.Queue()

        def reader():
            for line in proc.stdout:
                if line.startswith(('USER_URL=', 'BFF_URL=', 'WAR_URL=')):
                    lines.put(line.strip().split('=', 1))
            lines.put(('EXIT', str(proc.poll())))
        threading.Thread(target=reader, daemon=True).start()
        return proc, lines

    try:
        subprocess.run(['docker', 'run', '-d', '--name', name,
                        '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', '-p', '127.0.0.1::5432', 'postgres:16'],
                       check=True, stdout=subprocess.DEVNULL)
        # Readiness is bounded and checks the actual database rather than sleeping blindly.
        for _ in range(100):
            ready = subprocess.run(['docker', 'exec', name, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if ready.returncode == 0:
                break
            threading.Event().wait(.2)
        else:
            raise RuntimeError('Disposable PostgreSQL did not become ready')
        port = subprocess.check_output(['docker', 'port', name, '5432/tcp'], text=True).strip().rsplit(':', 1)[1]
        sql('postgres', 'CREATE DATABASE user_check; CREATE DATABASE moderation_check;')
        sql('user_check', (user / 'db/001_schema.sql').read_text())
        sql('moderation_check', (moderation / 'src/main/resources/schema.sql').read_text())
        with tempfile.TemporaryDirectory(prefix='bff-moderation-keys-') as temp:
            private, public = str(Path(temp) / 'private.pem'), str(Path(temp) / 'public.pem')
            subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', private],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.run(['openssl', 'pkey', '-in', private, '-pubout', '-out', public], check=True, stdout=subprocess.DEVNULL)
            with socket.socket() as reservation:
                reservation.bind(('127.0.0.1', 0))
                mod_port = reservation.getsockname()[1]
            mod_url = f'http://127.0.0.1:{mod_port}'
            fixture_url = f'http://127.0.0.1:{fixture.server_port}'
            env = os.environ | dict(DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/user_check',
                                    DB_USER='postgres', DB_PASSWORD='disposable-test-only',
                                    JWT_PRIVATE_KEY_PATH=private, JWT_PUBLIC_KEY_PATH=public,
                                    BCRYPT_COST='10', MODERATION_BASE_URL=mod_url, CORS_ALLOWED_ORIGINS=mod_url)
            process, lines = start('user-bff', 'RealUserIntegrationServer.java',
                                   [user / 'target/user-service.war', ROOT / 'target/scooter-bff.war'], env,
                                   ['-Dbff.test.context=', '-Dmoderation.panel.url=' + mod_url + '/admin/users.html',
                                    *['-D' + name + '.service.url=' + fixture_url for name in ('scooter', 'ride', 'document')]])
            urls = {}
            while len(urls) < 2:
                key, value = lines.get(timeout=60)
                if key == 'EXIT':
                    raise RuntimeError('User/BFF failed; inspect ' + str(logs))
                urls[key] = value
            mod_env = env | dict(DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/moderation_check',
                                 USER_BASE_URL=urls['USER_URL'], ADMIN_USER_BASE_URL=urls['USER_URL'],
                                 BFF_LOGIN_URL=urls['BFF_URL'] + '/login.jsp')
            _, mod_lines = start('moderation', 'IntegrationWarServer.java',
                                 [moderation / 'target/moderation.war'], mod_env, [f'-Dintegration.port={mod_port}'])
            key, _ = mod_lines.get(timeout=60)
            if key != 'WAR_URL':
                raise RuntimeError('Moderation failed; inspect ' + str(logs))
            for login in ('integration.admin', 'integration.user'):
                data = json.dumps(dict(login=login, password='Integration-password-123',
                                       fullName=login, birthDate='2000-01-01')).encode()
                request = urllib.request.Request(urls['USER_URL'] + '/api/users/register', data=data,
                                                 headers={'Content-Type': 'application/json'})
                with urllib.request.urlopen(request, timeout=15) as response:
                    assert response.status == 201
            # Fixture setup in a disposable database only; production role assignment uses PromoteAdmin CLI.
            sql('user_check', "UPDATE users SET role='ADMIN' WHERE login='integration.admin';")
            subprocess.run(['node', str(ROOT / 'scripts/check-moderation-browser.cjs')], check=True,
                           env=os.environ | {'BFF_REAL_URL': urls['BFF_URL'], 'USER_REAL_URL': urls['USER_URL'], 'MODERATION_REAL_URL': mod_url}, cwd=ROOT)
            assert ride_calls == ['/api/rides'], 'Blocked start must never reach Ride'
            process.terminate()
            process.wait(timeout=20)
            payload = base64.urlsafe_b64encode(json.dumps(dict(userId=str(uuid.uuid4()), role='ADMIN')).encode()).decode().rstrip('=')
            request = urllib.request.Request(mod_url + '/api/moderation/reasons',
                                             headers={'Authorization': 'Bearer e30.' + payload + '.fake'})
            try:
                urllib.request.urlopen(request, timeout=8)
                raise AssertionError('Unavailable User must not authorize a request')
            except urllib.error.HTTPError as error:
                assert error.code == 503
            print('PASS: unavailable User returns 503; blocked start made zero Ride calls')
            print('PASS: actual BFF + User + patched Moderation + PostgreSQL; no mock auth or moderation')
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
        for handle in handles:
            handle.close()
        fixture.shutdown()
        fixture.server_close()
        subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
