#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -cp "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle Wrapper absent. Installez Gradle 8.7 ou utilisez GitHub Actions pour le générer." >&2
exit 1
