#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
: "${ARCHI_PLUGINS:?Set ARCHI_PLUGINS to the plugins directory from Archi 5.11.0}"
[ -d "$ARCHI_PLUGINS" ] || { echo "Archi plug-ins directory not found: $ARCHI_PLUGINS" >&2; exit 1; }

mkdir -p "$ROOT/build"
rm -rf "$ROOT/build/classes"/*
rm -rf "$ROOT/build/bundle"
mkdir -p "$ROOT/build/classes"
find "$ROOT/src" -name '*.java' -print > "$ROOT/build/sources.list"
find "$ARCHI_PLUGINS" -type f -name '*.jar' -print > "$ROOT/build/archi-jars.list"
CLASSPATH=$(paste -sd: "$ROOT/build/archi-jars.list")
[ -n "$CLASSPATH" ] || { echo "No Archi platform JARs found" >&2; exit 1; }

javac --release 21 -encoding UTF-8 -classpath "$CLASSPATH" \
  -d "$ROOT/build/classes" @"$ROOT/build/sources.list"
mkdir -p "$ROOT/build/bundle/META-INF"
cp -R "$ROOT/build/classes/." "$ROOT/build/bundle/"
mkdir -p "$ROOT/build/bundle/icons"
cp "$ROOT/icons/github.png" "$ROOT/icons/catalogs.png" "$ROOT/build/bundle/icons/"
sed "s/@VERSION@/$VERSION/g" "$ROOT/META-INF/MANIFEST.MF" > "$ROOT/build/bundle/META-INF/MANIFEST.MF"
cp "$ROOT/plugin.xml" "$ROOT/build/bundle/plugin.xml"
echo "Compiled GitHub Workspace $VERSION against $(wc -l < "$ROOT/build/archi-jars.list" | tr -d ' ') official Archi platform bundles."
