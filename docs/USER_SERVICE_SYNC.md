# Синхронизация BFF и готового User Service

> После подключения ScootX изменились UI-файлы и старые JSP теперь перенаправляют
> в единый интерфейс. Актуальная схема и проверки — [FRONTEND_INTEGRATION.md](FRONTEND_INTEGRATION.md).

Проверено по исходникам `scooter-user-service.zip` и приложенным `контракты.md`.
В архиве находится User Service, не сервис самокатов. Его код не добавлялся в
репозиторий BFF и не менялся. В старом REST-документе сохранилось историческое
описание локального logout; реализованный `/api/users/logout` теперь позволяет
отзыв токена, поэтому BFF использует его.

## Решения по четырём замечаниям

1. **birthDate обязательна.** Это совпадает с готовой валидацией и NOT NULL в БД
   User. Диапазон — 1900-01-01 .. сегодня UTC, формат YYYY-MM-DD.
   Изменять БД коллег не требуется.
2. **Ограничения регистрации синхронизированы.** Логин 3–64: латиница, цифры,
   _, . и -; сервер приводит к нижнему регистру и убирает крайние пробелы.
   Пароль минимум 8 символов (как в Java String.length у коллег), максимум
   72 байта UTF-8, без NUL. Имя обязательно, trim, максимум 200 символов,
   без управляющих символов. Форма использует HTML-ограничения и JS-проверку
   длины пароля в байтах; BFF повторяет правила для прямых API-клиентов.
   Ошибки User API, в частности 409 «Логин уже занят», показываются текстом.
   Поле role не принимается BFF и не пересылается; User всегда назначает USER.
   avatarUrl остаётся опциональным API-полем, его окончательно проверяет User.
3. **ADMIN переходит в Moderation**, USER — на main.jsp. Для localhost выбран
   `http://localhost:8082/admin/users.html` из примера `MODERATION_REPLY.txt`.
   Это локальная договорённость BFF, а не подтверждённая доступность панели:
   в REST-документе есть лишь предложенный порт 8082. Команда Moderation должна
   подтвердить путь страницы или переопределить URL. Настройка:
   `MODERATION_PANEL_URL` либо `-Dmoderation.panel.url=...` (приоритет JVM).
   JWT передаётся только во фрагменте `#accessToken=...`, не в query.
   Панель читает токен, проверяет роль на своих API и очищает фрагмент через
   history.replaceState. BFF не сохраняет JWT в localStorage/sessionStorage.
4. **Общий выход отзывает JWT.** BFF вызывает POST User `/api/users/logout`
   с текущим Bearer (явный заголовок приоритетнее session JWT), затем удаляет
   сессию BFF. API `POST /api/auth/logout` → 204. Форма `POST /logout` → 303
   на login.jsp. GET /logout показывает подтверждение и ничего не отзывает.
   Повторный logout/уже истёкший или отозванный токен обрабатываются спокойно:
   401 User означает, что токен уже не действует. Предварительный validate
   на logout не нужен. При недоступности/ошибке User сессия BFF всё равно
   закрывается, но возвращается ошибка: успешный отзыв JWT не заявляется.
   При 503 токен потенциально жив до истечения срока; повторить отзыв можно
   через API с сохранённым Bearer. Отзыв хранится в User; для немедленного
   применения в других сервисах им также нужна проверка отзыва через User.

## Адрес входа и пример регистрации

При стандартном BFF Tomcat на порте 8080:
`http://localhost:8080/login.jsp`.
Для локального стенда согласован корневой контекст `/` и порт 8080; сборку scooter-bff.war разворачивают как ROOT.war либо задают `/` в IDE. При другом способе развёртывания адрес отличается; актуальная таблица — API_CONTRACTS.md.

```http
POST /scooter-bff/api/auth/register
Content-Type: application/json
```

```json
{
  "login": "ivan",
  "password": "example-password",
  "fullName": "Иван Петров",
  "birthDate": "2000-05-17"
}
```

Авторизация остаётся через User `/api/users/validate`. Готовый User выдаёт
RS256 с UUID и ролью, а не HS256 из раннего брифа. BFF токен не подписывает
и не изменяет, поэтому отдельный JWT-секрет или приватный ключ ему не нужны.
CORS для прямых запросов панели Moderation к User задаётся в User через
CORS_ALLOWED_ORIGINS; серверные запросы BFF не пересылают Origin браузера.

## Проверки

`mvn clean verify` — JSON/правила регистрации, WAR в Tomcat, API, сессии,
Bearer, logout и ошибки отзыва. `node --test src/test/js/*.test.cjs` — USER/ADMIN
маршрутизация, передача токена в fragment, неправильная настройка, UTF-8 лимит
и отображение ошибок. JS-проверки используют DOM-дубль, а не реальный браузер.

Для проверки работающих сервисов добавлен отдельный скрипт:

```sh
python3 scripts/check-user-integration.py \
  --bff-url http://localhost:8080 \
  --user-url http://localhost:8081
```

Запускать **только на одноразовом тестовом стенде**: скрипт регистрирует
пользователей, входит, проверяет профиль и отзывает их токены. Это реальные
REST-запросы, не заглушки. Скрипт не печатает пароли и JWT.

Для локального развёртывания двух WAR в тестовых embedded Tomcat можно
использовать `scripts/RealUserIntegrationServer.java`. Предварительно настроить
User DB_URL/DB_USER/DB_PASSWORD, ключи и MODERATION_BASE_URL, как в README коллег:

```sh
mvn dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
java --add-opens=java.base/java.io=ALL-UNNAMED \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED \
  -cp "$(cat target/test-classpath.txt)" scripts/RealUserIntegrationServer.java \
  /path/to/user-service.war target/scooter-bff.war
```

Сервер печатает USER_URL и BFF_URL на случайных loopback-портах — их передать
скрипту проверки. В Windows разделители classpath и синтаксис чтения файла
отличаются. Harness останавливает оба сервера при завершении процесса.
Проверка реальной панели Moderation остаётся отдельной от проверки этих двух WAR.

Фактический результат: 42 unit-теста BFF, 43 HTTP-интеграционных теста и 12
JS-тестов — все пройдены, пропусков нет. Дополнительно пройдены 3 сквозных
сценария с настоящими собранными WAR BFF и User Service и PostgreSQL 16.
Moderation в этом прогоне заменён только HTTP-заглушкой статуса пользователя.
Реальный JWT RS256 выдаётся User, записывается в его БД и перестаёт проходить
`/api/users/validate` после logout. Собственные 20 тестов User Service тоже
пройдены с одноразовой PostgreSQL; исходники User не менялись. Реальная панель
Moderation и прочие микросервисы в эти результаты не входят.
