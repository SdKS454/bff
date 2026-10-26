#!/bin/sh
set -eu
case "${APP_NAME:-}" in user|moderation|scooter|ride|documents|bff) ;; *) echo 'Set APP_NAME to a service name' >&2; exit 1;; esac
rm -rf /usr/local/tomcat/webapps/ROOT
mkdir -p /usr/local/tomcat/webapps/ROOT
cd /usr/local/tomcat/webapps/ROOT
jar xf "/opt/apps/$APP_NAME.war"
if [ "${NEED_SERVICE_KEY:-false}" = "true" ]; then
  SERVICE_API_KEY="$(cat /service-key/service.key)"
  export SERVICE_API_KEY
fi
exec catalina.sh run
