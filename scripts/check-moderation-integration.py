#!/usr/bin/env python3
"""Real BFF + User + patched Moderation + two disposable PostgreSQL databases.
Scooter/Ride are fixtures; Documents can run real with --documents-project. Requires Docker, Java 17, built WARs,
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
    parser.add_argument('--documents-project', type=Path, help='Also run real Documents with a third disposable database')
    parser.add_argument('--pdf-font', default=os.environ.get('PDF_FONT_PATH', '/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf'))
    args = parser.parse_args()
    user = args.user_project.resolve()
    moderation = args.moderation_project.resolve()
    java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'
    classpath = (ROOT / 'target/test-classpath.txt').read_text().strip()
    for war in (ROOT / 'target/scooter-bff.war', user / 'target/user-service.war', moderation / 'target/moderation.war'):
        if not war.is_file():
            raise SystemExit(f'Build WAR first: {war}')
    documents = args.documents_project.resolve() if args.documents_project else None
    if documents and (not (documents / 'target/documents.war').is_file() or not Path(args.pdf_font).is_file()):
        raise SystemExit('Build Documents and provide --pdf-font pointing to a Cyrillic TTF font')
    name = 'bff-moderation-check-' + uuid.uuid4().hex[:12]
    processes = []
    logs = ROOT / 'target/moderation-integration'
    logs.mkdir(parents=True, exist_ok=True)
    handles = []
    ride_calls = []

    document_fixture = {'mode': 'valid'}

    class Fixture(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            if documents and self.path.startswith('/api/rides'):
                mode = document_fixture['mode']
                if mode.isdigit(): return self.reply(int(mode), {'error': 'Injected Ride failure'})
                if mode == 'malformed': return self.reply(200, {'unexpected': True})
                auth = self.headers.get('Authorization', '').removeprefix('Bearer ')
                owner = json.loads(base64.urlsafe_b64decode(auth.split('.')[1] + '=='))['userId']
                ride = {'id': '7c9e6679-7425-40de-944b-e07fc1f90ae7', 'userId': owner,
                        'numberPlate': 'A123BC', 'status': 'PAID', 'amount': 123.45, 'currency': 'RUB',
                        'startedAt': '2026-10-20T10:00:00Z', 'finishedAt': '2026-10-20T10:20:00Z',
                        'paidAt': '2026-10-20T10:21:00Z'}
                if mode == 'foreign': ride['userId'] = '00000000-0000-4000-8000-000000000000'
                if mode == 'wrong-id': ride['id'] = '00000000-0000-4000-8000-000000000000'
                if mode == 'missing-payment': ride['paidAt'] = None
                if mode == 'unpaid': ride['status'] = 'FINISHED'
                if '?' in self.path:
                    old = ride | {'id': '7c9e6679-7425-40de-944b-e07fc1f90ae8', 'numberPlate': 'OUTSIDE',
                                  'startedAt': '2026-09-01T10:00:00Z'}
                    return self.reply(200, [ride, old])
                return self.reply(200, ride)
            self.reply(200, [])

        def do_POST(self):
            body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
            if documents and self.path == '/__document-fixture':
                document_fixture.update(json.loads(body)); return self.reply(200, {})
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
        if documents:
            sql('postgres', 'CREATE DATABASE documents_check;')
            sql('documents_check', (documents / 'src/main/resources/schema.sql').read_text())
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
            if documents:
                with socket.socket() as reservation:
                    reservation.bind(('127.0.0.1', 0))
                    doc_port = reservation.getsockname()[1]
                doc_url = f'http://127.0.0.1:{doc_port}'
            else:
                doc_url = fixture_url
            env = os.environ | dict(DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/user_check',
                                    DB_USER='postgres', DB_PASSWORD='disposable-test-only',
                                    JWT_PRIVATE_KEY_PATH=private, JWT_PUBLIC_KEY_PATH=public,
                                    BCRYPT_COST='10', MODERATION_BASE_URL=mod_url, CORS_ALLOWED_ORIGINS=mod_url)
            process, lines = start('user-bff', 'RealUserIntegrationServer.java',
                                   [user / 'target/user-service.war', ROOT / 'target/scooter-bff.war'], env,
                                   ['-Dbff.test.context=', '-Dmoderation.panel.url=' + mod_url + '/admin/users.html',
                                    *['-D' + name + '.service.url=' + (doc_url if name == 'document' else fixture_url) for name in ('scooter', 'ride', 'document')]])
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
            if documents:
                doc_env = os.environ | dict(DB_URL=f'jdbc:postgresql://127.0.0.1:{port}/documents_check',
                                            DB_USERNAME='postgres', DB_PASSWORD='disposable-test-only',
                                            JWT_PUBLIC_KEY_PATH=public, USER_SERVICE_URL=urls['USER_URL'],
                                            MODERATION_SERVICE_URL=mod_url, RIDE_SERVICE_URL=fixture_url,
                                            PDF_FONT_PATH=str(Path(args.pdf_font).resolve()))
                _, doc_lines = start('documents', 'IntegrationWarServer.java',
                                     [documents / 'target/documents.war'], doc_env, [f'-Dintegration.port={doc_port}'])
                key, _ = doc_lines.get(timeout=60)
                if key != 'WAR_URL': raise RuntimeError('Documents failed; inspect ' + str(logs))
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
            if documents:
                subprocess.run(['node', str(ROOT / 'scripts/check-documents-browser.cjs')], check=True,
                               env=os.environ | {'BFF_REAL_URL': urls['BFF_URL'], 'USER_REAL_URL': urls['USER_URL'],
                                                 'MODERATION_REAL_URL': mod_url, 'DOCUMENTS_REAL_URL': doc_url,
                                                 'FIXTURE_REAL_URL': fixture_url}, cwd=ROOT)
                summary = subprocess.check_output(['docker', 'exec', name, 'psql', '-U', 'postgres', '-d', 'documents_check',
                                                   '-At', '-c', 'SELECT status, count(*) FROM documents GROUP BY status ORDER BY status'], text=True)
                assert 'PENDING' not in summary and 'GENERATED|' in summary and 'FAILED|' in summary, summary
                print('PASS: real Documents metadata contains GENERATED/FAILED, no stranded PENDING records')
            if documents:
                request = urllib.request.Request(urls['USER_URL'] + '/api/users/login',
                    data=json.dumps({'login': 'integration.user', 'password': 'Integration-password-123'}).encode(),
                    headers={'Content-Type': 'application/json'})
                with urllib.request.urlopen(request, timeout=10) as response:
                    outage_auth = json.load(response)
            process.terminate()
            process.wait(timeout=20)
            if documents:
                request = urllib.request.Request(doc_url + '/api/documents/generate',
                    data=json.dumps({'userId': outage_auth['user']['id'], 'docType': 'USER_HISTORY',
                                     'from': '2026-10-01', 'to': '2026-10-31'}).encode(),
                    headers={'Authorization': 'Bearer ' + outage_auth['accessToken'], 'Content-Type': 'application/json'})
                try:
                    urllib.request.urlopen(request, timeout=8)
                    raise AssertionError('Documents must not accept tokens when User validation is unavailable')
                except urllib.error.HTTPError as error:
                    assert error.code == 503
                print('PASS: direct Documents fails closed with 503 when actual User is stopped')
            payload = base64.urlsafe_b64encode(json.dumps(dict(userId=str(uuid.uuid4()), role='ADMIN')).encode()).decode().rstrip('=')
            request = urllib.request.Request(mod_url + '/api/moderation/reasons',
                                             headers={'Authorization': 'Bearer e30.' + payload + '.fake'})
            try:
                urllib.request.urlopen(request, timeout=8)
                raise AssertionError('Unavailable User must not authorize a request')
            except urllib.error.HTTPError as error:
                assert error.code == 503
            print('PASS: unavailable User returns 503; blocked start made zero Ride calls')
            print('PASS: actual BFF + User + patched Moderation' + (' + Documents' if documents else '') + ' + PostgreSQL; no mock auth or moderation')
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
