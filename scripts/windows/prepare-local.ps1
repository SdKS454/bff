#Requires -Version 5.1
[CmdletBinding()]
param(
    [string]$PgHost = 'localhost',
    [ValidateRange(1,65535)][int]$PgPort = 5432,
    [string]$PgUser = 'postgres',
    [ValidatePattern('^[a-z][a-z0-9_]{0,35}$')][string]$DatabasePrefix = 'kicksharing_',
    [string]$PsqlPath,
    [string]$JavaPath,
    [string]$FontPath,
    [Security.SecureString]$DatabasePassword
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output = Join-Path $root 'local-run'
$utf8 = New-Object Text.UTF8Encoding($false)

function Write-Utf8([string]$Path, [string]$Content) {
    [IO.File]::WriteAllText($Path, $Content, $utf8)
}
function Invoke-Psql([string]$Database, [string]$Sql, [string]$File) {
    $parameters = @('-X', '--no-password', '--host', $PgHost, '--port', "$PgPort",
        '--username', $PgUser, '--dbname', $Database, '--set', 'ON_ERROR_STOP=1', '-At')
    if ($File) { $parameters += @('--file', $File) } else { $parameters += @('--command', $Sql) }
    $result = @(& $PsqlPath @parameters)
    if ($LASTEXITCODE -ne 0) { throw "psql failed for $Database (exit $LASTEXITCODE). No databases were deleted." }
    return ($result -join "`n").Trim()
}

if (!$PsqlPath) {
    $command = Get-Command psql -ErrorAction SilentlyContinue
    if ($command) { $PsqlPath = $command.Source }
    elseif ($env:ProgramFiles) {
        $candidates = @(Get-ChildItem -Path "$env:ProgramFiles/PostgreSQL/*/bin/psql.exe" -ErrorAction SilentlyContinue | Sort-Object FullName -Descending)
        if ($candidates.Count) { $PsqlPath = $candidates[0].FullName }
    }
}
if (!$PsqlPath -or !(Test-Path -LiteralPath $PsqlPath)) {
    throw 'PostgreSQL psql was not found. Install PostgreSQL 16+ or pass -PsqlPath "C:/Program Files/PostgreSQL/16/bin/psql.exe".'
}
if (!$JavaPath) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME/bin/java.exe")) { $JavaPath = "$env:JAVA_HOME/bin/java.exe" }
    else {
        $command = Get-Command java -ErrorAction SilentlyContinue
        if ($command) { $JavaPath = $command.Source }
    }
}
if (!$JavaPath) { throw 'Java was not found. Install JDK 17+ and reopen PowerShell, or pass -JavaPath.' }
$javaVersion = @(& $JavaPath --version)
if ($LASTEXITCODE -ne 0 -or !$javaVersion.Count -or $javaVersion[0] -notmatch '^(?:openjdk|java) ([0-9]+)' -or [int]$Matches[1] -lt 17) {
    throw 'JDK 17+ is required to generate the RSA keys.'
}
if (!$FontPath -and $env:WINDIR) { $FontPath = Join-Path $env:WINDIR 'Fonts/arial.ttf' }
if (!$FontPath -or !(Test-Path -LiteralPath $FontPath -PathType Leaf)) {
    throw 'A Cyrillic TTF font is required. Pass -FontPath "C:/Windows/Fonts/arial.ttf" or the path to DejaVuSans.ttf.'
}
$FontPath = [IO.Path]::GetFullPath($FontPath)
if (!$DatabasePassword) { $DatabasePassword = Read-Host "PostgreSQL password for $PgUser" -AsSecureString }
$credential = New-Object Management.Automation.PSCredential($PgUser, $DatabasePassword)
$password = $credential.GetNetworkCredential().Password
if ($password -match '[\r\n]') { throw 'Use a PostgreSQL password without newline characters.' }
$previousPassword = $env:PGPASSWORD
$previousEncoding = $env:PGCLIENTENCODING
$env:PGPASSWORD = $password
$env:PGCLIENTENCODING = 'UTF8'
try {
    $connectionJson = Invoke-Psql 'postgres' "SELECT json_build_object('version',current_setting('server_version'),'number',current_setting('server_version_num'),'address',inet_server_addr(),'port',inet_server_port());" ''
    try {
        $connection = $connectionJson | ConvertFrom-Json -ErrorAction Stop
        $versionNumber = 0
        if (![int]::TryParse([string]$connection.number, [ref]$versionNumber)) { throw 'Invalid version number' }
    } catch {
        throw 'Cannot read PostgreSQL server version from psql output. Check that -PsqlPath points to PostgreSQL psql.exe.'
    }
    Write-Host "Connected to PostgreSQL $($connection.version) at $($connection.address):$($connection.port) (requested ${PgHost}:${PgPort}; user $PgUser)."
    if ($versionNumber -lt 160000) {
        throw "Connected server is PostgreSQL $($connection.version) at $($connection.address):$($connection.port). This setup requires PostgreSQL 16+. If pgAdmin shows a newer version, compare its Connection host/port and pass those exact values with -PgHost and -PgPort. No project databases were changed."
    }
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $secrets = Join-Path $output 'secrets'
    New-Item -ItemType Directory -Force -Path $secrets | Out-Null
    $privateKey = Join-Path $secrets 'private.pem'
    $publicKey = Join-Path $secrets 'public.pem'
    if ((Test-Path $privateKey) -xor (Test-Path $publicKey)) {
        throw 'Incomplete RSA key pair in local-run/secrets. Restore the missing file; existing keys will not be overwritten.'
    }
    if (!(Test-Path $privateKey)) {
        & $JavaPath (Join-Path $root 'services/user/src/main/java/ru/scooter/user/cli/GenerateKeys.java') $secrets
        if ($LASTEXITCODE -ne 0) { throw 'RSA key generation failed.' }
    }
    $serviceKeyFile = Join-Path $secrets 'service.key'
    if (!(Test-Path $serviceKeyFile)) {
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        Write-Utf8 $serviceKeyFile ([BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant())
    }
    $serviceKey = [IO.File]::ReadAllText($serviceKeyFile).Trim()
    if ($serviceKey -notmatch '^[0-9a-f]{64}$') { throw 'Invalid existing local-run/secrets/service.key. Restore the original key.' }

    $schemas = [ordered]@{
        user = 'services/user/db/001_schema.sql'
        moderation = 'services/moderation/src/main/resources/schema.sql'
        scooter = 'services/scooter/db.sql'
        ride = 'services/ride/db/schema.sql'
        documents = 'services/documents/src/main/resources/schema.sql'
    }
    $tables = @{
        user = @('users','tokens'); moderation = @('reasons','blocked_users')
        scooter = @('scooter','scooter_reservations'); ride = @('rates','rides'); documents = @('documents')
    }
    $migrationChecksums = Get-Content -Raw (Join-Path $root 'local-stack/migration-checksums.json') | ConvertFrom-Json
    foreach ($service in $schemas.Keys) {
        $database = $DatabasePrefix + $service
        if ((Invoke-Psql 'postgres' "SELECT count(*) FROM pg_database WHERE datname='$database';" '') -eq '0') {
            Invoke-Psql 'postgres' "CREATE DATABASE $database;" '' | Out-Null
        }
        $tableCount = Invoke-Psql $database "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';" ''
        if ($tableCount -eq '0') {
            Invoke-Psql $database '' (Join-Path $root $schemas[$service]) | Out-Null
        } else {
            $names = ($tables[$service] | ForEach-Object { "'$_'" }) -join ','
            $known = Invoke-Psql $database "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ($names);" ''
            if ([int]$known -ne $tables[$service].Count) {
                throw "Database $database has an unknown/incomplete schema. Choose another -DatabasePrefix or inspect it manually. No tables were dropped."
            }
        }
        $migrationDirectory = Join-Path $root "local-stack/migrations/$service"
        if (Test-Path $migrationDirectory) {
            foreach ($migration in @(Get-ChildItem $migrationDirectory -Filter '*.sql' | Sort-Object Name)) {
                $checksum = (Get-FileHash -LiteralPath $migration.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
                $body = [IO.File]::ReadAllText($migration.FullName)
                $sql = @"
BEGIN;
SELECT pg_advisory_xact_lock(27102026);
CREATE TABLE IF NOT EXISTS schema_migrations(version TEXT PRIMARY KEY,checksum TEXT NOT NULL,applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW());
SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE version='$($migration.Name)') AS applied \gset
\if :applied
SELECT 1 / CASE WHEN checksum='$checksum' THEN 1 ELSE 0 END FROM schema_migrations WHERE version='$($migration.Name)';
\else
$body
INSERT INTO schema_migrations(version,checksum) VALUES('$($migration.Name)','$checksum');
\endif
COMMIT;
"@
                $temporary = Join-Path $output 'migration.tmp.sql'
                try {
                    Write-Utf8 $temporary $sql
                    Invoke-Psql $database '' $temporary | Out-Null
                } finally { Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue }
            }
        }
        Write-Host "Database ready: $database"
    }
    $settings = [ordered]@{}
    foreach ($service in $schemas.Keys) {
        $settings[$service] = [ordered]@{
            DB_URL = "jdbc:postgresql://${PgHost}:${PgPort}/$DatabasePrefix$service"
            DB_USER = $PgUser; DB_PASSWORD = $password
        }
    }
    $settings.user.JWT_PRIVATE_KEY_PATH = $privateKey
    $settings.user.JWT_PUBLIC_KEY_PATH = $publicKey
    $settings.user.MODERATION_BASE_URL = 'http://localhost:8082'
    $settings.user.CORS_ALLOWED_ORIGINS = 'http://localhost:8082'
    $settings.moderation.USER_BASE_URL = 'http://localhost:8081'
    $settings.moderation.ADMIN_USER_BASE_URL = 'http://localhost:8081'
    $settings.moderation.BFF_LOGIN_URL = 'http://localhost:8080/login.jsp'
    foreach ($service in @('scooter','ride','documents')) { $settings[$service].USER_SERVICE_URL = 'http://localhost:8081' }
    foreach ($service in @('scooter','ride')) { $settings[$service].SERVICE_API_KEY = $serviceKey }
    $settings.ride.SCOOTER_SERVICE_URL = 'http://localhost:8083'
    $settings.ride.PAYMENT_MODE = 'educational'
    $settings.documents.MODERATION_SERVICE_URL = 'http://localhost:8082'
    $settings.documents.RIDE_SERVICE_URL = 'http://localhost:8084'
    $settings.documents.JWT_PUBLIC_KEY_PATH = $publicKey
    $settings.documents.PDF_FONT_PATH = $FontPath
    $settings.bff = [ordered]@{MODERATION_PANEL_URL = 'http://localhost:8082/admin/users.html'}
    foreach ($service in $settings.Keys) {
        $values = $settings[$service]
        $lines = @($values.Keys | ForEach-Object { "$_=$($values[$_])" })
        Write-Utf8 (Join-Path $output "$service.env.txt") (($lines -join "`r`n") + "`r`n")
        Write-Utf8 (Join-Path $output "$service.env.json") ($values | ConvertTo-Json)
    }
    Write-Utf8 (Join-Path $output 'bff.vmoptions.txt') '-Duser.service.url=http://localhost:8081 -Dscooter.service.url=http://localhost:8083 -Dride.service.url=http://localhost:8084 -Ddocument.service.url=http://localhost:8085'
    Write-Host "Ready. Environment settings: $output/*.env.txt; BFF VM options: local-run/bff.vmoptions.txt"
    Write-Host 'Start six separate Tomcat 10.1 configurations with context /; see docs/WINDOWS_NO_DOCKER.md.'
    Write-Host 'local-run contains passwords and private keys: keep it local and do not send it to colleagues.'
} finally {
    if ($null -eq $previousPassword) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue } else { $env:PGPASSWORD = $previousPassword }
    if ($null -eq $previousEncoding) { Remove-Item Env:PGCLIENTENCODING -ErrorAction SilentlyContinue } else { $env:PGCLIENTENCODING = $previousEncoding }
}
