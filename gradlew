#!/usr/bin/env sh
set -e

APP_HOME=$(cd "$(dirname "$0")"; pwd -P)
LOCAL_GRADLE="$HOME/.gradle/wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj/gradle-9.4.1/bin/gradle"

if [ -n "$GRADLE_HOME" ] && [ -x "$GRADLE_HOME/bin/gradle" ]; then
  exec "$GRADLE_HOME/bin/gradle" "$@"
fi

if [ -x "$LOCAL_GRADLE" ]; then
  exec "$LOCAL_GRADLE" "$@"
fi

echo "Gradle 9.4.1 is not available locally. Install Gradle or set GRADLE_HOME." >&2
exit 1
