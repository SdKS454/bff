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
node --test src/test/js/ui.test.cjs
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

Базовые 13 endpoint-ов из приложенного REST-документа сохранены.
Изменения и найденные ошибки описаны в [docs/REVIEW.md](docs/REVIEW.md).

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
- `/logout` завершает только сессию BFF — как определено текущим REST-документом.
  Отзыв JWT в User Service этим контрактом не предусмотрен.

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
node --test src/test/js/ui.test.cjs
```

На локальном компьютере с JDK/Maven и обычным доступом к Maven Central
достаточно стандартных команд из первого раздела.
