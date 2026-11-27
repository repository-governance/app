#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
sh "$ROOT/scripts/test.sh"

DIST="$ROOT/dist"
mkdir -p "$DIST"
JAR="$DIST/github-workspace-$VERSION.jar"
ARCHIPLUGIN="$DIST/github-workspace-$VERSION.archiplugin"
rm -f "$JAR" "$ARCHIPLUGIN"
jar --create --file "$JAR" --manifest "$ROOT/build/bundle/META-INF/MANIFEST.MF" \
  -C "$ROOT/build/bundle" plugin.xml \
  -C "$ROOT/build/classes" .
printf '%s\n' 'Magic file to signify this is an Archi plug-in bundle.' > "$ROOT/build/archi-plugin"
(
  cd "$ROOT/build"
  zip -q -j "$ARCHIPLUGIN" archi-plugin
  zip -q -j "$ARCHIPLUGIN" "$JAR"
)
(cd "$DIST" && {
  find . -maxdepth 1 -type f \( -name 'github-workspace-*.jar' -o -name 'github-workspace-*.archiplugin' \
      -o -name 'governance-*.jar' -o -name 'governance-*.archiplugin' \) \
    -printf '%f\n' | sort | xargs -r sha256sum
} > SHA256SUMS)
jar --validate --file "$JAR"
unzip -tq "$ARCHIPLUGIN"
(cd "$DIST" && sha256sum --check SHA256SUMS)
java -classpath "$ROOT/build/classes:$ROOT/build/test-classes:$(paste -sd: "$ROOT/build/archi-jars.list")" \
  org.cda.archi.githubworkspace.tests.ArtifactValidationTests "$ROOT"
echo "Created $JAR and $ARCHIPLUGIN"
