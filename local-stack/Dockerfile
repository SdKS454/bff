# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /source
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=secret,id=maven_settings,target=/run/secrets/settings.xml \
    --mount=type=secret,id=maven_truststore,target=/run/secrets/cacerts \
    set -eu; settings=''; \
    if [ -f /run/secrets/settings.xml ]; then settings='-s /run/secrets/settings.xml'; fi; \
    if [ -f /run/secrets/cacerts ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/run/secrets/cacerts"; fi; \
    mkdir /output; \
    for app in user moderation scooter ride documents bff; do \
      project="services/$app"; if [ "$app" = bff ]; then project=.; fi; \
      mvn $settings -B -f "$project/pom.xml" -DskipTests package; \
      cp "$project"/target/*.war "/output/$app.war"; \
    done
FROM tomcat:10.1.61-jdk17-temurin
COPY --from=build /output /opt/apps
COPY local-stack/start.sh local-stack/init-keys.sh /opt/stack/
RUN rm -rf /usr/local/tomcat/webapps/*
ENTRYPOINT ["sh", "/opt/stack/start.sh"]
