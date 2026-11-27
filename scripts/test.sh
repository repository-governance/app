#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${ARCHI_PLUGINS:?Set ARCHI_PLUGINS to the plugins directory from Archi 5.11.0}"
sh "$ROOT/scripts/build.sh"
mkdir -p "$ROOT/build/test-classes"
find "$ROOT/tests" -name '*.java' -print > "$ROOT/build/test-sources.list"
find "$ARCHI_PLUGINS" -type f -name '*.jar' -print > "$ROOT/build/archi-jars.list"
PLATFORM_CP=$(paste -sd: "$ROOT/build/archi-jars.list")
javac --release 21 --add-modules jdk.httpserver -encoding UTF-8 \
  -classpath "$ROOT/build/classes:$PLATFORM_CP" \
  -d "$ROOT/build/test-classes" @"$ROOT/build/test-sources.list"
java --add-modules jdk.httpserver \
  -classpath "$ROOT/build/classes:$ROOT/build/test-classes:$PLATFORM_CP" \
  org.cda.archi.githubworkspace.tests.GitHubWorkspaceTests
