#!/usr/bin/env sh
# Launcher for the generated CLI. Builds the shaded jar on first use.
# Usage: ./petstore.sh <command> [options]   e.g.  ./petstore.sh login -u -p
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/petstore-cli/target/petstore-cli-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
  echo "CLI jar not found - building (first run only)..." >&2
  mvn -q -f "$SCRIPT_DIR/pom.xml" clean install -DskipTests || exit 1
fi

exec java -jar "$JAR" "$@"
