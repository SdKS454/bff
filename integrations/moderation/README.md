# Синхронизация Moderation с BFF и User Service

Проверен присланный `moderation-main.zip` от 23 октября 2026.
Это отдельный микросервис с собственной БД, REST API и страницей администрирования,
а не часть User Service. BFF не включает его классы/страницу в свой WAR.
В этом каталоге хранится переносимый патч для команды Moderation; их исходники
и собранный WAR не становятся зависимостями сборки BFF.

## Что исправлено

- **Критический обход авторизации:** исходный AuthFilter только декодировал
  JWT, поэтому подделанная роль ADMIN и отозванный токен могли приниматься.
  Теперь каждый защищённый запрос проверяется через User `GET /api/users/validate`
  с исходным Bearer. Только 204 разрешает продолжить; 401 запрещает, сеть,
  таймаут или неожиданный ответ дают 503. Redirect не выполняется.
  Role/userId читаются только после проверки User. Проверка использует именно
  validate: вызов `/me` создал бы цикл User → Moderation → User → Moderation.
- Панель обращается к User `/api/users`, `/api/users/blocked`, `/api/users/reasons`,
  `/api/users/{id}/ban` и `/unban`. User проверяет ADMIN и существование пользователя,
  затем вызывает Moderation с тем же JWT. Серверная проверка прав есть на обоих сервисах.
- Выход панели вызывает User `POST /api/users/logout`, затем удаляет JWT из
  sessionStorage и возвращает в BFF login.jsp. Повторный 401 считается завершённым
  выходом. При недоступности User показывается ошибка с возможностью повторить
  выход; успешный отзыв не имитируется. BFF при следующем запросе отвергает
  отозванный JWT, даже если его HTTP-сессия ещё существует.
- Общий диапазон `limit`: 1–500, offset ≥ 0; панель загружает страницы по 200.
  В исходном Moderation максимум был 200, хотя User разрешал 500.
- Java 17 / Servlet 6.0 / Tomcat 10.1 совместимы со всем проверенным стендом;
  Java 21 также подходит. Явно закреплены compiler/war plugins, обновлены
  Jackson до 2.18.6 и PostgreSQL driver до 42.7.8, имя артефакта `moderation.war`.
- `/admin/js/config.js` теперь отдаётся servlet, адреса настраиваются переменными
  окружения, а не правкой JS. Ответ содержит только публичные URL и не кешируется.

BFF уже правильно передавал JWT и перенаправлял ADMIN; менять его production
маршруты не понадобилось. Старт поездки по-прежнему проверяет бан через User.

## Получить исправленный сервис

В корне BFF (Python 3, Git):

```bash
python3 scripts/prepare-moderation.py /path/to/moderation-main.zip /path/to/moderation-synced
mvn -f /path/to/moderation-synced/pom.xml clean verify
```

Скрипт сверяет SHA-256 ровно полученного архива, создаёт новую папку, применяет
патч и не перезаписывает существующий проект. Для другой версии архива сначала
нужна новая сверка. Если исходник уже распакован, из каталога с его `pom.xml`:

```bash
git apply --check /path/to/bff/integrations/moderation/moderation-sync.patch
git apply /path/to/bff/integrations/moderation/moderation-sync.patch
mvn clean verify
```

В ZIP коллег каталоги имеют атрибут read-only; скрипт создаёт обычные доступные
для записи каталоги. При ручной распаковке разрешите запись до сборки.

## Настройки запуска

Все WAR для согласованных адресов разворачиваются в контексте `/`, например
как `ROOT.war` на отдельных Tomcat. Имя `moderation.war` само по себе создаст
контекст `/moderation` — это потребует изменить адреса во всех настройках.

| Компонент | Настройка |
| --- | --- |
| BFF `:8080` | `user.service.url=http://localhost:8081`; `MODERATION_PANEL_URL=http://localhost:8082/admin/users.html` (это значение по умолчанию) |
| User `:8081` | своя PostgreSQL БД/схема users+tokens, DB_URL/DB_USER/DB_PASSWORD, JWT_PRIVATE_KEY_PATH/JWT_PUBLIC_KEY_PATH; `MODERATION_BASE_URL=http://localhost:8082`; `CORS_ALLOWED_ORIGINS=http://localhost:8082` |
| Moderation `:8082` | **своя** PostgreSQL БД; выполнить `src/main/resources/schema.sql`; DB_URL/DB_USER/DB_PASSWORD; `USER_BASE_URL=http://localhost:8081` |
| Публичные адреса панели | `ADMIN_USER_BASE_URL` по умолчанию равен USER_BASE_URL; `BFF_LOGIN_URL` по умолчанию `http://localhost:8080/login.jsp` |

USER_BASE_URL — адрес User, доступный серверу Moderation; ADMIN_USER_BASE_URL —
адрес User, доступный браузеру. Для localhost они совпадают. Если origin панели
меняется, добавьте его точное значение (с портом, без пути) в CORS User.
Приватный ключ JWT нужен только User. Moderation проверяет токен через validate,
копировать ключи или заводить общий JWT secret не нужно.

Нужен заранее назначенный ADMIN через User CLI PromoteAdmin; регистрация всегда
создаёт USER. Вход: `http://localhost:8080/login.jsp`. После входа ADMIN BFF
переходит на `http://localhost:8082/admin/users.html#accessToken=<JWT>`, панель
переносит JWT в sessionStorage и сразу очищает fragment адреса.
USER остаётся в ScootX. User `/validate` не спрашивает Moderation: оба сервиса
успевают запуститься без взаимного запроса при инициализации.

## Воспроизводимая сквозная проверка

Соберите User и подготовленный Moderation. В BFF:

```bash
mvn clean verify dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
npm ci
# Требуется браузер Playwright (npx playwright install chromium) либо CHROMIUM_EXECUTABLE.
python3 scripts/check-moderation-integration.py \
  --user-project /path/to/user-service \
  --moderation-project /path/to/moderation-synced
```

Требования: Python 3.9+, Docker с доступом к PostgreSQL 16 image, OpenSSL,
Node.js 18+, Java 17+, браузер Chromium. JAVA_HOME и CHROMIUM_EXECUTABLE
поддерживаются. Скрипт создаёт контейнер с loopback-портом и двумя отдельными
временными БД, одноразовые RSA-ключи и тестовых пользователей; удаляет контейнер,
ключи и останавливает Java-процессы при завершении. К существующим БД он не
подключается. Логи приложений находятся в `target/moderation-integration/`.

Проверено: 128 тестов исправленного Moderation; 85 Java-тестов BFF; 6 JS-тестов
и 14 браузерных сценариев BFF. Дополнительно реальный браузер прошёл вход ADMIN,
CORS/таблицу, бан/разбан, идемпотентный бан, запрет старта в BFF, общий выход;
HTTP-проверки подтвердили отказ подделанному/отозванному JWT, права USER и 503
при недоступности User. Аутентификация и блокировки используют настоящий код
сервисов и PostgreSQL. Scooter/Ride/Documents в сквозном сценарии заменены
HTTP-фикстурами; готовность этих трёх сервисов этот прогон не подтверждает.
