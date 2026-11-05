# Djembe / AfroPlayer

29 pôvodných nahrávok z `C:/Projects/AfroPlayer/resources/samples/djembe_tomasmarek`.
Priradenie bas/tón/slap/tlmený tón a ich zosilnenie vychádza zo starého
`src/core/afp-sampler.rb`. Navyše sú dostupné aj pôvodné alternatívne slapy
`slap2` a tlmené slapy `slapclos`, ktoré starý sampler nepoužíval.

FLAC bol prevedený do mono PCM WAV, 44 100 Hz, 16 bitov, pomocou FFmpeg:

```text
ffmpeg -i INPUT.flac -map_metadata -1 -ac 1 -ar 44100 -c:a pcm_s16le OUTPUT.wav
```

Bez normalizácie, kompresie, orezania začiatku/konca alebo zmeny ladenia.
Zachovávame rozdiely medzi nahrávkami. V JavAfro sa už FFmpeg nevyžaduje.
Súbory nie sú súčasťou CC0 sady JIMMYJAMES112; zachovávajú pôvodné podmienky
z používateľovho projektu AfroPlayer. V pôvodnom adresári nie je licenčný súbor.

Spoločný manifest `../afroplayer-samples.yaml` používa Java aj web. Obsahuje šestice bánk
(bas 1, tón 6, slap 8, tlmený tón 3, tlmený slap 3, alternatívny slap 8),
zosilnenia a pravdepodobnosti. Pri každom údere sa náhodne vyberie jeden variant;
počas doznievania sa už sample ani jeho parametre nemenia.

Hlasitosť a rýchlosť riadia `gain` a `rate` pri jednotlivých bankách, rovnako
ako pôvodné Ruby `amp` a `rate`. Bas má gain 1, tón/slap/tlmený tón gain 2.
Slabšie nové noty používajú relatívny gain 0,7; akcenty a pôvodné noty 1.

Nový zápis: `b/t/s` = bas/tón/slap, `B/T/S` = akcenty. Malé písmená majú
70 % hlasitosti veľkých. `i/I` = tlmený tón, `c/C` = tlmený slap,
`a/A` = alternatívny slap. Pôvodné malé `i` zostáva na pôvodnej hlasitosti.

Aktuálny zápis: `B` bas, `o` tón, `S` slap, `i` tlmený tón;
`E/p/y` hrajú bas/tón/slap s pravdepodobnosťou 60 %, `F/q/z` s 12 %.
Pravdepodobnosť neznamená tichší úder. Malé/veľké `b/t/s` riadia hlasitosť.

Globálne `playback.humanizeDynamic` a `playback.humanizePitch` v `javafro.yaml`
zodpovedajú pôvodným `HUMANIZE_DYNAMIC` a `HUMANIZE_PITCH`. Humanize riadi aj
jemné zmeny dynamiky a ladenia djembe: pri 100 je hlasitosť predvolene
v rozsahu pôvodného gain ±0,07 a rýchlosť samplu 1 ±0,005; pri 0 sú obe
odchýlky vypnuté. Náhodný výber multisamplov a explicitné pravdepodobnosti
zostávajú aktívne aj pri humanize 0, rovnako ako v AfroPlayer.
