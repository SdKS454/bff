# Весь кикшеринг локально

В репозитории находятся BFF/ScootX и все пять микросервисов, приведённые к Java 17,
Jakarta Servlet 6.0 и Tomcat 10.1. Сервисы работают отдельными процессами и общаются
по HTTP. У каждого микросервиса своя база PostgreSQL; BFF своей БД не имеет.

## Самый простой запуск: Docker Desktop / Docker Engine + Compose v2

Из корня репозитория:

```bash
docker compose up --build -d
docker compose ps -a
```

При первой сборке нужен интернет для Maven и Docker-образов. Дождитесь healthy
для PostgreSQL и шести приложений. Задачи keys и migrate должны завершиться Exited (0). Первое развёртывание создаёт пять БД со схемами и примерами
самокатов, пару RSA-ключей User и закрытый ключ связи Ride → Scooter.
JWT, приватные RSA-ключи и служебный ключ не помещаются в исходники или образы.
Базы, RSA-ключи и служебный ключ хранятся в постоянных Docker volumes.
Один общий образ уменьшает размер сборки; каждый сервис запускается собственным
контейнером с отдельным приложением ROOT и собственными настройками.

| Что открыть | Адрес |
| --- | --- |
| Сайт BFF/ScootX | http://localhost:8080/ |
| Вход пользователей/модераторов | http://localhost:8080/login.jsp |
| User Service | http://localhost:8081 |
| Панель Moderation | http://localhost:8082/admin/users.html |
| Scooter Service | http://localhost:8083/api/scooters |
| Ride Service | http://localhost:8084/api/rides |
| Documents Service | http://localhost:8085/api/documents/generate |
| PostgreSQL для IDE | localhost:54320; БД user, moderation, scooter, ride, documents |

REST API требуют Bearer JWT (либо сессию BFF на 8080). Открытый напрямую URL
сервиса в браузере без JWT может вернуть 401/405 — это ожидаемый ответ API.
Сайт открывайте через localhost, чтобы адреса входа/панели и CORS совпадали.
Все опубликованные порты Compose привязаны к loopback.

DB-пользователь локального стенда: `kicksharing`. Учебный пароль по умолчанию:
`kicksharing-local-only`. Можно до первого запуска задать LOCAL_DB_PASSWORD
в корневом .env; этот файл исключён из Git и Docker build context. Если БД уже
созданы, смена переменной не меняет пароль существующей роли PostgreSQL:
нужно изменить его в самой БД и синхронно обновить окружение.

### Первый пользователь и администратор

Зарегистрируйтесь в ScootX: логин, пароль, ФИО, дата рождения. Новый пользователь
всегда USER. Затем назначьте существующему логину ADMIN через CLI User:

```bash
docker compose exec user java -cp '/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*' ru.scooter.user.cli.PromoteAdmin your.login
```

На Windows PowerShell можно заключить classpath в двойные кавычки.
Выйдите и войдите снова: повышение роли отзывает старые токены. ADMIN автоматически
переходит на панель 8082; токен передаётся через fragment и убирается из адреса.
Для обычного кабинета зарегистрируйте отдельного пользователя USER.

### Тариф и оплата

По согласованию используется **учебная оплата без списания настоящих денег**.
Тариф хранится в Ride DB: 50 ₽ за старт + 8 ₽ за каждую начатую минуту,
минимум одна минута. Минимальная сумма поездки — 58 ₽. Время/сумму рассчитывает
Ride, фронт не подменяет серверный результат. После завершения отдельно
подтвердите учебную оплату. Повторная оплата возвращает уже сохранённый результат.
Интерфейс и PDF-чек явно содержат пометку учебного режима.

Активные тарифы: GET /api/rates через BFF или Ride с JWT. Изменить тариф для новых
поездок можно в таблице `rates` базы ride. Начатые поездки сохраняют снимок тарифа.
PAYMENT_MODE=educational включён только явно в Compose/ручной конфигурации Ride;
без настройки режим оплаты отключён, настоящий платёжный шлюз не подключён.

### Остановка, перезапуск, диагностика

```bash
docker compose logs --tail=100 user moderation scooter ride documents bff
docker compose restart ride
docker compose down
```

`down` сохраняет данные и ключи. При повторном `up` SQL-инициализация не стирает БД,
а ключи не перегенерируются. Для очистки **всех данных именно этого локального
стенда** существует `docker compose down -v`; применяйте его только для намеренного
удаления пользователей, поездок, документов и ключей.

Не запускайте одновременно другой сервер на 8080–8085 или 54320. После обновления
кода используйте `docker compose up --build -d`. Схемы SQL применяются при создании новых БД. Для существующих БД задача migrate
применяет local-stack/migrations по версиям в транзакциях с advisory lock и SHA-256.
Повтор безопасен; изменение применённого файла отклоняется. Старые самостоятельные
базы коллег не подключаются автоматически. Для ручного Tomcat миграции нужно
применить к своим БД отдельно (по порядку файлов для каждой БД).

## Запуск без контейнеров приложений: IDE + отдельные Tomcat

Для полностью нативного Windows-запуска, включая PostgreSQL без Docker:
[WINDOWS_NO_DOCKER.md](../docs/WINDOWS_NO_DOCKER.md). Скрипт
`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\windows\prepare-local.ps1`
создаёт отдельные БД kicksharing_*, ключи и готовые переменные в local-run/.
Используется ваш PostgreSQL (обычно 5432), а не Docker-реквизиты 54320.

Нужны Java 17+, Maven 3.9+, Tomcat 10.1+, PostgreSQL. Соберите каждый pom.xml
в корне и `services/{user,moderation,scooter,ride,documents}`. Во всех шести Tomcat
назначьте порт из таблицы и **Application context /**, либо разверните
соответствующий WAR как webapps/ROOT.war. Копирование WAR под исходным именем
создаст дополнительный context path и потребует изменить base URL настроек.
Можно запустить только БД командой `docker compose up -d postgres` и использовать
jdbc:postgresql://localhost:54320/{имя_базы} с указанными выше DB-реквизитами.

Подготовьте пару ключей через CLI User GenerateKeys; в User передайте приватный
и публичный ключ, в Documents — только публичный. Подготовьте случайный
SERVICE_API_KEY (одинаковый только в Ride и Scooter). В Docker эти шаги автоматические.

| Сервис | Переменные / JVM-свойства при ручном запуске |
| --- | --- |
| User | DB_URL, DB_USER, DB_PASSWORD, JWT_PRIVATE_KEY_PATH, JWT_PUBLIC_KEY_PATH; MODERATION_BASE_URL=http://localhost:8082; CORS_ALLOWED_ORIGINS=http://localhost:8082 |
| Moderation | DB_URL, DB_USER, DB_PASSWORD; USER_BASE_URL=http://localhost:8081; ADMIN_USER_BASE_URL=http://localhost:8081; BFF_LOGIN_URL=http://localhost:8080/login.jsp |
| Scooter | DB_URL, DB_USER, DB_PASSWORD; USER_SERVICE_URL=http://localhost:8081; SERVICE_API_KEY |
| Ride | DB_URL, DB_USER (либо DB_USERNAME), DB_PASSWORD; USER_SERVICE_URL=http://localhost:8081; SCOOTER_SERVICE_URL=http://localhost:8083; SERVICE_API_KEY; PAYMENT_MODE=educational |
| Documents | DB_URL, DB_USER (либо DB_USERNAME), DB_PASSWORD; USER_SERVICE_URL=http://localhost:8081; MODERATION_SERVICE_URL=http://localhost:8082; RIDE_SERVICE_URL=http://localhost:8084; JWT_PUBLIC_KEY_PATH; PDF_FONT_PATH |
| BFF | -Duser.service.url=http://localhost:8081 -Dscooter.service.url=http://localhost:8083 -Dride.service.url=http://localhost:8084 -Ddocument.service.url=http://localhost:8085; MODERATION_PANEL_URL=http://localhost:8082/admin/users.html |

SQL: User `db/001_schema.sql`, Moderation/Documents `src/main/resources/schema.sql`,
Scooter `db.sql`, Ride `db/schema.sql`. Создайте каждую схему в **своей** БД.
Шрифт Documents — локальный TTF с кириллицей (DejaVuSans или LiberationSans).
В контейнере уже есть DejaVuSans; путь настроен в Compose. Установка на хост не нужна.

## Восстановление состояния и ограничения

Scooter выдаёт массивы/поля numberPlate, posX, posY и состояния READY/IN_RIDE,
принимает дробные координаты X/Y от -1000000 до 1000000. Используется учебная
евклидова сетка, distance в единицах сетки; GPS-кнопки нет.
Разряд батареи по вымышленному коэффициенту не рассчитывается. Готовые примеры
самокатов используют условную сетку; вводите сопоставимые координаты.

Ride атомарно резервирует самокат с уникальным rideId. Ограничения БД запрещают
две активные поездки пользователя/самоката. Завершение фиксирует время/стоимость
до освобождения самоката. Незавершённые отмены/освобождения сохраняются в БД и
повторяются после восстановления связи/перезапуска; повтор не освобождает чужую
новую резервацию. Внутренние операции Scooter защищены отдельным служебным ключом;
их не разрешает обычный пользовательский JWT. Отменённый rideId не может поздним
запросом повторно занять самокат.

Бан запрещает новый старт, но не завершение/оплату существующей поездки. Источник
бана — Moderation, токенов/ролей — User. Все защищённые сервисы учитывают отзыв JWT.
Документы используют настоящий Ride GET одной поездки, проверяют владельца и PAID;
сохраняют метаданные и JSON-снимки, PDF создают по запросу. В кабинете доступны
история генераций, повторное скачивание исходного содержимого и soft delete.
PUT /api/rides/{id}/position обновляет позицию активной поездки владельцем
и синхронизирует Scooter. Детали — в ../docs/API_CONTRACTS.md.
Бессрочный PAYMENT_DEBT снимается ADMIN вручную; учебная оплата не отменяет чужой бан.

## Проверка разработчика

Регрессионные тесты BFF: `mvn clean verify`, `npm ci`, `npm run test:e2e`.
Тесты каждого сервиса: `mvn -f services/ИМЯ/pom.xml verify`. Для User JDBC IT
нужны TEST_DB_URL/TEST_DB_USER/TEST_DB_PASSWORD (тест создаёт отдельную схему).
Для PDF-тестов задайте PDF_FONT_PATH к TTF с кириллицей, если системного шрифта нет.

Полная проверка с намеренными отключениями и перезапусками допускается только
на **отдельном тестовом проекте**, не на вашей рабочей БД:

```bash
docker compose -p kicksharing-full-check up --build -d
npm ci
# Нужны Chromium (npx playwright install chromium) и pdftotext.
node scripts/check-full-stack.cjs
node scripts/check-submission.cjs
node scripts/check-stack-recovery.cjs
```

Сценарий проверяет конкуренцию, тарифный снимок, оплату/чеки, бан, восстановление,
перезапуск User и полный браузерный путь. Он создаёт тестовых пользователей и
назначает одному ADMIN. Порты те же, поэтому рабочий стенд перед этим остановите.
После теста `docker compose -p kicksharing-full-check down` остановит его с
сохранением данных. Удаление тестовых томов — отдельный осознанный шаг с `-v`.

DNS-кэш JVM в Compose ограничен одной секундой: после изменения IP контейнера
зависимые приложения восстанавливают адреса без общего перезапуска.

Перед сдачей: [ручной чек-лист](../docs/SUBMISSION_CHECKLIST.md), [отчёт](../docs/SUBMISSION_REPORT.md).
