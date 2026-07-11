# Skins on a cracked account

The server runs in **offline mode** so that TLauncher and other non-premium players can join.
The cost of offline mode is that Mojang never tells the server what your skin is, so by default
everyone would be a default Steve or Alex.

The pack fixes that with **CustomSkinLoader**. It ships with the pack — you do not install
anything. Instead of asking Mojang, it looks your skin up **by your username** on a list of skin
hosts, in this order:

| Order | Host | Who it's for |
|---|---|---|
| 1 | **Ely.by** | Anyone. Free, no Minecraft account needed. **This is the one to use.** |
| 2 | **TLauncher** | TLauncher players who already set a skin inside the launcher. |
| 3 | **LittleSkin** | Alternative host, if you already have an account there. |
| 4 | **Mojang** | Players whose username is a real premium account — your normal skin just works. |
| 5 | **Local** | A `.png` you drop in the folder yourself (see below). |

The first host that has a skin for your name wins, so an Ely.by skin overrides a TLauncher one.

**Everyone sees everyone.** CustomSkinLoader is on every player's client because it's part of the
pack, so your skin shows up for the whole server, not just for you.

---

## Get a skin (Ely.by — recommended)

1. Sign up at <https://ely.by/register>. **Use the exact same username you use to join the
   server.** This is the whole trick — the skin is matched by name.
2. Go to <https://ely.by/skins/add>, upload your skin `.png`, and pick Steve (classic) or Alex
   (slim) arms.
3. Restart Minecraft. Done — no launcher settings, no mod install.

Ely.by also hosts capes if you want one.

## Already a TLauncher user

If you set your skin in TLauncher's own skin manager, it works as-is — the pack reads TLauncher's
skin server too. Only use this if you don't want an Ely.by account; Ely.by is more reliable and
does not depend on which launcher you play from.

## Just for yourself (local skin)

Drop a `.png` at `.minecraft/CustomSkinLoader/LocalSkin/skins/<YourUsername>.png` and it loads
instantly. **Only you see it** — nobody else on the server does, because the file is on your PC.
Fine for testing, useless for showing off.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Still Steve after uploading | Your Ely.by username and your in-game username don't match exactly (capitalisation counts). |
| Skin changed but the game shows the old one | Skins are cached for 30 minutes. Restart the game, or delete `.minecraft/CustomSkinLoader/cache/`. |
| Worked yesterday, Steve today | The skin host was down. CustomSkinLoader falls back to the next host in the list, and to Steve if none answer. |
| Want to see what it's doing | Read `.minecraft/CustomSkinLoader/CustomSkinLoader.log` — it names the host it fetched each player from. |

Do **not** edit `.minecraft/CustomSkinLoader/CustomSkinLoader.json` unless you know what you're
doing; the pack ships it preconfigured with the host list above.
