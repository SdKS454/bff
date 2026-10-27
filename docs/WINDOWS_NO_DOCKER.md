# Windows: весь проект без Docker

Этот вариант запускает PostgreSQL непосредственно в Windows, а шесть приложений —
в отдельных Tomcat через IntelliJ IDEA. Docker, WSL и Node.js не нужны.

## 1. Что установить

- **JDK 17+**, например Temurin 17. В IDEA выберите этот JDK для проекта и Tomcat.
- **PostgreSQL 16+ для Windows**: https://www.postgresql.org/download/windows/ .
  При установке оставьте порт **5432**, запомните пароль пользователя **postgres**.
  Компонент Command Line Tools (psql) нужен; pgAdmin удобен, Stack Builder необязателен.
  Служба PostgreSQL должна быть запущена (Win+R → services.msc).
- **Apache Tomcat 10.1**, ZIP: https://tomcat.apache.org/download-10.cgi .
  Распакуйте, например, в `C:/tools/apache-tomcat-10.1`. Нужна именно ветка 10.1,
  не Tomcat 9. Для встроенного запуска Tomcat из IDEA нужна поддержка application servers;
  она есть в IntelliJ IDEA Ultimate. Если такой поддержки нет, см. ручной запуск ниже.
- **Maven 3.9+**, только если собираете WAR самостоятельно. Можно использовать Maven IDEA.

Не устанавливайте второй PostgreSQL, если подходящий уже работает: используйте его
порт и пароль. Базы подготовки имеют отдельные имена с префиксом `kicksharing_`.

## 2. Автоматическая подготовка БД, ключей и настроек

Скачайте актуальную временную ветку, распакуйте и откройте папку с `compose.yaml`,
`pom.xml`, `scripts/` и `services/`. Имя compose.yaml здесь просто помогает найти
корень проекта; Docker не вызывается.

В адресной строке Проводника этой папки введите `powershell` и нажмите Enter.
Выполните:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\prepare-local.ps1
```

Скрипт спросит пароль **установленного PostgreSQL-пользователя postgres**. Это
пароль, выбранный вами в установщике PostgreSQL, а не пароль регистрации на сайте.
Он не отображается при вводе. `Bypass` действует только для этого процесса запуска.

Если инструменты не найдены автоматически, укажите реальные пути и порт:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\prepare-local.ps1 -PsqlPath "C:\Program Files\PostgreSQL\16\bin\psql.exe" -JavaPath "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe" -PgPort 5432 -FontPath "C:\Windows\Fonts\arial.ttf"
```

Путь JDK в примере условный: замените на путь вашей установленной версии.
Можно также задать `-PgUser`, `-PgHost` и другой `-DatabasePrefix`, например
`coursework_`. Нужен PostgreSQL-пользователь с правом CREATE DATABASE.

Скрипт:

1. Проверяет PostgreSQL, JDK и файл шрифта.
2. Создаёт `kicksharing_user`, `kicksharing_moderation`, `kicksharing_scooter`,
   `kicksharing_ride`, `kicksharing_documents`, схемы и примеры самокатов.
3. Применяет версионные миграции с транзакциями, блокировкой и проверкой SHA-256.
4. Создаёт одну RSA-пару и общий случайный ключ Ride/Scooter.
5. Сохраняет готовые значения в **`local-run/`**:

```text
local-run/
  user.env.txt
  moderation.env.txt
  scooter.env.txt
  ride.env.txt
  documents.env.txt
  bff.env.txt
  bff.vmoptions.txt
  *.env.json
  secrets/private.pem
  secrets/public.pem
  secrets/service.key
```

`*.env.txt` — строки NAME=value для переноса в Environment variables соответствующего
Tomcat. `*.env.json` — те же значения в структурированном виде, без неоднозначного
разбора специальных символов пароля. IDEA не обязана загружать эти файлы автоматически:
откройте файл и перенесите значения в таблицу переменных нужной конфигурации.
Если пароль содержит `;`, задайте его отдельным значением в таблице, а не строкой
из нескольких переменных через точку с запятой. BFF VM options берутся из отдельного файла.

Повторный запуск сохраняет существующие БД, пользователей и ключи; старые таблицы
не удаляет. Неизвестная/неполная схема или изменённая применённая миграция вызывает
ошибку. Для другой независимой установки выберите новый префикс БД и отдельную
копию проекта. Не удаляйте secrets при обычном перезапуске.

**local-run содержит реальные пароли и приватный ключ.** Он исключён из Git и
Docker build context. Не отправляйте эту папку коллегам и не включайте в архив сдачи.
Параметры нашего прежнего Docker-проекта (54320/kicksharing-local-only) здесь не используются.

## 3. Собрать приложения

В PowerShell из корня проекта:

```powershell
mvn -DskipTests package
if ($LASTEXITCODE -ne 0) { throw "Build failed: BFF" }
foreach ($service in 'user','moderation','scooter','ride','documents') {
    mvn -f "services/$service/pom.xml" -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $service" }
}
```

`-DskipTests` здесь нужен для получения WAR; это не команда полной проверки.
Если Maven отсутствует в PATH, используйте Maven → Lifecycle → package в IDEA,
включив Skip Tests. Откройте каждый pom.xml сервисов как Maven-проект.

| Приложение | WAR | HTTP-порт |
| --- | --- | --- |
| BFF | target/scooter-bff.war | 8080 |
| User | services/user/target/user-service.war | 8081 |
| Moderation | services/moderation/target/moderation.war | 8082 |
| Scooter | services/scooter/target/scooter.war | 8083 |
| Ride | services/ride/target/ride-service.war | 8084 |
| Documents | services/documents/target/documents.war | 8085 |

Можно взять проверенные WAR из приложенного архива kicksharing-wars.zip: внутри
`wars/bff.war`, `user.war`, `moderation.war`, `scooter.war`, `ride.war`, `documents.war`.

## 4. IntelliJ IDEA: шесть конфигураций Tomcat

Run → Edit Configurations → Tomcat Server → Local. Создайте отдельную конфигурацию
для каждого приложения и выберите JDK 17 и установленный Tomcat 10.1.

Для каждой конфигурации:

1. HTTP port — из таблицы выше.
2. Deployment — только соответствующий WAR/артефакт; **Application context `/`**.
3. Environment variables — значения из `local-run/ИМЯ.env.txt`.
4. Для BFF: VM options — содержимое `local-run/bff.vmoptions.txt`.
5. Если IDEA предлагает дополнительный JMX/RMI-порт, назначьте каждому серверу
   свой свободный порт: например 1099, 1100, 1101, 1102, 1103, 1104.

Не добавляйте все шесть WAR в один Tomcat. У каждого должен быть отдельный
CATALINA_BASE (IDEA обычно создаёт его для каждой конфигурации сама).

Запускайте User и Moderation, затем Scooter, Ride, Documents, BFF. Во время запуска
первой пары временные ошибки до готовности второго сервиса допустимы; для входа
дождитесь готовности обоих. Откройте **http://localhost:8080/**.

## 5. Без интеграции Tomcat в IDEA

Подготовьте шесть отдельных папок Tomcat 10.1. В каждой измените conf/server.xml:
HTTP Connector — соответствующий порт 8080–8085, shutdown port — **-1**. Не
оставляйте дополнительные HTTP/AJP connectors с одинаковыми портами. Удалите
стандартное демонстрационное webapps/ROOT только в этих новых папках и положите
туда соответствующий WAR под именем **ROOT.war**.

В каждой папке bin создайте setenv.bat. Например, для User:

```bat
@echo off
set "JAVA_HOME=C:\path\to\your\jdk-17"
set "DB_URL=jdbc:postgresql://localhost:5432/kicksharing_user"
set "DB_USER=postgres"
set "DB_PASSWORD=YOUR_POSTGRES_PASSWORD"
rem Add the remaining User values from local-run/user.env.txt the same way.
```

Каждую строку NAME=value из своего файла перенесите как `set "NAME=value"`.
Для BFF добавьте `set "CATALINA_OPTS=..."` с содержимым bff.vmoptions.txt.
Если пароль содержит специальные символы CMD (`%`, `!` и другие), используйте
вариант через IDEA и задайте пароль в таблице переменных — прямое копирование
такого пароля в BAT может изменить его значение.

Запускайте **bin/catalina.bat run** каждого сервера в отдельном окне. Остановка —
Ctrl+C в этом окне. Если используете bin/startup.bat, учитывайте, что shutdown port
-1 отключает стандартную остановку через shutdown.bat.

## 6. Первый ADMIN и проверка

Зарегистрируйте двух пользователей — обычного и будущего администратора.
Для назначения ADMIN можно открыть новое окно PowerShell и выполнить команду
для распакованного User в новом Tomcat (пути замените на свои):

```powershell
$settings = Get-Content .\local-run\user.env.json -Raw -Encoding UTF8 | ConvertFrom-Json
$settings.PSObject.Properties | ForEach-Object { [Environment]::SetEnvironmentVariable($_.Name, [string]$_.Value, 'Process') }
java -cp "C:/tomcat-user/webapps/ROOT/WEB-INF/classes;C:/tomcat-user/webapps/ROOT/WEB-INF/lib/*" ru.scooter.user.cli.PromoteAdmin your.admin.login
```

Сервис User должен успеть распаковать ROOT.war. При запуске через IDEA используйте
его фактический deployment-каталог с WEB-INF/classes и WEB-INF/lib либо собранный
services/user/target/user-service. После повышения роли войдите заново.
Разные роли проверяйте в разных профилях браузера/инкогнито.

Список ручных проверок: [SUBMISSION_CHECKLIST.md](SUBMISSION_CHECKLIST.md), раздел 3.
Docker-команды остановки из того чек-листа заменяются остановкой/запуском шести
Tomcat; службу PostgreSQL при обычной остановке приложений можно оставить работающей.

## Необязательная автоматическая проверка без Docker

Если установлен Node.js 22+, после готовности всех шести приложений:

```powershell
node scripts/check-native-stack.cjs
```

Сценарий создаёт тестовых пользователей/поездку/документы и проверяет регистрацию,
nearest, права на position, finish/pay, PDF-чек/историю, снимок, чужой доступ,
удаление документа и отзыв JWT. Ничего не останавливает и не вызывает Docker.
Ожидается PASS; для полной проверки ADMIN используйте ручной чек-лист.

## Если запуск не удался

- psql не найден — укажите -PsqlPath к bin/psql.exe установленного PostgreSQL.
- Connection refused — проверьте службу PostgreSQL и его фактический порт.
- Password authentication failed — нужен пароль PostgreSQL, а не аккаунта сайта.
- Address already in use — проверьте HTTP/JMX-порты и другие запущенные Tomcat.
- 404 на /api/... — проверьте, что контекст приложения именно `/`.
- Ошибка PDF — проверьте PDF_FONT_PATH и соответствие public.pem ключу User.
- 503 при входе — должны работать и User, и Moderation; проверьте их логи.

Скрипт не устанавливает программы и не запускает Tomcat автоматически. Он готовит
БД, ключи и значения окружения; серверы запускаются способами из разделов 4–5.

Фактический прогон подготовки и нативного стенда описан в [NATIVE_SETUP_VERIFICATION.md](NATIVE_SETUP_VERIFICATION.md).
