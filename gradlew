#!/bin/sh
# Упрощённый gradlew: сам скачивает Gradle 8.9 при первом запуске (нужны curl или wget, и unzip).
V=8.9
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper-lite"
D="$BASE/gradle-$V"
if [ ! -x "$D/bin/gradle" ]; then
  echo "Скачиваю Gradle $V..."
  mkdir -p "$BASE" || exit 1
  T="$BASE/tmp.zip"
  URL="https://services.gradle.org/distributions/gradle-$V-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$T" || exit 1
  else
    wget -O "$T" "$URL" || exit 1
  fi
  unzip -q -o "$T" -d "$BASE" || exit 1
  rm -f "$T"
fi
exec "$D/bin/gradle" "$@"
