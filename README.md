# Scooter Rental BFF

BFF учебного проекта «Кикшеринг»: Java 17, Jakarta Servlet 6, JSP 3.1,
Tomcat 10.1, Maven, REST/JSON. Разворачивается как `scooter-bff.war`,
контекст `/scooter-bff`. Исходная версия взята из предоставленного
`bff__test-main.zip`; микросервисы в этот репозиторий не входят.

## Проверка и сборка

Требуются полноценный JDK 17+ (не только JRE), Maven 3.9+ и, для JS-тестов,
Node.js 18+. Целевая версия Java — 17.

```sh
mvn clean verify
node --test src/test/js/*.test.cjs
```

`verify` выполняет unit-тесты JSON, собирает `target/scooter-bff.war`, затем
разворачивает именно этот WAR в Tomcat 10.1 на случайном локальном порту.
Интеграционные проверки используют HTTP-заглушки downstream-сервисов,
проверяют servlet/filter-аннотации, JSP, сессии, REST, PDF и остановку HTTP-пула.
Проверки JS используют DOM-дубль; реальный браузер и его геолокацию они не запускают.
`mvn test` выполняет только unit-тесты; для проверки всего BFF нужен `verify`.

## Запуск с микросервисами

Разместите `target/scooter-bff.war` в `webapps` установленного Tomcat 10.1.
Адреса сервисов заданы в `src/main/resources/application.properties` и могут
переопределяться JVM-свойствами Tomcat без изменения WAR:

```sh
export CATALINA_OPTS="${CATALINA_OPTS:-} -Duser.service.url=http://localhost:8081 -Dscooter.service.url=http://localhost:8083 -Dride.service.url=http://localhost:8084 -Ddocument.service.url=http://localhost:8085"
"$CATALINA_HOME/bin/catalina.sh" run
```

Для готовности нужны ответы реальных сервисов: успешный login, `/api/users/me`,
поиск самокатов и сценарий аренды. Само наличие порта или HTTP 200 у JSP не
доказывает готовность интеграции. HTTP-заглушки тестов не заменяют микросервисы
и не должны использоваться для реальной аренды.

В production настройте HTTPS и Secure для session cookie на уровне Tomcat;
HttpOnly и запрет URL-сессий включены в приложении. При TLS-терминации reverse proxy
должен корректно передавать внешние scheme/host/port в Tomcat: проверка Origin
сравнивает их с Origin браузера. CORS не включён: JSP и API используют один origin.
Геолокация браузера требует защищённого контекста и разрешения пользователя.

Таймаут подключения к сервисам — 3 секунды, HTTP-запроса — 10 секунд.
JWT BFF получает из Bearer-заголовка либо HTTP-сессии. Явно переданный
неправильный Authorization не заменяется токеном из сессии.
JWT проверяется через User Service, локальный JWT-секрет BFF не нужен.

## API и поведение

Базовые маршруты из REST-документа сохранены; logout переведён на POST.
Первичная проверка описана в [docs/REVIEW.md](docs/REVIEW.md), актуальная
синхронизация с готовым User — в [docs/USER_SERVICE_SYNC.md](docs/USER_SERVICE_SYNC.md).

- ID пользователя, поездки, тарифа и источника документа — UUID.
- При регистрации роль клиента игнорируется и не пересылается; User обязан
  самостоятельно назначать роль USER.
- Для JSON-запросов нужен `Content-Type: application/json`, тело до 64 KiB.
- Координаты — конечные JSON-числа без округления. Единицы пока не согласованы.
- Статусы поездок в UI: ACTIVE → FINISHED → PAID. Ошибку оплаты можно повторить
  отдельной кнопкой, уже завершённая поездка повторно не завершается.
- PDF: RIDE_PAYMENT, USER_BLOCKED, USER_HISTORY. Старое RECEIPT принимается как
  alias RIDE_PAYMENT. Пользователь определяется через `/api/users/me`, клиентский
  userId не используется; sourceId для RIDE_PAYMENT — UUID поездки.
- `POST /api/auth/logout` отзывает JWT через User Service и завершает сессию BFF;
  `POST /logout` делает то же для формы и возвращает 303 на login.jsp.
  `GET /logout` только показывает подтверждение. Если User недоступен, сессия
  закрывается, но API сообщает ошибку отзыва токена, а не успешный общий выход.

Проверку владельца поездки при finish/pay и генерации чека дополнительно
выполняют Ride/Documents по JWT. BFF не может гарантировать их реализацию.

## Проверка в текущей облачной машине

Полноценные JDK и Maven установлены отдельно от репозитория. Здесь можно
повторить проверку так:

```sh
cd /workspace/bff
JAVA_HOME='/workspace/tools/jdk-17.0.20.1+1' \
MAVEN_OPTS='-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
/workspace/tools/apache-maven-3.9.11/bin/mvn \
  -s /workspace/tools/maven-settings.xml \
  -Dmaven.repo.local=/workspace/.m2 clean verify
node --test src/test/js/*.test.cjs
```

На локальном компьютере с JDK/Maven и обычным доступом к Maven Central
достаточно стандартных команд из первого раздела.

## Регистрация и вход ADMIN

Дата рождения обязательна: от 1900-01-01 до текущей даты UTC. Логин — 3–64
символа латиницы, цифр, _, . или -; пароль — минимум 8 символов и максимум
72 байта UTF-8; fullName — непустое имя до 200 символов без управляющих символов.
Правила проверяются формой, BFF и окончательно User Service; ошибки API видны в форме.

USER после входа открывает main.jsp. ADMIN переходит на панель с JWT в
`#accessToken=...`. Для локального стенда выбран адрес из примера коллег:
`http://localhost:8082/admin/users.html`. Команда Moderation должна отдавать
панель именно по этому пути либо сообщить другой путь. Порт 8082 сам по себе
не определяет путь страницы, реальная панель этим репозиторием не проверена.

Переопределение: JVM-свойство `-Dmoderation.panel.url=...` или переменная
`MODERATION_PANEL_URL` (JVM-свойство приоритетнее). Допустим полный HTTP(S) URL
без логина/пароля и фрагмента. `GET /api/config` после авторизации возвращает
`moderationPanelUrl`; произвольный URL из параметров браузера не используется.
Вне локального стенда используйте HTTPS. Панель должна сразу удалить фрагмент
из адресной строки после чтения токена.

Для стандартного Tomcat на порте 8080 адрес формы входа:
`http://localhost:8080/scooter-bff/login.jsp`; при другом Connector port меняется порт.
