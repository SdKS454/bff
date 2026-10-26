USER SERVICE — учебный прокат самокатов

Стек: Java 17+ (можно JDK 21), Maven 3.9+, Jakarta Servlet 6.0,
Tomcat 10.1, PostgreSQL 16+, чистый JDBC через DriverManager,
JWT RS256/JJWT, Jackson, BCrypt. Spring и пул соединений не используются.
Один запрос репозитория открывает соединение и закрывает его через try-with-resources.

Все настройки читаются из переменных окружения процесса Tomcat.
.env.example — образец имён и значений, он не загружается приложением автоматически.
Секреты не лежат в исходниках и не включены в архив.

БЫСТРЫЙ ЗАПУСК

1. Установить JDK 17/21, Maven, PostgreSQL и Tomcat 10.1.
   В JAVA_HOME должен быть JDK. Используется jakarta.servlet, не javax.servlet;
   для Tomcat 9 этот WAR не подходит.

2. Создать отдельного пользователя и базу в psql от администратора PostgreSQL:

   CREATE ROLE scooter_user LOGIN PASSWORD 'choose-a-local-password';
   CREATE DATABASE scooter_users OWNER scooter_user;

   Подключиться к scooter_users под scooter_user и выполнить db/001_schema.sql:

   psql -h localhost -U scooter_user -d scooter_users -v ON_ERROR_STOP=1 -f db/001_schema.sql

   Скрипт 001 предназначен для новой БД и создаёт ровно две таблицы: users и tokens.
   Если уже установлена предыдущая версия нашего проекта с tokens.expires_at, вместо
   повторного 001 выполнить только db/002_rename_token_expiry.sql: он сохраняет данные,
   переименовывает столбец в expire_at и индекс. Для новой БД 002 не запускать.
   UUID создаются в Java; расширения PostgreSQL не нужны.

3. В dist/user-service.war уже лежит собранный WAR. Для пересборки из корня проекта
   и создания собственной пары ключей выполнить:

   mvn clean package
   mvn exec:java -Dexec.mainClass=ru.scooter.user.cli.GenerateKeys -Dexec.args=secrets

   Будут созданы secrets/private.pem (PKCS#8) и secrets/public.pem (X.509).
   Повторный запуск откажется перезаписывать ключи. Приватный ключ хранится только
   у User Service. Публичный ключ передать Moderation, Ride, Scooter и Documents.

4. Задать env перед запуском Tomcat. Пример PowerShell, пути заменить своими:

   $env:DB_URL = 'jdbc:postgresql://localhost:5432/scooter_users'
   $env:DB_USER = 'scooter_user'
   $env:DB_PASSWORD = 'choose-a-local-password'
   $env:JWT_PRIVATE_KEY_PATH = (Resolve-Path '.\secrets\private.pem').Path
   $env:JWT_PUBLIC_KEY_PATH = (Resolve-Path '.\secrets\public.pem').Path
   $env:JWT_ISSUER = 'scooter-user-service'
   $env:JWT_TTL_SECONDS = '3600'
   $env:CORS_ALLOWED_ORIGINS = 'http://localhost:8082'
   $env:MODERATION_BASE_URL = 'http://localhost:8082'
   $env:MODERATION_TIMEOUT_SECONDS = '3'
   $env:BCRYPT_COST = '12'

   Для Bash используются export DB_URL='...' и аналогичные export остальных env.
   Если Tomcat запускается как служба Windows, переменные надо задать именно службе;
   переменные открытого терминала уже работающей службе не передаются.

   DB_URL, DB_USER, DB_PASSWORD, JWT_PRIVATE_KEY_PATH, JWT_PUBLIC_KEY_PATH обязательны.
   Остальные переменные имеют значения по умолчанию из .env.example.
   JWT_TTL_SECONDS: 60..86400; BCRYPT_COST: 10..14; timeout Moderation: 1..5 секунд.
   MODERATION_BASE_URL не должен оканчиваться на /; допускается context path:
   http://localhost:8082/moderation.
   CORS_ALLOWED_ORIGINS — список origin через запятую, без путей и завершающего /.
   При доступе с других компьютеров использовать реальный hostname/IP вместо localhost.
   Вне локального стенда использовать HTTPS и при необходимости sslmode=verify-full
   в JDBC URL с настройкой сертификата PostgreSQL.

5. В conf/server.xml отдельного Tomcat установить HTTP Connector port="8081".
   Скопировать target/user-service.war как webapps/ROOT.war. Это обеспечивает URL
   http://localhost:8081/api/users/... без дополнительного context path, как ожидает BFF.
   Убрать старое стандартное приложение ROOT перед первым развёртыванием.
   Запустить bin/catalina.bat run (Windows) или bin/catalina.sh run (Linux/macOS)
   из окружения с настройками выше. При неверной БД/схеме/ключах сервис не стартует.

   Можно развернуть и как /user-service, но тогда user.service.url в BFF должен
   включать этот context path.

6. У BFF оставить user.service.url=http://localhost:8081.
   Основной вход пользователей и администраторов — существующий login.jsp в BFF.
   При отсутствии токена панель Moderation перенаправляет браузер туда. Команде BFF
   надо добавить редирект ADMIN после успешного login: готовые 4 строки находятся
   в docs/MODERATION_REPLY.txt. Сам BFF не менялся и не входит в этот архив.
   Адрес панели настраивает команда BFF. USER продолжает переходить на main.jsp.

СОЗДАНИЕ АДМИНИСТРАТОРА

Публичная регистрация всегда создаёт USER, даже если прислать role=ADMIN.
Сначала зарегистрировать будущего администратора через BFF или POST /api/users/register.
Потом из корня проекта с DB_URL, DB_USER, DB_PASSWORD выполнить:

   mvn exec:java -Dexec.mainClass=ru.scooter.user.cli.PromoteAdmin -Dexec.args=admin_login

Команда работает напрямую с БД, повышает существующего активного пользователя и
отзывает его прежние токены в одной транзакции. Публичного API выдачи ADMIN нет.
После этого войти через login.jsp сервиса BFF. User Service предоставляет только
REST API: POST /api/users/login возвращает accessToken, expiresInSeconds и user,
без HTML и HTTP-редиректов. Админские API проверяют роль ADMIN на сервере.
Отображение формы входа и редирект в Moderation реализует команда BFF.
Никаких общих паролей или готовых админских аккаунтов в проекте нет.

ИНТЕГРАЦИЯ С MODERATION

Обязательный контракт статуса:
GET /api/moderation/users/{userId}/status
Authorization: Bearer <исходный JWT>
200 {"userId":"UUID","blocked":false}

Дополнительные поля reasonType, reason, blockedAt, blockedUntil допускаются.
Если записи о пользователе нет, Moderation должен отвечать 200 и blocked=false,
а не 404. Источник роли — User; источник блокировки — Moderation.
Moderation проверяет подпись локально и разрешает status самому пользователю и ADMIN.
Для login токен записывается до запроса status, поэтому возможная проверка /validate
с его стороны тоже завершится без рекурсии; /validate никогда не вызывает Moderation.
Нельзя вызывать полный /api/users/{id} из обработчика status: это вызовет цикл,
так как профиль сам получает status. Для ban User проверяет существование заранее.

POST /api/moderation/users/{userId}/ban    {"reason":"RIDE_DANGEROUS"}
POST /api/moderation/users/{userId}/unban  без тела
GET  /api/moderation/users?blocked=true&limit=100&offset=0
GET  /api/moderation/reasons

У ban/unban ожидается объект статуса с userId и boolean blocked, у списков — массив.
Неизвестную причину окончательно проверяет Moderation, User не копирует справочник.
Недоступность, таймаут, 429 или 5xx Moderation -> 503 User.
Неожиданный формат/404/редирект Moderation -> 502, статус блокировки не выдумывается.
400/401/403/409 Moderation пробрасываются в едином JSON-формате ошибки.

Новый пользователь получает blocked=false при регистрации: его UUID только что создан,
а JWT ещё не выдаётся. При login, me и GET по id статус запрашивается у Moderation.
Если Moderation недоступен, вход и эти профили отвечают ошибкой; выданный при попытке
входа токен отзывается. /validate и админский краткий список от Moderation не зависят.
Заблокированный пользователь может войти и посмотреть профиль; запрет поездки
выполняет BFF после отдельной проверки blocked, как в его контракте.

Панель модерации должна сразу прочитать фрагмент URL, сохранить токен в sessionStorage
и убрать фрагмент через history.replaceState. Токен не должен попадать в query string.
Схема с фрагментом реализована по запросу команды; для более серьёзного SSO используется
обмен одноразовым кодом. JSON API /login сам не делает HTTP 302 и не создаёт сессию.
Редирект делает js/login.js команды BFF после обычного login.
В User Service нет страниц входа, JS, CSS и сервлетов представления.
Обработка фрагмента на стороне Moderation — ответственность её страницы, она не в архиве.

ТОКЕНЫ И ВЫХОД

JWT: alg=RS256; userId — строковый UUID; role — USER или ADMIN;
iss=scooter-user-service (настраивается), sub=userId, jti=UUID, iat и exp — NumericDate.
Соседние сервисы должны согласовать RS256/issuer и получить public.pem, а не просто
декодировать payload. Приватный ключ им не нужен. Ключи и issuer должны сохраняться
между перезапусками; ротация ключа делает ранее выданные JWT непригодными.

User проверяет подпись, issuer, срок, обязательные claims, запись Tokens, SHA-256 JWT,
is_valid, наличие активного пользователя и совпадение текущей роли в БД с токеном.
В БД хранится хэш токена, не сам JWT. Пароль — BCrypt с солью, максимум 72 байта UTF-8.
POST /api/users/logout отзывает текущий JWT. /validate делает только локальную проверку
JWT и два индексированных чтения из БД, без bcrypt и Moderation.
В tokens используются user_id, token_hash, expire_at, is_valid; дополнительных таблиц
и столбца blocked нет. Остальные столбцы tokens — id (jti) и created_at.

Сервисы, проверяющие только подпись локально, узнают об отзыве/смене роли лишь после
истечения JWT; для немедленной проверки они должны обращаться к /validate.
Текущий BFF вызывает /validate на защищённых запросах. Его GET /logout инвалидирует
только сессию BFF; сам он наш logout не вызывает. Для полного отзыва ему надо дополнительно
отправить POST /api/users/logout с тем же Bearer. Прокси новых админских ручек в текущем
BFF тоже нет: панель Moderation может обращаться к User напрямую с разрешённым CORS.

ПРОВЕРКИ

mvn clean verify

Без TEST_DB_URL выполняются unit-тесты JWT/валидации/BCrypt, интеграционные тесты пропускаются.
Для полного прогона создать отдельную тестовую базу, задать TEST_DB_URL, TEST_DB_USER,
TEST_DB_PASSWORD, затем выполнить mvn clean verify. Основные DB_* не используются тестами.
Пример TEST_DB_URL: jdbc:postgresql://localhost:5432/scooter_users_test.
Пользователь тестовой БД должен уметь создавать схемы. Тесты создают случайную изолированную
схему, применяют наш SQL, запускают собранный WAR в Tomcat на свободном порту и удаляют
только свою схему. Реальный Moderation для тестов не нужен: его HTTP-контракт имитируется
исключительно в src/test. В рабочем коде применяется реальный HttpClient.

Тесты проверяют регистрацию/дубликаты/конкурентную регистрацию, роли, пароли,
JWT/подмену/просрочку/issuer, доступ к списку, CORS, редирект, logout, soft delete,
смену роли и доступ к Moderation. Результат выполненного прогона: docs/VERIFICATION.txt.

СТРУКТУРА

servlet — маршрутизация HTTP и DTO, без SQL;
service — правила регистрации, входа, прав и интеграции;
repository — интерфейсы хранения; repository/jdbc — PreparedStatement и PostgreSQL;
entity — User, Token, Role; dto — входные и выходные данные без passwordHash;
filter — единые ошибки/заголовки, CORS, Bearer-аутентификация;
client — интерфейс и HTTP-клиент Moderation;
util — JWT/PEM/BCrypt/JSON/валидация; config — env и сборка зависимостей в listener;
cli — генерация ключей и выдача ADMIN оператором;
db — создание схемы; docs — API, решения по фото, ответ модерации и проверка.

Дополнительные зависимости и исходники BFF, сам Moderation, PostgreSQL, Tomcat,
секреты и содержимое рабочей базы в архив не входят.
