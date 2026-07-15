#!/usr/bin/env bash
# Builds shababparty-<version>.jar and installs it into pack-two/mods/ and server/run/mods/.
#
# No Gradle and no ForgeGradle. We compile straight against the production-mapped jars that the
# server already has on disk, which means Minecraft methods must be written with their SRG names
# (m_9236_ etc) in the source -- there is no reobfuscation step to translate readable names. In
# exchange there is nothing to download, nothing to decompile, and the build is a few seconds.
set -euo pipefail

VERSION=1.7.0
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
LIB="$REPO/server/run/libraries"
MODS="$REPO/server/run/mods"

# Minecraft here is vanilla-in-SRG-names. Forge's own patches (getCapability on Entity, and so on)
# are binary-patched in as the game boots and exist in no jar on disk, so anything Forge adds to a
# Minecraft class has to be reached through the Forge interface that declares it.
MC_SRG="$LIB/net/minecraft/server/1.20.1-20230612.114412/server-1.20.1-20230612.114412-srg.jar"
FORGE_JAR="$LIB/net/minecraftforge/forge/1.20.1-47.4.18/forge-1.20.1-47.4.18-universal.jar"
FMLCORE_JAR="$LIB/net/minecraftforge/fmlcore/1.20.1-47.4.18/fmlcore-1.20.1-47.4.18.jar"
JAVAFML_JAR="$LIB/net/minecraftforge/javafmllanguage/1.20.1-47.4.18/javafmllanguage-1.20.1-47.4.18.jar"
EVENTBUS_JAR="$LIB/net/minecraftforge/eventbus/6.2.33/eventbus-6.2.33.jar"
LOG4J_JAR="$LIB/org/apache/logging/log4j/log4j-api/2.19.0/log4j-api-2.19.0.jar"
# ForgeConfigSpec extends a night-config type, so it is needed just to resolve the class hierarchy.
NIGHTCONFIG_JAR="$LIB/com/electronwill/night-config/core/3.6.4/core-3.6.4.jar"
MIXIN_JAR="$LIB/org/spongepowered/mixin/0.8.5/mixin-0.8.5.jar"
# Component extends com.mojang.brigadier.Message, so brigadier must resolve for any chat code.
BRIGADIER_JAR="$LIB/com/mojang/brigadier/1.1.8/brigadier-1.1.8.jar"
AUTHLIB_JAR="$LIB/com/mojang/authlib/4.0.43/authlib-4.0.43.jar"
FTBTEAMS_JAR="$(ls "$MODS"/ftb-teams-forge-*.jar 2>/dev/null | head -1)"
FTBLIB_JAR="$(ls "$MODS"/ftb-library-forge-*.jar 2>/dev/null | head -1)"
SOLO_JAR="$(ls "$MODS"/sololeveling-*.jar 2>/dev/null | head -1)"
# Solo Leveling's AfterImageEntity implements GeckoLib's GeoEntity, so javac has to be able to
# resolve that interface before it can load the entity class at all.
GECKOLIB_JAR="$(ls "$MODS"/geckolib-forge-*.jar 2>/dev/null | head -1)"

DEPS=("$MC_SRG" "$FORGE_JAR" "$FMLCORE_JAR" "$JAVAFML_JAR" "$EVENTBUS_JAR" "$LOG4J_JAR" \
      "$NIGHTCONFIG_JAR" "$MIXIN_JAR" "$AUTHLIB_JAR" "$FTBTEAMS_JAR" "$FTBLIB_JAR" "$SOLO_JAR" "$GECKOLIB_JAR" "$BRIGADIER_JAR")
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
# gson on the processor path. The mixin references no Minecraft classes, so no refmap is needed.
javac -proc:none -nowarn --release 17 \
    -cp "$CP" \
    -d "$(win "$BUILD/classes")" \
    $(find "$HERE/src" -name '*.java' -exec cygpath -w {} \;)

cp -r "$HERE/res/." "$BUILD/classes/"

JAR="$BUILD/shababparty-$VERSION.jar"
( cd "$BUILD/classes" && jar --create --file "$JAR" --manifest META-INF/MANIFEST.MF -C . . )

# Two shababparty jars in mods/ is a duplicate mod id and Forge will not boot. A version bump
# changes the filename, so the old one has to go rather than sit alongside the new one.
rm -f "$REPO/pack-two/mods"/shababparty-*.jar "$MODS"/shababparty-*.jar

cp "$JAR" "$REPO/pack-two/mods/"
cp "$JAR" "$MODS/"

echo "built and installed: shababparty-$VERSION.jar"
echo "next: cd pack-two && packwiz refresh"
