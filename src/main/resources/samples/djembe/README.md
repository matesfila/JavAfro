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

Spoločný manifest `../djembe.yaml` používa Java aj web. Obsahuje šestice bánk
(bas 1, tón 6, slap 8, tlmený tón 3, tlmený slap 3, alternatívny slap 8),
zosilnenia a pravdepodobnosti. Pri každom údere sa náhodne vyberie jeden variant;
počas doznievania sa už sample ani jeho parametre nemenia.

Spoločný výstupný gain 0,45 poskytuje rezervu aj pre najsilnejšie akcenty a
kladnú odchýlku dynamiky. Uplatňuje sa rovnako na všetky banky v Jave aj vo webe;
nemení relatívne hlasitosti medzi údermi ani samotné WAV súbory.

Nový zápis: `d/t/s` = bas/tón/slap, `D/T/S` = akcenty. Malé písmená majú
70 % hlasitosti veľkých. `i/I` = tlmený tón, `c/C` = tlmený slap,
`a/A` = alternatívny slap. Pôvodné malé `i` zostáva na pôvodnej hlasitosti.

Pôvodný zápis sa dá prehrať priamo: `D` bas, `o` tón, `x` slap, `i` tlmený tón;
`E/p/y` hrajú bas/tón/slap s pravdepodobnosťou 60 %, `F/q/z` s 12 %.
Pravdepodobnosť neznamená tichší úder. Malé/veľké `d/t/s` riadia hlasitosť.

Humanize riadi aj jemné zmeny dynamiky a ladenia djembe: pri 100 je hlasitosť
v rozsahu pôvodného gain ±0,07 a rýchlosť samplu 1 ±0,005; pri 0 sú obe
odchýlky vypnuté. Náhodný výber multisamplov a explicitné pravdepodobnosti
zostávajú aktívne aj pri humanize 0, rovnako ako v AfroPlayer.
