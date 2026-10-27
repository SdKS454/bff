# Ubuntu: подготовка БД, ключей и окружения без Docker

Скрипт `scripts/linux/prepare-local.sh` использует только Bash, Python 3,
PostgreSQL client и JDK. Он не устанавливает программы и не запускает Tomcat.

## Быстрый запуск

Для Ubuntu 22.04/24.04 с обычными пакетами:

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk postgresql postgresql-client python3 fonts-dejavu-core
sudo service postgresql start
```

Если подходящие программы уже установлены, повторная установка не требуется.
Рекомендуются JDK 17 и PostgreSQL 16+ для новой установки; Linux-скрипт совместим
с PostgreSQL 13+ (в том числе PostgreSQL 14 из Ubuntu 22.04).

Из **корня распакованного проекта**:

```bash
bash scripts/linux/prepare-local.sh --pg-port 5432
```

Укажите фактический порт вашей Ubuntu-БД. Если это 7689:

```bash
bash scripts/linux/prepare-local.sh --pg-port 7689
```

Запускайте скрипт **обычным пользователем, без sudo перед bash**. В стандартном
режиме он сам вызывает sudo для административных запросов через локальный
PostgreSQL socket. Если sudo спросит пароль, это пароль вашего пользователя Ubuntu,
а не пароль аккаунта сайта и не заранее заданный пароль PostgreSQL postgres.

Скрипт создаёт отдельную служебную роль вида kicksharing_XXXXXXXX со случайным
паролем, без superuser/CREATEDB/CREATEROLE. Все пять БД принадлежат этой роли.
Существующим ролям пароли не сбрасываются, чужие БД не переписываются.

## Результат

Создаются БД:

```text
kicksharing_user
kicksharing_moderation
kicksharing_scooter
kicksharing_ride
kicksharing_documents
```

В них загружаются схемы, примеры самокатов и версионные миграции. Миграции
выполняются в транзакции, с advisory lock и проверкой SHA-256 применённого файла.

В корне проекта появляется приватная папка local-run:

```text
local-run/
  linux-database.json
  user.env.txt / user.env.json / user.env.sh
  moderation.env.txt / moderation.env.json / moderation.env.sh
  scooter.env.txt / scooter.env.json / scooter.env.sh
  ride.env.txt / ride.env.json / ride.env.sh
  documents.env.txt / documents.env.json / documents.env.sh
  bff.env.txt / bff.env.json / bff.env.sh
  bff.vmoptions.txt
  secrets/private.pem
  secrets/public.pem
  secrets/service.key
```

- **env.txt:** готовые NAME=value для Environment variables конфигурации IDEA.
- **env.json:** те же настройки в структурированном виде.
- **env.sh:** безопасно экранированные export-команды для Bash. Например:

  ```bash
  source ./local-run/user.env.sh
  ```

  Затем запускайте User в своём Tomcat из этого же терминала. Для другого сервиса
  используйте отдельный терминал и его собственный env.sh.
- **bff.vmoptions.txt:** скопировать в VM options BFF при запуске через IDEA.
- **linux-database.json:** служебные реквизиты подготовленного стенда. Сохраняйте
  его вместе с ключами: без него новый случайный пароль не заменит существующий.

Скрипт подставляет правильный JDBC-порт, имя БД каждого сервиса и currentSchema=public.
JAVA_HOME/JRE_HOME указывают найденный JDK; JAVA_OPTS задаёт UTF-8/headless и
предел памяти. В BFF env.sh также задан CATALINA_OPTS с адресами микросервисов.

Папки local-run/secrets имеют права 700, генерируемые файлы окружения и реквизитов —
600. Не отправляйте local-run в Git/архив преподавателю. Ключи и настройки должны
быть доступны пользователю, от которого вы запускаете приложения.

## Затем запустить приложения

Нужны шесть отдельных Tomcat **10.1** или шесть конфигураций IDEA с собственными
CATALINA_BASE. Везде Application context `/`; каждый WAR разворачивается как ROOT.
Используйте локальный Tomcat от своего пользователя, чтобы он мог читать local-run.

| Приложение | HTTP-порт | Файл окружения |
|---|---|---|
| BFF | 8080 | bff.env.txt / bff.env.sh |
| User | 8081 | user.env.txt / user.env.sh |
| Moderation | 8082 | moderation.env.txt / moderation.env.sh |
| Scooter | 8083 | scooter.env.txt / scooter.env.sh |
| Ride | 8084 | ride.env.txt / ride.env.sh |
| Documents | 8085 | documents.env.txt / documents.env.sh |

Порядок: User + Moderation, затем Scooter, Ride, Documents, BFF.
JMX/дополнительные порты тоже должны быть уникальными. Не устанавливайте Tomcat 9.
На Linux разделитель Java classpath — `:`, на Windows — `;`.

Открыть **http://localhost:8080/** и разрешить геолокацию браузера. При открытии
с другого компьютера по обычному HTTP-IP геолокация может быть недоступна:
для такого размещения нужен HTTPS. GPS-точки самокатов в БД должны соответствовать
выбранному городу; детали новой GPS-ветки — в ПРОЧИТАЙ_СНАЧАЛА.md.

Корень отдельного API-сервиса может возвращать 404. Проверка User:
http://localhost:8081/api/users/exists?login=qa — ожидается JSON/200.
GET http://localhost:8085/api/documents/generate должен вернуть 405 (генерация принимает POST).

Для первого ADMIN сначала зарегистрируйте отдельный аккаунт через сайт. Затем,
если User собран Maven в services/user/target/user-service:

```bash
source ./local-run/user.env.sh
"$JAVA_HOME/bin/java" -cp 'services/user/target/user-service/WEB-INF/classes:services/user/target/user-service/WEB-INF/lib/*' ru.scooter.user.cli.PromoteAdmin your.admin.login
```

При готовом WAR используйте фактический каталог распакованного User с WEB-INF.
После повышения роли нужно войти заново.

## Повторный запуск и существующие БД

Повторяйте ту же команду с теми же параметрами. Пароль служебной роли и ключи
переиспользуются, зарегистрированные пользователи/поездки/документы сохраняются.
Не удаляйте linux-database.json и secrets при обычном обновлении исходников.

Если БД с такими именами уже создана другой установкой и принадлежит другой роли,
скрипт остановится. Восстановите подходящую старую local-run или используйте
отдельную копию проекта с другим префиксом, например:

```bash
bash scripts/linux/prepare-local.sh --database-prefix coursework_ --pg-port 5432
```

Не подменяйте вручную реквизиты в linux-database.json: это не сброс пароля роли.

## Дополнительные параметры

```bash
bash scripts/linux/prepare-local.sh --help
```

- `--java /path/to/jdk-17/bin/java` — явный JDK, если JAVA_HOME/PATH указывают другой.
- `--font-path /path/to/DejaVuSans.ttf` — явный шрифт с кириллицей.
- `--psql /path/to/psql` — явный PostgreSQL client.
- `--socket-dir /var/run/postgresql` — каталог socket локального сервера.
- `--password-auth --admin-user postgres` — вместо sudo/peer спросить пароль
  администратора PostgreSQL и подключиться по TCP. Пароль администратора не
  сохраняется; для приложений создаются отдельные служебные реквизиты.
- `--no-sudo` — только для локального кластера, к которому текущий OS-пользователь
  уже имеет административный доступ. Это не обход PostgreSQL-авторизации.

При Connection refused проверьте службу, `pg_lsclusters` и порт. При ошибке peer
проверьте владельца/настройки кластера либо выберите password-auth. При отсутствии
psql/JDK/шрифта установите пакеты или передайте соответствующий путь.

Дальнейшая ручная проверка: [FULL_CHECKLIST_GPS.md](../FULL_CHECKLIST_GPS.md).
