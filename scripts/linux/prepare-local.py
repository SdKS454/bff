#!/usr/bin/env python3
"""Prepare a local PostgreSQL/Tomcat stand without Docker or third-party Python packages."""
import argparse
import fcntl
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'local-run'
SCHEMAS = {
    'user': ('services/user/db/001_schema.sql', ['users', 'tokens']),
    'moderation': ('services/moderation/src/main/resources/schema.sql', ['reasons', 'blocked_users']),
    'scooter': ('services/scooter/db.sql', ['scooter', 'scooter_reservations']),
    'ride': ('services/ride/db/schema.sql', ['rates', 'rides']),
    'documents': ('services/documents/src/main/resources/schema.sql', ['documents']),
}


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def identifier(value):
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,62}', value):
        raise RuntimeError('Use lowercase letters, digits and underscores for database/role names.')
    return '"' + value + '"'


def write_private(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name('.' + path.name + '.' + secrets.token_hex(4) + '.tmp')
    try:
        with open(temporary, 'w', encoding='utf-8', opener=lambda p, flags: os.open(p, flags, 0o600)) as output:
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        path.chmod(0o600)
    finally:
        temporary.unlink(missing_ok=True)


def psql(args, database, sql, *, admin=False, credentials=None, admin_password=None):
    environment = os.environ.copy()
    for key in ('PGPASSWORD', 'PGSERVICE', 'PGSERVICEFILE', 'PGOPTIONS'):
        environment.pop(key, None)
    environment.update(PGCLIENTENCODING='UTF8', PGOPTIONS='-c search_path=public', PGCONNECT_TIMEOUT='8')
    command = [args.psql, '-X', '--no-password', '-qAt', '-v', 'ON_ERROR_STOP=1', '-p', str(args.pg_port), '-d', database]
    if admin and not args.password_auth:
        command += ['-h', args.socket_dir, '-U', args.admin_user]
        if not args.no_sudo:
            command = ['sudo', '-n', '-u', 'postgres', '--'] + command
    else:
        command += ['-h', args.pg_host, '-U', args.admin_user if admin else credentials['user']]
        environment['PGPASSWORD'] = admin_password if admin else credentials['password']
    result = subprocess.run(command, input=sql, text=True, encoding='utf-8', errors='replace',
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=environment, cwd='/')
    if result.returncode:
        # SQL is sent through stdin, never command-line arguments. Do not log credentials.
        raise RuntimeError('PostgreSQL request failed for ' + database + ':\n' + result.stderr.strip())
    return result.stdout.strip()


def prepare(args):
    if os.geteuid() == 0:
        raise RuntimeError('Run this script as your normal user, without sudo. It uses sudo only for PostgreSQL administration.')
    args.psql = shutil.which(args.psql)
    if not args.psql:
        raise RuntimeError('psql was not found. Install postgresql-client or specify --psql /full/path/to/psql.')
    # Keep the name psql: on Ubuntu it is a symlink whose wrapper dispatches by argv[0].
    args.psql = os.path.abspath(args.psql)
    args.socket_dir = str(Path(args.socket_dir).resolve())
    candidates = [args.java] if args.java else []
    if not candidates and os.environ.get('JAVA_HOME'):
        candidates.append(str(Path(os.environ['JAVA_HOME']) / 'bin/java'))
    if not candidates:
        candidates.extend(str(p) for p in sorted(Path('/usr/lib/jvm').glob('java-17-*/bin/java')))
    if not candidates:
        candidates.append('java')
    executable = shutil.which(candidates[0])
    if not executable:
        raise RuntimeError('JDK 17+ was not found. Install openjdk-17-jdk or specify --java /full/path/to/java.')
    executable = str(Path(executable).resolve())
    version = subprocess.run([executable, '--version'], capture_output=True, text=True)
    match = re.search(r'^(?:openjdk|java) (\d+)', version.stdout)
    if version.returncode or not match or int(match[1]) < 17:
        raise RuntimeError('JDK 17+ is required; JDK 17 is recommended.')
    java_home = str(Path(executable).parent.parent)
    fonts = [Path(args.font_path)] if args.font_path else [
        Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'),
        Path('/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf'),
    ]
    font = next((p.resolve() for p in fonts if p.is_file()), None)
    if font is None:
        raise RuntimeError('Install fonts-dejavu-core or specify --font-path /path/to/CyrillicFont.ttf.')
    for relative, _ in SCHEMAS.values():
        if not (ROOT / relative).is_file():
            raise RuntimeError('Missing schema: ' + relative + '. Run from the complete source project.')
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,35}', args.database_prefix):
        raise RuntimeError('Invalid --database-prefix; use lowercase letters, digits and underscores.')
    if args.pg_host not in ('127.0.0.1', 'localhost', '::1'):
        raise RuntimeError('This helper prepares a local PostgreSQL instance; use a loopback --pg-host.')
    if not 1 <= args.pg_port <= 65535:
        raise RuntimeError('Invalid --pg-port.')
    admin_password = None
    if args.password_auth:
        admin_password = getpass.getpass('PostgreSQL administrator password (not saved): ')
    elif not args.no_sudo:
        if not shutil.which('sudo'):
            raise RuntimeError('sudo is required for the default local PostgreSQL administrator login.')
        subprocess.run(['sudo', '-v'], check=True)
    admin = lambda db, sql: psql(args, db, sql, admin=True, admin_password=admin_password)
    info = json.loads(admin('postgres', "SELECT json_build_object('version',current_setting('server_version'),'number',current_setting('server_version_num'),'port',inet_server_port());"))
    print('Connected to PostgreSQL ' + info['version'] + '; requested port ' + str(args.pg_port), flush=True)
    # All current schemas use built-in gen_random_uuid(), available since PostgreSQL 13.
    if int(info['number']) < 130000:
        raise RuntimeError('This native Linux helper requires PostgreSQL 13+; 16+ is recommended for a new installation.')
    OUTPUT.mkdir(mode=0o700, exist_ok=True)
    OUTPUT.chmod(0o700)
    with (OUTPUT / '.prepare.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise RuntimeError('Another preparation is already running for this project.')
        state_file = OUTPUT / 'linux-database.json'
        if state_file.exists():
            state = json.loads(state_file.read_text())
            if not isinstance(state, dict) or not isinstance(state.get('user'), str) or not isinstance(state.get('password'), str) or not re.fullmatch('[0-9a-f]{64}', state['password']):
                raise RuntimeError('Invalid local-run/linux-database.json. Restore the original managed credentials.')
            for key, value in (('host', args.pg_host), ('port', args.pg_port), ('prefix', args.database_prefix)):
                if state.get(key) != value:
                    raise RuntimeError('Existing local-run/linux-database.json uses different ' + key + '. Reuse its connection parameters or use a separate project copy for another stand.')
            credentials = state
            identifier(credentials['user'])
        else:
            credentials = dict(host=args.pg_host, port=args.pg_port, prefix=args.database_prefix,
                               user='kicksharing_' + secrets.token_hex(4), password=secrets.token_hex(32))
            write_private(state_file, json.dumps(credentials, indent=2) + '\n')
        role = credentials['user']
        if admin('postgres', 'SELECT count(*) FROM pg_roles WHERE rolname=' + literal(role) + ';') == '0':
            admin('postgres', "SET password_encryption='scram-sha-256'; CREATE ROLE " + identifier(role) + ' LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD ' + literal(credentials['password']) + ';')
        app = lambda db, sql: psql(args, db, sql, credentials=credentials)
        app('postgres', 'SELECT 1;')  # Verify that TCP reaches the same managed role before creating databases.
        for service, (relative, required) in SCHEMAS.items():
            database = args.database_prefix + service
            owner = admin('postgres', 'SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=' + literal(database) + ';')
            if owner and owner != role:
                raise RuntimeError('Database ' + database + ' already belongs to another role. Restore the matching local-run credentials or choose a different --database-prefix in a separate project copy. No ownership or tables were changed.')
            if not owner:
                admin('postgres', 'CREATE DATABASE ' + identifier(database) + ' OWNER ' + identifier(role) + ';')
            count = app(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';")
            if count == '0':
                schema = (ROOT / relative).read_text(encoding='utf-8-sig')
                # User's original schema already contains BEGIN/COMMIT.
                app(database, schema if service == 'user' else 'BEGIN;\n' + schema + '\nCOMMIT;')
            else:
                known = app(database, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN (" + ','.join(literal(t) for t in required) + ');')
                if int(known) != len(required):
                    raise RuntimeError('Unknown/incomplete schema in ' + database + '. No tables were deleted.')
            for migration in sorted((ROOT / 'local-stack/migrations' / service).glob('*.sql')):
                checksum = hashlib.sha256(migration.read_bytes()).hexdigest()
                name = literal(migration.name)
                sql = "BEGIN;\nSELECT pg_advisory_xact_lock(27102026);\nCREATE TABLE IF NOT EXISTS schema_migrations(version TEXT PRIMARY KEY,checksum TEXT NOT NULL,applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW());\n"
                sql += 'SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE version=' + name + ') AS applied \\gset\n\\if :applied\n'
                sql += "DO $migration_check$ BEGIN IF EXISTS(SELECT 1 FROM schema_migrations WHERE version=" + name + " AND checksum<>" + literal(checksum) + ") THEN RAISE EXCEPTION 'Applied migration has changed: restore the original file and add a new version'; END IF; END; $migration_check$;\n\\else\n"
                sql += migration.read_text(encoding='utf-8-sig') + '\nINSERT INTO schema_migrations(version,checksum) VALUES(' + name + ',' + literal(checksum) + ');\n\\endif\nCOMMIT;'
                app(database, sql)
            print('Database ready: ' + database, flush=True)
        key_dir = OUTPUT / 'secrets'
        key_dir.mkdir(mode=0o700, exist_ok=True)
        key_dir.chmod(0o700)
        private, public = key_dir / 'private.pem', key_dir / 'public.pem'
        if private.exists() != public.exists():
            raise RuntimeError('Incomplete RSA key pair. Restore the missing key; existing keys are not overwritten.')
        if not private.exists():
            subprocess.run([executable, str(ROOT / 'services/user/src/main/java/ru/scooter/user/cli/GenerateKeys.java'), str(key_dir)], check=True)
        service_file = key_dir / 'service.key'
        if not service_file.exists():
            write_private(service_file, secrets.token_hex(32))
        service_key = service_file.read_text().strip()
        if not re.fullmatch('[0-9a-f]{64}', service_key):
            raise RuntimeError('Invalid existing service.key. Restore the original file.')
        host = '[' + args.pg_host + ']' if ':' in args.pg_host else args.pg_host
        settings = {}
        for service in SCHEMAS:
            settings[service] = dict(DB_URL='jdbc:postgresql://' + host + ':' + str(args.pg_port) + '/' + args.database_prefix + service + '?currentSchema=public',
                                     DB_USER=role, DB_PASSWORD=credentials['password'])
        settings['user'].update(JWT_PRIVATE_KEY_PATH=str(private), JWT_PUBLIC_KEY_PATH=str(public),
                                MODERATION_BASE_URL='http://localhost:8082', CORS_ALLOWED_ORIGINS='http://localhost:8082')
        settings['moderation'].update(USER_BASE_URL='http://localhost:8081', ADMIN_USER_BASE_URL='http://localhost:8081', BFF_LOGIN_URL='http://localhost:8080/login.jsp')
        for service in ('scooter', 'ride', 'documents'):
            settings[service]['USER_SERVICE_URL'] = 'http://localhost:8081'
        for service in ('scooter', 'ride'):
            settings[service]['SERVICE_API_KEY'] = service_key
        settings['ride'].update(SCOOTER_SERVICE_URL='http://localhost:8083', PAYMENT_MODE='educational')
        settings['documents'].update(MODERATION_SERVICE_URL='http://localhost:8082', RIDE_SERVICE_URL='http://localhost:8084', JWT_PUBLIC_KEY_PATH=str(public), PDF_FONT_PATH=str(font))
        vm_options = '-Duser.service.url=http://localhost:8081 -Dscooter.service.url=http://localhost:8083 -Dride.service.url=http://localhost:8084 -Ddocument.service.url=http://localhost:8085'
        settings['bff'] = dict(MODERATION_PANEL_URL='http://localhost:8082/admin/users.html', CATALINA_OPTS=vm_options)
        for service, values in settings.items():
            values.update(JAVA_HOME=java_home, JRE_HOME=java_home, JAVA_OPTS='-Xms32m -Xmx' + ('512m' if service == 'documents' else '256m') + ' -Dfile.encoding=UTF-8 -Djava.awt.headless=true')
            write_private(OUTPUT / (service + '.env.txt'), ''.join(k + '=' + v + '\n' for k, v in values.items()))
            write_private(OUTPUT / (service + '.env.sh'), '# Generated local configuration; contains secrets.\n' + ''.join('export ' + k + '=' + shlex.quote(v) + '\n' for k, v in values.items()))
            write_private(OUTPUT / (service + '.env.json'), json.dumps(values, ensure_ascii=False, indent=2) + '\n')
        write_private(OUTPUT / 'bff.vmoptions.txt', vm_options + '\n')
        print('Ready. Settings: ' + str(OUTPUT) + '/*.env.txt, *.env.sh, *.env.json', flush=True)
        print('For example: source ./local-run/user.env.sh; then start that service in its own Tomcat.', flush=True)
        print('Keep local-run private: it contains the generated DB password and RSA/service keys.', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pg-host', default='127.0.0.1')
    parser.add_argument('--pg-port', type=int, default=5432)
    parser.add_argument('--database-prefix', default='kicksharing_')
    parser.add_argument('--admin-user', default='postgres')
    parser.add_argument('--psql', default='psql')
    parser.add_argument('--java')
    parser.add_argument('--font-path')
    parser.add_argument('--socket-dir', default='/var/run/postgresql')
    parser.add_argument('--no-sudo', action='store_true', help='For an existing local cluster administrable by the current OS user.')
    parser.add_argument('--password-auth', action='store_true', help='Prompt for the PostgreSQL administrator TCP password instead of using sudo/peer.')
    args = parser.parse_args()
    try:
        prepare(args)
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        print('ERROR: ' + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
