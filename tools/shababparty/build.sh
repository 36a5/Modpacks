#!/usr/bin/env bash
# Builds shababparty-<version>.jar and installs it into pack-two/mods/ and server/run/mods/.
#
# No Gradle and no ForgeGradle. We compile straight against the production-mapped jars that the
# server already has on disk, which means Minecraft methods must be written with their SRG names
# (m_9236_ etc) in the source -- there is no reobfuscation step to translate readable names. In
# exchange there is nothing to download, nothing to decompile, and the build is a few seconds.
set -euo pipefail

VERSION=1.21.0
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
LIB="$REPO/server/run/libraries"
MODS="$REPO/server/run/mods"

# Minecraft here is vanilla-in-SRG-names. Forge's own patches (getCapability on Entity, and so on)
# are binary-patched in as the game boots and exist in no jar on disk, so anything Forge adds to a
# Minecraft class has to be reached through the Forge interface that declares it.
#
# This is the *joined* jar -- client and server together. The server-only jar that used to be here
# has no net.minecraft.client at all, so nothing client-side (keybinds, screens, world rendering)
# could be compiled against it.
#
# It is deliberately NOT committed: it is Mojang's game code, this repo is public, and redistributing
# the client binary is not ours to do. libs/ is gitignored and the jar is reconstructed locally from
# the minecolonies-fork ForgeGradle cache, which every machine that has built that fork already has.
MC_SRG="$HERE/libs/minecraft-1.20.1-joined-srg.jar"
if [ ! -f "$MC_SRG" ]; then
    FG_JOINED="$REPO/../minecolonies-fork/build/fg_cache/mcp/1.20.1-20230612.114412/joined"
    FG_SRG="$(ls "$FG_JOINED"/*/rename/output.jar 2>/dev/null | head -1)"
    if [ -n "$FG_SRG" ] && [ -f "$FG_SRG" ]; then
        echo "bootstrapping $(basename "$MC_SRG") from the minecolonies-fork ForgeGradle cache"
        mkdir -p "$HERE/libs"
        cp "$FG_SRG" "$MC_SRG"
    else
        cat >&2 <<'MSG'
error: tools/shababparty/libs/minecraft-1.20.1-joined-srg.jar is missing and could not be rebuilt.

This is the joined (client+server) vanilla jar in SRG names. It is not committed to git on purpose --
it is Mojang's code and this repo is public.

To produce it, build the minecolonies fork once so ForgeGradle populates its cache:

    cd ../../minecolonies-fork && ./gradlew build

then re-run this script; it will copy the jar out of
minecolonies-fork/build/fg_cache/mcp/1.20.1-20230612.114412/joined/*/rename/output.jar
MSG
        exit 1
    fi
fi
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
# FriendlyByteBuf extends io.netty.buffer.ByteBuf, so netty must resolve before any packet code can
# call writeFloat/readFloat.
NETTY_JAR="$LIB/io/netty/netty-buffer/4.1.82.Final/netty-buffer-4.1.82.Final.jar"
# ByteBuf implements io.netty.util.ReferenceCounted, which lives in netty-common rather than
# netty-buffer -- without it javac cannot complete the hierarchy and every buf call fails.
NETTY_COMMON_JAR="$LIB/io/netty/netty-common/4.1.82.Final/netty-common-4.1.82.Final.jar"
# Dist and @OnlyIn live in mergetool's api jar, not in any forge/fml jar.
DISTMARKER_JAR="$LIB/net/minecraftforge/mergetool/1.1.5/mergetool-1.1.5-api.jar"
# PoseStack.mulPose takes an org.joml.Quaternionf and Font.drawInBatch takes an org.joml.Matrix4f.
JOML_JAR="$LIB/org/joml/joml/1.10.5/joml-1.10.5.jar"
FTBTEAMS_JAR="$(ls "$MODS"/ftb-teams-forge-*.jar 2>/dev/null | head -1)"
FTBLIB_JAR="$(ls "$MODS"/ftb-library-forge-*.jar 2>/dev/null | head -1)"
SOLO_JAR="$(ls "$MODS"/sololeveling-*.jar 2>/dev/null | head -1)"
# Solo Leveling's AfterImageEntity implements GeckoLib's GeoEntity, so javac has to be able to
# resolve that interface before it can load the entity class at all.
GECKOLIB_JAR="$(ls "$MODS"/geckolib-forge-*.jar 2>/dev/null | head -1)"

DEPS=("$MC_SRG" "$FORGE_JAR" "$FMLCORE_JAR" "$JAVAFML_JAR" "$EVENTBUS_JAR" "$LOG4J_JAR" \
      "$NIGHTCONFIG_JAR" "$MIXIN_JAR" "$AUTHLIB_JAR" "$FTBTEAMS_JAR" "$FTBLIB_JAR" "$SOLO_JAR" \
      "$GECKOLIB_JAR" "$BRIGADIER_JAR" "$NETTY_JAR" "$NETTY_COMMON_JAR" "$DISTMARKER_JAR" "$JOML_JAR")
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

# Stamp the real version into the jar's mods.toml. res/META-INF/mods.toml carried a hand-written
# version that nobody ever bumped, so every build from 1.19.0 through 1.21.0 reported itself to
# Forge as "1.2.0". That made the server log useless for answering "which shababparty jar is
# actually loaded?" -- which is exactly the question that mattered when a stale jar and a fresh one
# ended up in mods/ together. VERSION above is now the single source of truth.
sed -i "s|^version=.*|version=\"$VERSION\"|" "$BUILD/classes/META-INF/mods.toml"
grep -q "version=\"$VERSION\"" "$BUILD/classes/META-INF/mods.toml" \
    || { echo "failed to stamp version into mods.toml" >&2; exit 1; }

JAR="$BUILD/shababparty-$VERSION.jar"
( cd "$BUILD/classes" && jar --create --file "$JAR" --manifest META-INF/MANIFEST.MF -C . . )

# Two shababparty jars in mods/ is a duplicate mod id and Forge will not boot. A version bump
# changes the filename, so the old one has to go rather than sit alongside the new one.
rm -f "$REPO/pack-two/mods"/shababparty-*.jar "$MODS"/shababparty-*.jar

cp "$JAR" "$REPO/pack-two/mods/"
cp "$JAR" "$MODS/"

echo "built and installed: shababparty-$VERSION.jar"
echo "next: cd pack-two && packwiz refresh"
