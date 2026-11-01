# Game language support

OpenMapMC Delta follows the language selected in Minecraft. The game UI and the
Web map have matching translation key sets for these locales:

| Locale | Language |
| --- | --- |
| `en_us` | English |
| `ja_jp` | Japanese |
| `zh_cn` | Simplified Chinese (China) |
| `zh_tw` | Traditional Chinese (Taiwan) |
| `zh_hk` | Traditional Chinese (Hong Kong) |
| `yue_hk` | Cantonese (Hong Kong), added by OpenMapMC Delta |
| `fr_fr` | French (France) |
| `hi_in` | Hindi |
| `ru_ru` | Russian |
| `pt_br` | Portuguese (Brazil) |
| `pt_pt` | Portuguese (Portugal) |
| `es_es` | Spanish (Spain) |
| `de_de` | German |
| `cs_cz` | Czech (Čeština) |
| `ms_my` | Malay (Malaysia) |
| `ro_ro` | Romanian |

The generic Portuguese request is covered by the Brazilian and European
Portuguese catalogs. Malay is `ms_my`, named “Bahasa Melayu” in the official
game metadata.

Mojang's official assets for Minecraft 1.20.1 and 26.2 list `zh_hk` as Traditional
Chinese for Hong Kong, not Cantonese, and contain no separate Cantonese locale.
OpenMapMC Delta registers `yue_hk` (“粵語”, Hong Kong) in its resource pack metadata
so it can be selected in Minecraft's language settings. Its OpenMapMC Delta text is
Cantonese; vanilla Minecraft and other mods fall back to English unless a
resource pack supplies their Cantonese translations. This is an additional
locale, not a change to `zh_hk`.

All 16 catalogs distinguish the beam hide-distance setting from the separate
icon/label fade-start distance. Both labels express distances in meters; the
fade-start label describes when the icon and label begin to become transparent,
while the hide-distance label applies only to the beam.

The verified official language metadata is the `pack.mcmeta` asset referenced
by the game asset indexes, SHA-1 `6cb57c8bf7f43ecb75339be01e23b5be0c83f0c9`:

- [Minecraft 1.20.1 official asset index](https://piston-meta.mojang.com/v1/packages/1c3a5c1a8cdc878cc249f3c3b68c7f939076bca8/5.json)
- [Minecraft 26.2 official asset index](https://piston-meta.mojang.com/v1/packages/a7b53da5fa967c3e8196712f1b734bbc9df44bb6/32.json)
- [Official language metadata](https://resources.download.minecraft.net/6c/6cb57c8bf7f43ecb75339be01e23b5be0c83f0c9)

Run `scripts/test-locales.ps1` to check all version roots for valid UTF-8 JSON,
identical translation key sets, format placeholders, source references, enum
labels, substantial translation coverage, and Cantonese language metadata.

On 2026-10-28, the 135 recent area-editor, server-feedback and controller-guide
entries in 26.2 were localized for the fourteen non-English/Japanese catalogs.
The existing 477 values in each catalog were preserved; this does not claim that
every older English fallback has been translated. Japanese and English are unchanged.
All four version roots pass the checks: 64 catalogs, with 476 / 480 / 422 / 612
keys per catalog in 1.20.1 / 1.21.1 / 26.1.2 / 26.2 respectively.
Source-reference checks include the newer `gui.openmapdelta.*` and
`openmapdelta.area.*` namespaces and the 26.2 controller-hint mode labels.
