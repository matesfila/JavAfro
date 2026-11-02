# JavAfro

Prehrávač afrických rytmov so Swing GUI, YAML editorom a zvukovými samplami.

## Konfigurácia aplikácie

Predvolené nastavenia sú v [javafro.yaml](javafro.yaml):

| Nastavenie | Význam |
| --- | --- |
| `playback.defaultTrackVolume` | Predvolená hlasitosť každého tracku v mixeri, celé číslo 0–100 %, predvolene 40. Hodnota zodpovedá percentám v UI. |
| `playback.humanize` | Počiatočná humanizácia 0–100; posuvník ju môže zmeniť počas hrania. |
| `playback.humanizeApplianceProbability` | Pravdepodobnosť použitia humanizácie na každú notu, celé číslo 0–100, predvolene 100. Pri 50 sa pre každú notu nezávisle rozhodne s pravdepodobnosťou 50 %. |
| `playback.humanizeMaxOffsetMs` | Maximálny časový posun ±ms pri humanizácii 100 % (0–1000, predvolene 30). Hodnota 0 vypne časové odchýlky. |
| `playback.humanizeDynamic` | Globálna odchýlka gain samplov pri humanize 100, predvolene ±0,07 ako pôvodné `HUMANIZE_DYNAMIC`. |
| `playback.humanizePitch` | Globálna odchýlka rate samplov pri humanize 100, predvolene ±0,005 ako pôvodné `HUMANIZE_PITCH`. |
| `playback.tempo.initial` | Globálne predvolené BPM; použije sa aj pre rytmus bez `bpm`. |
| `playback.tempo.minimum`, `maximum` | Predvolený rozsah posuvníka; rozšíri sa pre rytmus s BPM mimo rozsahu. |
| `library.directory` | Priečinok rytmov pre Java výber súborov a zostavenie webovej knižnice. |
| `library.initialRhythm` | Relatívna cesta prvého rytmu webovej verzie, napr. `imported/Djole.yaml`. |
| `editor.fontSize` | Veľkosť písma YAML editora v Java aj webovej verzii. |
| `window.width`, `height` | Počiatočná veľkosť Java okna. |

Java načíta konfiguráciu pri štarte: najprv `javafro.yaml` v pracovnom priečinku,
potom vedľa JAR, prípadne vedľa Windows spúšťača. Ak externý súbor nenájde,
použije pribalenú konfiguráciu. `library.directory` sa vyhodnocuje voči priečinku
externého konfiguračného súboru; pri pribalenej konfigurácii voči pracovnému priečinku.
Konfigurácia môže obsahovať iba zmenené položky; ostatné dostanú predvolené hodnoty.
Neznáme nastavenia, duplicitné kľúče, nesprávne typy a hodnoty mimo rozsahu vyvolajú chybu.

Po úprave konfigurácie Java aplikáciu reštartuj. Web preberá rovnaký súbor pri
spustení vývoja alebo zostavení; offline verziu treba znovu zostaviť a aktualizovať.
Konfigurácia sa automaticky neprepisuje pri posúvaní ovládacích prvkov.
Časová odchýlka sa škáluje percentom humanizácie a naďalej sa obmedzuje na štvrtinu
najkratšej medzery medzi pulzmi podľa dialectu. Vypnutie časových odchýlok nemení
humanizáciu hlasitosti a ladenia djembe.
`humanizeApplianceProbability` vyberá raz pre každú notu, či sa na ňu použije
nastavená intenzita Humanize (čas, hlasitosť aj ladenie spolu), alebo Humanize 0.
Pri 100 sa zachová pôvodné správanie, pri 0 sa humanizácia nepoužije.
Automatický zvonček zdieľa rozhodnutie bubna. Dialect, náhodný výber samplov
a pravdepodobnosť zahratia noty zostávajú aktívne aj pri vynechaní humanizácie.
Pravdepodobnosť možno meniť aj počas hrania. V desktopovej aj webovej verzii sú
posuvníky **Intenzita** a **Pravdepodobnosť** spolu v rozbaľovacom paneli **Humanize**.
Samostatne možno rozbaliť aj **Dialect**. Oba panely sú predvolene zbalené;
zbalenie zachová nastavenia aj prehrávanie. Konfigurácia určuje počiatočnú hodnotu;
zmeny posuvníka sa zachovajú pri Stop/Play aj zmene rytmu a neukladajú sa do YAML.
Tempo, metrum, delenie doby a dialect konkrétneho rytmu naďalej patria do jeho YAML.

## Webová verzia

Java aj webový prehrávač majú **Mixer** so zobrazením hlasitosti 0–100 % a prepínačom
**Mute** pre každý track. V desktopovej verzii tlačidlo **Mixer…** otvorí samostatné
okno; na webe rozbalíš panel **Mixer**. Zatvorenie okna alebo zbalenie panela
zachová nastavený mix a prehrávanie pokračuje. Zmeny fungujú aj počas hrania; vypnutie mute obnoví
nastavenú hlasitosť. Stlmenie tracku nemení hlasitosť ostatných trackov.
Stop/Play zachová mix aktuálneho rytmu. Nastavenia mixera sa neukladajú do YAML;
otvorenie súboru ich obnoví na `playback.defaultTrackVolume` (predvolene 40 %) bez mute.
Zobrazená hlasitosť je lineárna: 50 % = 1× amplitúda, 100 % = 2× (približne +6 dB).
Konfigurácia `playback.defaultTrackVolume` používa rovnakú stupnicu 0–100 ako UI;
hodnota 35 sa zobrazí ako 35 %. Pri prechode zo starej stupnice 0–200 vydeľ
konfiguračnú hodnotu dvoma, aby sa zachovala zvuková úroveň.
Posuvník **Master** ovláda celý výstup v rozsahu 0–100 %, predvolene 25 %
(pôvodný násobok 0,25). Master 100 % je štvornásobná amplitúda oproti 25 %,
približne +12 dB. Mix sa naďalej delí počtom trackov; pri hlasných samploch
a prekrývaní úderov môže vzniknúť clipping. Master funguje aj počas hrania,
zachová sa pri Stop/Play aj zmene rytmu alebo súboru a neukladá sa do YAML.

V adresári [web](web/README.md) je React/TypeScript verzia s YAML editorom,
rovnakými rytmami a samplami, nepretržitým prehrávaním, tempom, humanizáciou
a dialectom. Spustenie: `cd web`, `npm ci`, `npm run dev`.
Nepotrebuje Java backend; produkčný build vytvorí statický adresár `web/dist`.

Na stiahnutie offline verzie na iPhone z vlastného PC spusti v `web` príkaz
`npm run phone`. Podrobný postup lokálneho HTTPS, inštalácie certifikátu a
pridania na plochu je v [návode pre telefóny](web/README.md#telefóny).

## Swing prehrávač rytmov

`picosoft.javafro.AfroJavaPlayer` otvorí editor YAML a ovládanie prehrávania:

```powershell
mvn package
& "$env:JAVA_HOME/bin/java.exe" -cp target/JavAfro.jar picosoft.javafro.AfroJavaPlayer "rhythms/basicRhythms.yaml"
```

Voliteľný argument je cesta k jednému YAML súboru na disku (UTF-8).
Bez argumentu sa otvorí prázdny editor; predvolený rytmus z resources sa nenačítava.
Relatívna cesta sa vyhodnocuje voči pracovnému priečinku. V okne je aj tlačidlo **Otvoriť YAML…**.
Jeden súbor `.yaml` alebo `.yml` možno tiež presunúť z Prieskumníka priamo do okna
alebo editora. Načíta sa jeho obsah, tempo a dialect. Počas prehrávania treba najprv
stlačiť Stop. Viac súborov, priečinky a iné prípony sa odmietnu; pri neplatnom YAML
zostane doterajší obsah zachovaný. Bežné kopírovanie a vkladanie textu funguje naďalej.
Editor RSyntaxTextArea zvýrazňuje YAML syntax,
skrýva čísla riadkov a pri stlačení Tab odsádza dvoma medzerami.
Má veľkú plochu s neproporcionálnym
písmom a posuvníkmi pre dlhšie zápisy. Jeden súbor môže obsahovať viac rytmov
ako samostatné hlavné YAML kľúče. Play prehrá rytmus, v ktorého definícii je kurzor.
Text pred prvým kľúčom vyberá prvý rytmus; medzera alebo komentár medzi definíciami
patrí predchádzajúcemu rytmu. Presun kurzora počas hrania neprepína zvuk.
Play zostáva dostupné: ďalšie stlačenie zastaví aktuálny rytmus a od začiatku
spustí definíciu pod kurzorom vrátane úprav YAML. Pri rýchlych kliknutiach platí
posledný výber; Stop zruší aj čakajúce spustenie. Súbor z argumentu príkazového riadka
sa iba načíta, s kurzorom na začiatku. Prehrávanie sa spustí až po stlačení Play.

**Play** načíta aktuálny YAML a prehráva základné patterny nepretržite až po **Stop**.
Posuvníky **Tempo** a **Humanize** fungujú aj počas prehrávania bez reštartovania
rytmu. Zmena sa použije od ďalšieho generovaného kroku, s malým oneskorením daným
už pripraveným zvukom. Tempo posuvníka má pri prehrávaní prednosť pred BPM v editore;
pri otvorení súboru sa posuvník inicializuje jeho BPM. Zmeny YAML sa použijú pri
ďalšom Play. Zatvorenie okna zastaví aj zvuk. Spúšťací JAR teraz otvára rytmický prehrávač.

Nepretržité prehrávanie možno použiť aj bez GUI (blokuje volajúce vlákno):

```java
PlaybackSettings controls = new PlaybackSettings(rhythm.bpm(), 40);
BeepSequencer sequencer = new BeepSequencer();
// Spusti vo vlastnom vlákne:
sequencer.playContinuously(rhythm, controls);
// Z ovládacieho vlákna: controls.setBpm(140); controls.setHumanize(70);
// Zastavenie: sequencer.stop(); na ďalšie prehrávanie vytvor nový sequencer.
```

Pôvodné metódy s počtom opakovaní zostávajú dostupné.

### Spustenie aktuálneho YAML v IntelliJ IDEA

Zdieľaná run konfigurácia **AfroJavaPlayer - Current YAML** je v
`.run/AfroJavaPlayer_Current_YAML.run.xml`. Otvor a ulož YAML súbor, nechaj ho
aktívny v editore a spusti túto konfiguráciu. Makro `"$FilePath$"` odovzdá
absolútnu cestu k aktuálne zvolenému súboru vrátane prípadných medzier.
Konfigurácia používa modul `JavAfro`, hlavný vstup `picosoft.javafro.AfroJavaPlayer`
a pracovný priečinok koreňa projektu. Pred spustením sa vykoná Build.

### Dialect

Každá hodnota `dialect` je nezávislý posun jedného pulzu od rovnej pozície:

```yaml
stepsPerBeat: 4
dialect: [0, -0.4, 0, 0]
```

Počet hodnôt musí byť presne `stepsPerBeat`. Rozsah je −1 až +1; nula znamená
presnú pozíciu, záporné číslo skorší úder a kladné neskorší. Krajné hodnoty
posúvajú pulz o polovicu štandardného kroku. V príklade príde iba druhý pulz
skôr o 20 % kroku; ostatné zostanú na svojich miestach. Pri 120 BPM a štyroch
pulzoch sú pozície 0, 100, 250 a 375 ms. Ďalší beat stále začína v 500 ms.

Neutrálne nastavenie je `[0, 0, 0, 0]`, pri troch pulzoch `[0, 0, 0]`.
Vynechané `dialect` znamená samé nuly. Starý zápis dĺžok medzier sa už nepoužíva.
Medzery sa odvodzujú z pozícií vrátane hranice beatov; neposúva sa BPM ani ostatné
pulzy. Krajné susedné posuny +1 a −1 môžu spojiť dva pulzy v rovnakom čase,
ale neprehodia ich poradie. Prvý úder pred začiatkom prehrávania sa oreže na nulu.

UI má jeden posuvník pre každý pulz (štyri pri Djole, tri pri triolovom delení).
Všetky majú rovnaký pevný rozsah −1 až +1 a krok 0,01. Žiadny nemení hodnotu
ani rozsah iného posuvníka. Zmeny sa používajú od ďalšieho generovaného beatu.
Humanizácia pridáva náhodné odchýlky okolo dialectových pozícií, pričom rešpektuje
najkratšiu odvodenú medzeru. Pri spojených pulzoch je jej rozsah nulový.
Pre skoršie údery si prehrávač pripravuje krátky úsek zvuku dopredu; živé zmeny
preto majú oneskorenie podľa tempa a delenia beatu.

Načítanie súboru alebo zmena YAML a ďalší Play nastavia posuvníky podľa YAML.
Posuvníky nemenia zdrojový YAML; bez jeho úpravy zostanú ich nastavenia zachované
aj po Stop/Play. Dialect je zatiaľ spoločný pre všetky tracky.

## Rhythm

### Djembe z AfroPlayer

Nástroj `picosoft.javafro.instruments.sample.matesamples.DjembeInstrument` používa 29 pôvodných
samplov Tomáša Mareka. Funguje v Java aj webovom prehrávači; v knižnici je
`rhythms/djembe-demo.yaml` s ukážkou všetkých úderov aj pôvodného zápisu.

```yaml
djembe-demo:
  bpm: 100
  timeSignature: "4/4"
  stepsPerBeat: 4
  dialect: [0, 0, 0, 0]
  tracks:
    djembe: "d..t.s..D.tt.S.."
```

`d` = bas, `t` = tón, `s` = slap; `D/T/S` sú akcenty (malé písmená majú 70 %
ich hlasitosti). `i/I` hrá tlmený tón, `c/C` tlmený slap a `a/A` alternatívny slap.
Pôvodné `i` ostáva na pôvodnej hlasitosti. `.` znamená ticho a `|` oddeľuje takty.
Basové bubny prijímajú aj pôvodné otvorené/uzavreté údery a zvonce opísané nižšie.

Zachovaný je aj pôvodný zápis AfroPlayer: `D/o/x/i`. Znaky `E/p/y` hrajú bas/tón/slap
s pravdepodobnosťou 60 %, `F/q/z` s 12 %; neznamenajú nižšiu hlasitosť.
Každý úder náhodne vyberá jeden multisample. Humanize navyše mení hlasitosť
(pri 100 ±0,07) a ladenie/rýchlosť (±0,5 %); pri 0 sú tieto odchýlky vypnuté.
Výber samplu aj parametre zostávajú nemenné počas celého doznievania úderu.

Zdroje a prevod audio súborov sú zdokumentované v
`src/main/resources/samples/djembe/README.md`. Spoločný manifest `samples/afroplayer-samples.yaml`
riadi mapovanie a hlasitosti v oboch prehrávačoch. Pri používaní nástroja nie je
potrebný AfroPlayer, Sonic Pi ani FFmpeg.

### Ostatné pôvodné nástroje AfroPlayer

Triedy pôvodných nástrojov sú v `picosoft.javafro.instruments.sample.matesamples`:

| YAML nástroj | Java trieda |
| --- | --- |
| `dundun` | `DundunInstrument` |
| `sangban` | `SangbanInstrument` |
| `kensereni` alebo `kenken` | `KensereniInstrument` |
| `djembe2` | `Djembe2Instrument` |
| `dundun_bell` | `DundunBellInstrument` |
| `sangban_bell` | `SangbanBellInstrument` |
| `kensereni_bell` alebo `kenken_bell` | `KensereniBellInstrument` |

Basové bubny používajú pôvodné multisample: `X` aj `x` sú otvorený úder,
`i/I` uzavretý úder a `b` samotný zvonec. Každý úspešný úder bubna automaticky
pridá jeho zvonec, rovnako ako AfroPlayer. `A/B/C` sú otvorené údery
s pravdepodobnosťou 12/40/80 %; pri vynechaní úderu nezaznie ani zvonec.
Samostatné zvonce prijímajú `b`, `x`, `X` a ďalší zvonec už nepridávajú.

`djembe2` používa pôvodné nahrávky `djembe_kontakt`: `D/o/x/i` znamená
bas/tón/slap/uzavretý zvuk; `E/p/y` majú 60 % a `F/q/z` 12 % pravdepodobnosť.
Prijíma aj novšie aliasy `d/t/s` a akcenty `D/T/S`.

Mapovanie, zosilnenia, rýchlosti, pôvodné pan pozície a pravdepodobnosti sú
v [afroplayer-samples.yaml](src/main/resources/samples/afroplayer-samples.yaml). Java prehráva mono;
web aplikuje aj pôvodné stereo pan pozície. Celé dozvuky zostávajú zachované.
Ukážka všetkých nástrojov a pôvodný Djole sú v
[afroplayer-instruments.yaml](rhythms/afroplayer-instruments.yaml).
Zdroj a opakovateľný prevod 54 samplov opisuje
[AFROPLAYER.md](src/main/resources/samples/AFROPLAYER.md).

### Pôvodné Freesound nástroje

Nahraté nástroje `DundunInstrument`, `SangbanInstrument` a `KensereniInstrument`
sú v balíku `picosoft.javafro.instruments.sample`.
Každý nástroj má bezparametrový konštruktor a používa reálnu nahrávku pribalenú
v JAR. Dekódovaný sample sa zdieľa medzi inštanciami a pri údere znie celý,
vrátane dozvuku. Nepotrebuje internet ani externý audio prehrávač.

Tieto staršie triedy zostávajú nezmenené. V YAML ich vyber cez `dundun_freesound`,
`sangban_freesound`, `kensereni_freesound`. Syntetické varianty sú naďalej dostupné
cez Java triedy v balíku `picosoft.javafro.instruments.synth`.

Nahrávky sú od JIMMYJAMES112 na Freesound pod licenciou CC0. Sangban je drevený
bubon; dundunba a kenkeni sú podľa autora repliky zo sudov s hovädzou kožou.
Pribalené PCM WAV sú odvodené z verejných HQ MP3 náhľadov (originálne WAV
vyžadujú prihlásenie). Prevod do WAV nezvyšuje kvalitu pôvodného MP3.
Zdroje a spracovanie sú zaznamenané v `src/main/resources/samples/README.md`.

`Rhythm` z balíka `picosoft.javafro.base` spája názov, tracky a tempo rytmu:

```java
Rhythm rhythm = new Rhythm("Djole", tracks, 116);
new BeepSequencer().playPattern(rhythm, 32, 70);
```

Poradie parametrov je `name, tracks, bpm`, voliteľne aj `TimeSignature` ako štvrtý parameter.
Predvolené metrum je 4/4.
Názov nesmie byť prázdny, zoznam musí obsahovať aspoň jeden nenulový track,
tempo musí byť kladné. Rhythm si uloží nemennú kópiu zoznamu trackov.
Počet opakovaní a humanizácia patria k prehrávaniu. `playPattern(rhythm)` prehrá
rytmus raz bez humanizácie; `playPattern(rhythm, 32)` ho prehrá 32-krát bez humanizácie.

Rytmy možno definovať v YAML (ukážka `rhythms/basicRhythms.yaml`):

```yaml
Djole:
  bpm: 116
  timeSignature: "4/4"
  stepsPerBeat: 4
  tracks:
    dundun:    "x.......x.x....."
    sangban:   "....x.......x..."
    kensereni: "..xx..xx..xx..xx"
```

`RhythmLoader.load(Path)` alebo `RhythmLoader.load(InputStream)` v balíku
`picosoft.javafro.io` vráti mapu rytmov podľa identifikátora.
Každý dodávaný súbor obsahuje jeden rytmus, ktorého hlavný kľúč sa presne zhoduje
s názvom súboru bez prípony `.yaml` (vrátane veľkosti písmen).
Názov rytmu je jeho hlavný YAML kľúč; samostatné pole `name` sa nepoužíva.
Z polí definície je povinné iba `tracks`. Vynechané `bpm` prevezme globálne tempo
z `playback.tempo.initial` v `javafro.yaml`. Vynechaný `dialect` použije samé nuly
(jednu pre každý krok na dobu). Explicitne zadané neplatné hodnoty sa odmietnu;
podporované nástroje zahŕňajú `dundun`, `sangban`, `kensereni`/`kenken`, `djembe`,
`djembe2` a ich samostatné zvonce podľa tabuľky vyššie.
`timeSignature` je voliteľné, predvolene `"4/4"`. Podporované sú kladné metrá
s menovateľom 4 alebo 8. Bez `stepsPerBeat` každý znak `x` alebo `.` predstavuje osminu;
`|` oddeľuje takty, nezaberá čas a nespúšťa zvuk. Každý takt musí byť úplný:

- `4/4`: 8 znakov na takt, napr. `"x.......|x.x....."`.
- `8/4`: 16 znakov na takt, napr. `"x.......x.x....."`.
- `12/8`: 12 znakov na takt, napr. `"x..x..x..x.."`.

Pri `stepsPerBeat: 4` predstavuje znak šestnástinu: jeden 4/4 takt má 16 znakov,
ako v príklade Djole vyššie. Dĺžka kroku je vždy `60 / (bpm * stepsPerBeat)` sekúnd.
Pipe iba označuje hranicu taktu a nemení rýchlosť. Rovnakých 16 krokov môže byť
jeden 4/4 takt bez pipe alebo dva 2/4 takty s pipe uprostred; pri rovnakých
BPM a `stepsPerBeat: 4` znejú rovnako dlho. Predvolený Djole má pri 116 BPM
cyklus dlhý približne 2,069 sekundy (štyri beaty).

Každý úsek medzi `|` môže obsahovať aj presne polovicu krokov taktu. Vtedy sa
pri prehrávaní zahrá dvakrát za sebou a vyplní celý takt. Napríklad pri `4/4`
a `stepsPerBeat: 4` je `"d.tt..s."` skrátený zápis `"d.tt..s.d.tt..s."`.
Pravidlo platí aj pre variácie; iné zlomky dĺžky taktu nie sú povolené.

V zložených osminových metrách (6/8, 9/8, 12/8 atď.) BPM označuje bodkovanú
štvrťovú dobu: tri osminy na beat. Inde BPM označuje štvrťovú dobu: dve osminy
na beat. Preto 4/4 aj 12/8 majú pri rovnakom BPM štyri beaty na takt.
Tracky môžu mať rôzny počet celých taktov; kratšie sa cyklia.
Pôvodné volania `playPattern(pattern, ...)` a `playPattern(tracks, ...)` zachovávajú
šestnástinové časovanie; aj v nich je `|` iba oddeľovač bez časového trvania.
Neznáme polia, nástroje, duplicitné kľúče a neplatné hodnoty vyvolajú chybu.
Kompletné načítanie zo zdrojov a prehrávanie je v `AfroJavaPlayer`.

### Variácie a import z AfroPlayer

Track môže zostať reťazec alebo obsahovať základ a zoznam variácií:

```yaml
tracks:
  dundun:
    base: "x.......|x.x....."
    variations:
      - "xx.xxx.x|xx.xx.x."
  sangban: "....x...|....x..."
  kensereni: "..xx..xx|..xx..xx"
```

Bežné prehrávanie používa iba `base`. Alternatívy sú dostupné cez
`track.variations()`; `BeepTrack.withVariation(index)` vytvorí track s vybranou
variáciou (index od nuly), ktorý možno vložiť do nového `Rhythm`.

V `src/main/resources/rhythms/imported` je 23 YAML súborov z 20 pôvodných Ruby
súborov. Kon Masi, Kon Maja a Soli Masi majú samostatné súbory
`Kon_Masi.yaml`, `Kon_Maja.yaml` a `Soli_Masi.yaml`.
Ručné definície Djole a Soko sú spolu v `rhythms/basicRhythms.yaml`
s kľúčmi `Djole` a `Soko`.
Načítanie napríklad:

```java
try (var input = AfroJavaPlayer.class.getResourceAsStream("/rhythms/imported/Djole.yaml")) {
    Rhythm rhythm = RhythmLoader.load(input).get("Djole");
    new BeepSequencer().playPattern(rhythm, 32, 40);
}
```

Aktuálny importér zachováva pôvodné noty basových bubnov, zvonce aj `djembe2`.
Starý `kenken` sa pri importe pomenuje `kensereni`; obidva názvy možno načítať.
Ručne upravené existujúce rytmy sa pri implementácii nástrojov neprepisovali.
Staršie importy môžu stále obsahovať zjednodušené `x`; úplné pôvodné dáta sú
v `legacy.definition`. Načítavač `legacy` akceptuje ako archív; neovplyvňuje prehrávanie.
Nový import zachová otvorené/uzavreté údery a pravdepodobnosti. Nepodporované
symboly zo zdroja ohlási a nahradí tichom, originál ponechá v archíve.

Voliteľné `stepsPerBeat` určuje počet krokov na beat (predvolene 2, pri zloženom
osminovom metre 3). Hodnota 4 umožní šestnástinové delenie. Import ho spolu
s metrom nastavuje tak, aby zachoval pôvodné BPM a časové pozície úderov;
staré označenie metra preto nemusí byť rovnaké ako nové.
Príklady: staré `8/4` používalo osem šestnástin na úsek, teda nové `2/4`
so `stepsPerBeat: 4`; staré `12/8` zostáva `12/8` s tromi krokmi na beat.

Podrobnosti, upozornenia a pôvodné zdrojové súbory sú v
[správe o importe](docs/afroplayer-import/README.md).
Prevod možno zopakovať príkazom (prepíše generované importy a ich archív):

```powershell
node tools/import-afroplayer.cjs C:/Projects/AfroPlayer/src/rhythms
```

Gambiu s pôvodným djembe groove a štyrmi variáciami možno obnoviť samostatne
bez prepísania ostatných ručne upravených rytmov:

```powershell
node tools/import-gambia.cjs C:/Projects/AfroPlayer/src/rhythms/Gambia.rb
```

Používa 4/4 a 4 kroky na dobu. Groove zachováva pôvodné časovanie; údery v sólach
so 6 a 7 znakmi sa zaokrúhľujú na najbližší krok. Počet aj poradie úderov zostávajú
zachované. Dvojice pôvodných dvojdobých úsekov tvoria jeden takt.
Pôvodné symboly djembe `D/o/x` sa prehrávajú priamo; zvonček `b` zostáva v archíve.

## Humanizácia patternov

`BeepSequencer.playPattern` prijíma voliteľný štvrtý parameter `humanize` (celé číslo 0–100),
pre jeden pattern aj zoznam trackov:

```java
new BeepSequencer().playPattern("x...x...x.x.x...", 8, 116, 40);
// Alebo: sequencer.playPattern(tracks, repetitions, bpm, 40);
```

Hodnota `0` zachová presné časovanie a je predvolená pri pôvodnom trojparametrovom volaní.
Každý úder dostane nezávislý náhodný posun dopredu alebo dozadu. Pri `100` je rozsah
predvolene ±30 ms (nastaviteľné cez `playback.humanizeMaxOffsetMs` v aplikácii),
najviac však štvrtina najkratšej medzery medzi pulzmi; nižšie percentá ho úmerne zmenšujú.
Tempo sa nemení ani medzi opakovaniami. Posun pred začiatok nahrávky sa oreže na nulu.
Hlasitosť sa nemení. Hodnoty mimo rozsahu vyvolajú `IllegalArgumentException`.

## Spustiteľný JAR

Na build je potrebný Maven a JDK 25 (`JAVA_HOME` musí ukazovať na JDK 25).

```powershell
mvn package
& "$env:JAVA_HOME/bin/java.exe" -jar target/JavAfro.jar
```

Výsledný `target/JavAfro.jar` možno spustiť bez IntelliJ IDEA.
Na cieľovom počítači musí byť Java 25 alebo novšia; príkaz `java` musí používať túto verziu.
Ak je Java 25 nastavená v `PATH`, stačí `java -jar target/JavAfro.jar`.
Dvojklik na JAR funguje, ak je vo Windows nastavená príslušná asociácia súborov s Javou.

### Obfuskovaná distribúcia

`mvn package` navyše vytvorí **`target/JavAfro-obfuscated.jar`** na distribúciu.
Spustenie vyžaduje Java 25 alebo novšiu:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -jar target/JavAfro-obfuscated.jar
```

ProGuard premenuje aplikačné triedy, metódy a polia a odstráni zdrojové debug
informácie. Vstupná trieda zostáva zachovaná; knižnice RSyntaxTextArea a SnakeYAML
si ponechávajú názvy kvôli reflexii a načítaniu resources. YAML, ikony a zvukové
sample zostávajú čitateľné a nezmenené. Obfuskácia nešifruje reťazce ani dáta.
Odstraňovanie tried a optimalizácie bytecode sú vypnuté.

Pravidlá sú v [proguard.pro](proguard.pro). Použitá verzia ProGuard podporuje
[Java 25](https://www.guardsquare.com/manual/releasenotes).
`target/JavAfro.jar` zostáva vývojovou verziou bez obfuskácie.
Súbor `target/proguard-mapping.txt` uchovaj súkromne ku konkrétnemu vydaniu pre
diagnostiku chýb; nepribaľuj ho k distribúcii. Ďalší build ho prepíše.

Overenie skutočného obfuskovaného JAR (premenené triedy, nezmenené resources,
konfigurácia, načítanie rytmov a samplov, Swing YAML editor a skladanie blokov):

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Djava.awt.headless=true tools/ObfuscationSmokeCheck.java
```

## Windows aplikácia s pribalenou Javou

Na Windows spusti z koreňa projektu:

```powershell
.\build-windows.ps1
```

Skript vykoná `mvn package`, použije obfuskovaný JAR a pomocou nástroja `jpackage` z `JAVA_HOME`
vytvorí `target/windows/JavAfro/JavAfro.exe`.
Potrebné Java moduly určí z výsledného JAR pomocou `jdeps`, vrátane závislostí
knižníc (napríklad `java.logging` pre SnakeYAML). Pred dokončením overí aj
zabalený JAR priamo s pribalenou Javou. Runtime obsahuje `runtime/bin/java.exe`
na diagnostiku prípadných problémov.
Spúšťač otvorí aplikáciu bez konzolového okna.

Na iný Windows počítač s rovnakou architektúrou skopíruj **celý priečinok
`target/windows/JavAfro`**, vrátane podpriečinkov `app` a `runtime`.
Používateľ potom spustí `JavAfro.exe`; nemusí mať nainštalovanú Javu ani IntelliJ IDEA.
Samotný EXE súbor nestačí. Toto je prenosná aplikácia, nie inštalátor.

Podrobnosti k baleniu: [oficiálna dokumentácia jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html).

Opakovaný build nahradí vygenerovaný priečinok aplikácie. Pred buildom ju zatvor.
`mvn clean` odstráni všetky výstupy v `target`, vrátane Windows aplikácie.
