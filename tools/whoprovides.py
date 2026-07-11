"""Which jar provides a class? Run this when a crash says NoClassDefFoundError.

    python whoprovides.py <mods-folder> dev/isxander/yacl3/api/controller/ControllerBuilder

Prints the jar that provides it, or tells you nothing does - which means the mod that
needed it is missing a dependency the pack never installed.
"""
import sys, zipfile, glob, os

if len(sys.argv) != 3:
    sys.exit(__doc__)

mods_dir = sys.argv[1]
# Strip ".class" BEFORE turning dots into slashes, or the suffix stops matching.
wanted = sys.argv[2]
if wanted.endswith(".class"):
    wanted = wanted[:-len(".class")]
wanted = wanted.replace(".", "/")
jars = sorted(glob.glob(os.path.join(mods_dir, "*.jar")))
if not jars:
    sys.exit(f"no jars in {mods_dir}")

hits = []
for jar in jars:
    try:
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
    except zipfile.BadZipFile:
        print(f"  ! not a zip: {os.path.basename(jar)}")
        continue
    if f"{wanted}.class" in names:
        hits.append((os.path.basename(jar), "exact class"))
    elif any(n.startswith(wanted.rsplit("/", 1)[0] + "/") for n in names):
        hits.append((os.path.basename(jar), "package present, class missing"))

print(f"scanned {len(jars)} jars in {mods_dir}")
print(f"looking for: {wanted}.class\n")
if hits:
    for jar, how in hits:
        print(f"  PROVIDED BY  {jar}   ({how})")
else:
    print("  NOTHING in this pack provides it.")
    print("  The mod that referenced it is missing a dependency. Add the library mod,")
    print("  or remove the mod that wants it.")
