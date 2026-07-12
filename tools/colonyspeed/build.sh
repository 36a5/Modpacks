#!/usr/bin/env bash
# Builds colonyspeed-<version>.jar and installs it into pack-two/mods/ and server/run/mods/.
#
# Same no-Gradle approach as tools/shababparty: compile straight against the production jars the
# server already has on disk. The mixins here only touch MineColonies classes, which ship with their
# real names, so unlike shababparty there are no SRG names to write by hand.
set -euo pipefail

VERSION=1.1.0
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
LIB="$REPO/server/run/libraries"
MODS="$REPO/server/run/mods"

MC_SRG="$LIB/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
FORGE_JAR="$LIB/net/minecraftforge/forge/1.20.1-47.4.18/forge-1.20.1-47.4.18-universal.jar"
FMLCORE_JAR="$LIB/net/minecraftforge/fmlcore/1.20.1-47.4.18/fmlcore-1.20.1-47.4.18.jar"
JAVAFML_JAR="$LIB/net/minecraftforge/javafmllanguage/1.20.1-47.4.18/javafmllanguage-1.20.1-47.4.18.jar"
EVENTBUS_JAR="$LIB/net/minecraftforge/eventbus/6.2.33/eventbus-6.2.33.jar"
LOG4J_JAR="$LIB/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar"
# ForgeConfigSpec extends a night-config type, so it is needed just to resolve the class hierarchy.
NIGHTCONFIG_JAR="$LIB/com/electronwill/night-config/core/3.6.4/core-3.6.4.jar"
MIXIN_JAR="$LIB/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar"
# MineColonies plus the three ldtteam libraries its AI classes appear in the signatures of, which
# javac has to resolve before it can load AbstractEntityAIStructure.
MC_COLONIES_JAR="$(ls "$MODS"/minecolonies-*.jar 2>/dev/null | head -1)"
STRUCTURIZE_JAR="$(ls "$MODS"/structurize-*.jar 2>/dev/null | head -1)"
BLOCKUI_JAR="$(ls "$MODS"/blockui-*.jar 2>/dev/null | head -1)"
DOMUM_JAR="$(ls "$MODS"/domum_ornamentum-*.jar 2>/dev/null | head -1)"

DEPS=("$MC_SRG" "$FORGE_JAR" "$FMLCORE_JAR" "$JAVAFML_JAR" "$EVENTBUS_JAR" "$LOG4J_JAR" \
      "$NIGHTCONFIG_JAR" "$MIXIN_JAR" "$MC_COLONIES_JAR" "$STRUCTURIZE_JAR" "$BLOCKUI_JAR" "$DOMUM_JAR")
for j in "${DEPS[@]}"; do
    [ -f "$j" ] || { echo "missing compile dependency: $j" >&2; exit 1; }
done

# javac is a Windows binary and cannot read the MSYS paths ("/c/...") that Git Bash hands out.
win() { cygpath -w "$1"; }

CP=""
for j in "${DEPS[@]}"; do CP="$CP$(win "$j");"; done

BUILD="$HERE/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/classes"

# -proc:none: the mixin jar ships an annotation processor that generates refmaps and crashes without
# gson on the processor path. The mixins reference no Minecraft classes, so no refmap is needed.
javac -proc:none -nowarn --release 17 \
    -cp "$CP" \
    -d "$(win "$BUILD/classes")" \
    $(find "$HERE/src" -name '*.java' -exec cygpath -w {} \;)

cp -r "$HERE/res/." "$BUILD/classes/"

JAR="$BUILD/colonyspeed-$VERSION.jar"
( cd "$BUILD/classes" && jar --create --file "$JAR" --manifest META-INF/MANIFEST.MF -C . . )

cp "$JAR" "$REPO/pack-two/mods/"
cp "$JAR" "$MODS/"

echo "built and installed: colonyspeed-$VERSION.jar"
echo "next: cd pack-two && packwiz refresh"
