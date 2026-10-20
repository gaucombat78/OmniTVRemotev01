#!/usr/bin/env sh
APP_HOME="$(cd -P -- "$(dirname -- "$0")" && pwd -P)" || exit
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec java -jar "$CLASSPATH" "$@"
