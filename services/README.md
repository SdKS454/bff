# Исходники пяти микросервисов

Это канонические согласованные версии, которые собирает корневой compose.yaml.
BFF/ScootX находятся отдельно в корневом src/.

| Каталог | Порт localhost | WAR после mvn verify | Схема БД |
| --- | --- | --- | --- |
| user | 8081 | target/user-service.war | db/001_schema.sql |
| moderation | 8082 | target/moderation.war | src/main/resources/schema.sql |
| scooter | 8083 | target/scooter.war | db.sql |
| ride | 8084 | target/ride-service.war | db/schema.sql |
| documents | 8085 | target/documents.war | src/main/resources/schema.sql |

Все приложения: Java 17+, Servlet 6.0, Tomcat 10.1, контекст `/`.
Для каждого сервиса есть LOCAL_SETUP.md. Основная инструкция:
[local-stack/README.md](../local-stack/README.md).

User получен ранее как scooter-user-service.zip; Moderation — moderation-main.zip;
Documents — Documents_for_integration.zip; Scooter и Ride — scooter.zip и
ride-service.zip. Существенные исправления перечислены в
[STACK_INTEGRATION.md](../docs/STACK_INTEGRATION.md).
Первоначальные архивы и старые временные handoff-сборки не заменяют эти версии.
