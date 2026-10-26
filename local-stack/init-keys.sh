#!/bin/sh
set -eu
mkdir -p /keys /public /service-key /tmp/user-keygen
cd /tmp/user-keygen
jar xf /opt/apps/user.war
if [ ! -f /keys/private.pem ] && [ ! -f /keys/public.pem ]; then
  java -cp '/tmp/user-keygen/WEB-INF/classes:/tmp/user-keygen/WEB-INF/lib/*' ru.scooter.user.cli.GenerateKeys /keys
elif [ ! -f /keys/private.pem ] || [ ! -f /keys/public.pem ]; then
  echo 'Incomplete RSA key pair: restore the missing key before starting User.' >&2
  exit 1
fi
cp /keys/public.pem /public/public.pem
if [ ! -s /service-key/service.key ]; then
  umask 077
  openssl rand -hex 32 > /service-key/service.key
fi
