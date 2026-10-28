# Переменные окружения актуального стенда

Админка самокатов не требует новых переменных. Скрипт `python3 scripts/stand.py prepare --pg-port 5432 --database-prefix kicksharing_teacher_` создаёт все фактические значения в `local-run/<service>.env.txt`, `.env.sh`, `.env.json` и BFF JVM-параметры в `local-run/bff.vmoptions.txt`. Скрипт start читает их автоматически. Для IDEA переносите значения в конфигурацию запуска соответствующего сервиса. Не копируйте один DB_URL на все сервисы.

В примерах замените `<PG_PORT>` (5432 либо ваш порт, например7689), `<DB_USER>/<DB_PASSWORD>` из сгенерированных файлов, `<PROJECT>` на абсолютный путь к проекту, `<SERVICE_KEY>` на содержимое local-run/secrets/service.key. Угловые скобки вводить не нужно. Префикс БД здесь `kicksharing_teacher_`; если prepare использовал иной — берите фактические имена из local-run. Пробелы/кириллица в пути допустимы, в IDEA окружении кавычки вокруг значения не нужны. В Bash пути с пробелами заключайте в кавычки. RSA private.pem только у User, public.pem один и тот же у User/Documents; SERVICE_API_KEY одинаков у Scooter/Ride. Пароль DB — пароль PostgreSQL-роли, не пароль аккаунта сайта.

User — порт8081:

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:<PG_PORT>/kicksharing_teacher_user?currentSchema=public
DB_USER=<DB_USER>
DB_PASSWORD=<DB_PASSWORD>
JWT_PRIVATE_KEY_PATH=<PROJECT>/local-run/secrets/private.pem
JWT_PUBLIC_KEY_PATH=<PROJECT>/local-run/secrets/public.pem
MODERATION_BASE_URL=http://localhost:8082
CORS_ALLOWED_ORIGINS=http://localhost:8082
```

Moderation — порт8082:

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:<PG_PORT>/kicksharing_teacher_moderation?currentSchema=public
DB_USER=<DB_USER>
DB_PASSWORD=<DB_PASSWORD>
USER_BASE_URL=http://localhost:8081
ADMIN_USER_BASE_URL=http://localhost:8081
BFF_LOGIN_URL=http://localhost:8080/login.jsp
```

Scooter — порт8083:

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:<PG_PORT>/kicksharing_teacher_scooter?currentSchema=public
DB_USER=<DB_USER>
DB_PASSWORD=<DB_PASSWORD>
USER_SERVICE_URL=http://localhost:8081
SERVICE_API_KEY=<SERVICE_KEY>
```

Ride — порт8084:

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:<PG_PORT>/kicksharing_teacher_ride?currentSchema=public
DB_USER=<DB_USER>
DB_PASSWORD=<DB_PASSWORD>
USER_SERVICE_URL=http://localhost:8081
SCOOTER_SERVICE_URL=http://localhost:8083
SERVICE_API_KEY=<SERVICE_KEY>
PAYMENT_MODE=educational
```

Documents — порт8085:

```dotenv
DB_URL=jdbc:postgresql://127.0.0.1:<PG_PORT>/kicksharing_teacher_documents?currentSchema=public
DB_USER=<DB_USER>
DB_PASSWORD=<DB_PASSWORD>
USER_SERVICE_URL=http://localhost:8081
MODERATION_SERVICE_URL=http://localhost:8082
RIDE_SERVICE_URL=http://localhost:8084
JWT_PUBLIC_KEY_PATH=<PROJECT>/local-run/secrets/public.pem
PDF_FONT_PATH=<PROJECT>/assets/DejaVuSans.ttf
```

BFF — порт8080, отдельная БД не требуется:

```dotenv
MODERATION_PANEL_URL=http://localhost:8082/admin/users.html
```

В IDEA добавьте BFF в **VM options**, не в Environment variables:

```text
-Duser.service.url=http://localhost:8081 -Dscooter.service.url=http://localhost:8083 -Dride.service.url=http://localhost:8084 -Ddocument.service.url=http://localhost:8085
```

При запуске через `catalina.sh`/`catalina.bat` та же строка задаётся как значение переменной `CATALINA_OPTS`; скрипты prepare/start уже это делают. Отдельной SCOOTER_ADMIN_URL нет: интерфейс парка находится внутри BFF. Moderation строит ссылку на него из существующего BFF_LOGIN_URL.

Дополнительные поддерживаемые параметры (обычно менять не нужно):

| Сервис | Необязательные настройки |
| --- | --- |
| User | JWT_ISSUER=scooter-user-service; JWT_TTL_SECONDS=3600 (60–86400); MODERATION_TIMEOUT_SECONDS=3 (1–5); BCRYPT_COST=12 (10–14) |
| Scooter/Ride | DB_USERNAME — запасное имя для DB_USER; используйте только DB_USER, чтобы не создавать неоднозначность |
| Documents | DB_USERNAME — основное альтернативное имя для DB_USER (при наличии обоих приоритет DB_USERNAME); JWT_ISSUER=scooter-user-service обязан совпасть с User; JWT_PUBLIC_KEY — PEM/base64 ключ вместо файла, при заданном JWT_PUBLIC_KEY_PATH приоритет у файла; JVM-свойства documents.<ключ> имеют приоритет перед env, например -Ddocuments.jwt.public.key.path=/absolute/public.pem |
| BFF | -Dmoderation.panel.url — JVM-переопределение MODERATION_PANEL_URL |

Для ручного Tomcat дополнительно может потребоваться JAVA_HOME=<путь к JDK17>; JRE_HOME тот же. Рекомендуемые JAVA_OPTS: `-Xms32m -Xmx256m -Dfile.encoding=UTF-8 -Djava.awt.headless=true`, для Documents `-Xmx512m`. Для Documents writable-кэш: `-Dpdfbox.fontcache=<путь к доступной папке кэша>` и XDG_CACHE_HOME=<эта папка>; stand.py создаёт его сам. HTTP-порт задаётся конфигурацией Tomcat, переменная DB_URL не задаёт HTTP-порт. Все приложения — контекст `/`, каждый в отдельном CATALINA_BASE.

При изменении окружения остановите и заново запустите JVM сервиса; одного обновления страницы недостаточно. Файлы local-run содержат секреты, их не отправляют коллегам вместе с исходниками.
