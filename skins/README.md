# Skin overrides

The pack's CustomSkinLoader checks this folder first, before Ely.by, TLauncher and Mojang.
A file named `<ExactUsername>.png` here is the skin everyone on the server sees for that player.

Use it when a player's name is taken by someone else on a skin site that is checked before the
one they actually use. Example: `endry` is a real Mojang account, but a different person uploaded
a TLauncher skin under the same name, so everyone saw the stranger's skin.

- The file name must match the in-game name exactly, including capital letters.
- 64x64 PNG. Slim (Alex) arms are detected automatically.
- Takes effect after a push to `master` and a game restart. Skins are cached for 30 minutes.

This folder is not part of the pack, so players do not download it. It is only served over
GitHub raw.
