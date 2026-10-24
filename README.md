# ScootX + Scooter BFF

Единое веб-приложение учебного проекта «Кикшеринг»: интерфейс ScootX из архива
`ScootX 3.zip` подключён к BFF. Стек — Java 17, Jakarta Servlet 6/JSP 3.1,
Tomcat 10.1, Maven; фронтенд — обычные HTML/CSS/JavaScript без сборщика.
Шрифты и изображения включены в WAR, внешние CDN для работы сайта не нужны.

## Сборка и запуск

```sh
mvn clean verify
```

Для согласованного локального стенда задайте в Tomcat порт 8080 и контекст `/`.
В IDE это Application context `/`; при обычном развёртывании разместите сборку
`target/scooter-bff.war` как `webapps/ROOT.war` (сохранив прежнее ROOT-приложение,
если оно нужно). Адрес сайта: **http://localhost:8080/**.

Имя Maven-сборки не менялось. Если разместить файл без переименования как
`webapps/scooter-bff.war`, Tomcat назначит другой контекст `/scooter-bff`.
Это альтернативное развёртывание; для текущего локального стенда согласован `/`.

Старые `/login.jsp`, `/register.jsp`, `/main.jsp` перенаправляют на нужный экран
ScootX. Адрес входа для Moderation остаётся
`http://localhost:8080/login.jsp` (если порт BFF — 8080).
Открытие исходного HTML двойным щелчком больше не является способом запуска:
для API и HTTP-сессии нужен BFF в Tomcat. npm/Node для запуска приложения не нужны.

Адреса сервисов по умолчанию заданы в `src/main/resources/application.properties`:
User 8081, Scooters 8083, Ride 8084, Documents 8085. Их можно переопределить
JVM-свойствами без изменения WAR:

```sh
export CATALINA_OPTS="${CATALINA_OPTS:-} -Duser.service.url=http://localhost:8081 -Dscooter.service.url=http://localhost:8083 -Dride.service.url=http://localhost:8084 -Ddocument.service.url=http://localhost:8085"
"$CATALINA_HOME/bin/catalina.sh" run
```

Если сервисы развёрнуты не как ROOT.war, их base URL должен включать context path.
Для ADMIN выбрана локальная настройка `http://localhost:8082/admin/users.html`.
Присланный Moderation проверен с BFF и User; для исправления проверки JWT
и выхода подготовлен [патч и инструкция](integrations/moderation/README.md).
До запуска сервис коллег нужно обновить этим патчем. Переопределение: `MODERATION_PANEL_URL` или JVM-свойство
`-Dmoderation.panel.url=...` (приоритетнее). Панель запускается отдельным сервисом, в WAR BFF она не включается.
JWT передаётся ей во фрагменте `#accessToken=...`; панель должна прочитать токен
и сразу очистить фрагмент. Вне локального стенда использовать HTTPS.

Documents также получен и проверен с BFF/User/Moderation: [сверка и запуск](integrations/documents/README.md).
Исправленные исходники, архивы и WAR доступны в [комплекте для скачивания](https://github.com/SdKS454/bff/tree/temp/documents-integration-2026-10-24/handoff).
Для сервиса нужны собственная БД, публичный ключ User и TTF-шрифт с кириллицей.
Реальный Ride ещё должен подтвердить API получения данных для чека.

## Что подключено

- Регистрация и автоматический вход отдельным запросом после её успеха.
  Логин 3–64: латиница/цифры/_, . и -; ФИО обязательно, до 200 символов;
  birthDate обязательна, 1900-01-01 .. сегодня UTC; пароль от 8 символов,
  не более 72 байт UTF-8. Роль клиента не пересылается.
- Вход, профиль из User, роль ADMIN → Moderation. Пароли, JWT и профили
  не сохраняются в localStorage. Авторизация BFF использует HttpOnly-сессию.
- Каталог, карточка самоката, поиск ближайших по введённым координатам.
  Каталог требует авторизации, как и API BFF. При ошибке показывается ошибка,
  а не подставной парк самокатов.
- Старт → завершение → отдельное подтверждение оплаты. Статусы и сумма приходят
  из Ride. Неуспешную оплату можно повторить; локальной «оплаты» больше нет.
- PDF-чек PAID-поездки, история поездок PDF за период, уведомление о блокировке.
- `POST /api/auth/logout`: отзыв JWT через User и закрытие сессии BFF.
  При ошибке отзыва UI показывает предупреждение; успешный глобальный выход
  не заявляется. `POST /logout` поддерживается для формы, GET — подтверждение.

Избранное — только номера самокатов на этом устройстве, раздельно по userId;
это явно указано в кабинете. Изменение профиля отключено: метода записи в API нет.
Демобаза `demo-storage.js`, локальная авторизация, выдуманные адреса, тарифы,
запас хода и телеметрия не включены в приложение.

Система координат сервисов пока не согласована. Поля принимают числа без
округления; геолокация по отдельной кнопке заполняет X=longitude, Y=latitude,
а поля можно заполнить вручную. Единицы расстояния не называются метрами.
Применять геолокацию можно только с совместимыми координатами сервисов.

## Проверки

`mvn clean verify` запускает 42 unit-теста и 43 HTTP-интеграционных теста:
собранный WAR разворачивается в Tomcat, проверяются фильтры, JSP, UUID, сессии,
ошибки, аренда и документы с HTTP-заглушками downstream-сервисов.

Тесты API-адаптера без браузера:

```sh
node --test src/test/js/*.test.cjs
```

Для 14 сценариев в настоящем браузере:

```sh
npm ci
npx playwright install chromium
mvn verify dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt
npm run test:e2e
```

Тесты сами поднимают BFF WAR и тестовые HTTP-сервисы. Проверяют регистрацию,
аренду, оплату, PDF, выход, ADMIN, истёкшую сессию, баны, ошибки сервисов,
XSS, избранное, ресурсы и шрифты. Можно использовать установленный Chrome:
`CHROMIUM_EXECUTABLE=/absolute/path/to/chrome npm run test:e2e`.
Node.js 18+ и Playwright нужны только для разработки/проверки.

При подключённых реальных BFF и User доступен дополнительный API-прогон:

```sh
python3 scripts/check-user-integration.py \
  --bff-url http://localhost:8080 \
  --user-url http://localhost:8081
```

Только на одноразовом тестовом стенде: создаёт пользователей и отзывает их токены.
Детали локального развёртывания двух WAR — [USER_SERVICE_SYNC.md](docs/USER_SERVICE_SYNC.md).
Фактические результаты объединения фронта — [FRONTEND_INTEGRATION.md](docs/FRONTEND_INTEGRATION.md).

## Настройки безопасности и зависимости

BFF проверяет JWT через User `/api/users/validate` и передаёт его downstream
без изменений; свой JWT-секрет или приватный ключ BFF не нужны. Готовый User
использует RS256. Владельца поездки и право на PDF должны проверять также Ride
и Documents. Таймаут подключения — 3 секунды, запроса — 10 секунд.

CORS не включён: ScootX и BFF работают с одного origin. За reverse proxy
нужно корректно настроить внешние scheme/host/port Tomcat для проверки Origin.
В production нужны HTTPS и Secure session cookie; HttpOnly и COOKIE-only
включены в приложении. Геолокация требует защищённого контекста браузера.
Сохранена исходная настольная вёрстка ScootX с минимальной шириной 1080 px.

## В текущей облачной машине

```sh
cd /workspace/bff
JAVA_HOME='/workspace/tools/jdk-17.0.20.1+1' \
MAVEN_OPTS='-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
/workspace/tools/apache-maven-3.9.11/bin/mvn \
  -s /workspace/tools/maven-settings.xml \
  -Dmaven.repo.local=/workspace/.m2 verify dependency:build-classpath \
  -Dmdep.outputFile=target/test-classpath.txt
JAVA_HOME='/workspace/tools/jdk-17.0.20.1+1' \
CHROMIUM_EXECUTABLE=/workspace/tools/chrome/opt/google/chrome/chrome npm run test:e2e
```

Системное хранилище доверия и HTTP(S)-прокси используются без отключения TLS.
На обычном локальном компьютере достаточно стандартных JDK/Maven-команд выше.
Исторический отчёт первой доработки BFF — [REVIEW.md](docs/REVIEW.md).

Текущие адреса и контракт входа ADMIN: [API_CONTRACTS.md](docs/API_CONTRACTS.md).

Чтобы прогнать те же браузерные сценарии именно в корневом контексте `/`,
укажите `BFF_TEST_CONTEXT='' npm run test:e2e` (в Bash). Без этой переменной
тесты сохраняют дополнительное покрытие альтернативного `/scooter-bff`.
