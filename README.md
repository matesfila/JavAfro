# JavAfro

Prehrávač afrických rytmov so Swing GUI, YAML editorom a zvukovými samplami.

## Webová verzia

V adresári [web](web/README.md) je React/TypeScript verzia s YAML editorom,
rovnakými rytmami a samplami, nepretržitým prehrávaním, tempom, humanizáciou
a dialectom. Spustenie: `cd web`, `npm ci`, `npm run dev`.
Nepotrebuje Java backend; produkčný build vytvorí statický adresár `web/dist`.

Na stiahnutie offline verzie na iPhone z vlastného PC spusti v `web` príkaz
`npm run phone`. Podrobný postup lokálneho HTTPS, inštalácie certifikátu a
pridania na plochu je v [návode pre telefóny](web/README.md#telefóny).

## Samostatný PomodoroTimer

PomodoroTimer bol presunutý do `C:/Projects/PomodoroTimer`. Je to nezávislý Maven projekt
s vlastným `pom.xml`, run konfiguráciou, zvukovým alarmom a Windows balením.
V IntelliJ ho otvor ako samostatný projekt. Postup spustenia je v jeho `README.md`.

## Swing prehrávač rytmov

`picosoft.javafro.AfroJavaPlayer` otvorí editor YAML a ovládanie prehrávania:

```powershell
mvn package
& "$env:JAVA_HOME/bin/java.exe" -cp target/JavAfro.jar picosoft.javafro.AfroJavaPlayer "rhythms/djole-samples.yaml"
```

Voliteľný argument je cesta k jednému YAML súboru na disku (UTF-8).
Bez argumentu sa otvorí prázdny editor; predvolený rytmus z resources sa nenačítava.
Relatívna cesta sa vyhodnocuje voči pracovnému priečinku. V okne je aj tlačidlo **Otvoriť YAML…**.
Jeden súbor `.yaml` alebo `.yml` možno tiež presunúť z Prieskumníka priamo do okna
alebo editora. Načíta sa jeho obsah, tempo a dialect. Počas prehrávania treba najprv
stlačiť Stop. Viac súborov, priečinky a iné prípony sa odmietnu; pri neplatnom YAML
zostane doterajší obsah zachovaný. Bežné kopírovanie a vkladanie textu funguje naďalej.
Editor RSyntaxTextArea zvýrazňuje YAML syntax,
zobrazuje čísla riadkov a pri stlačení Tab odsádza dvoma medzerami.
Má veľkú plochu s neproporcionálnym
písmom a posuvníkmi pre dlhšie zápisy. Musí obsahovať práve jeden rytmus.

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

Nástroj `picosoft.javafro.instruments.sample.DjembeInstrument` používa 29 pôvodných
samplov Tomáša Mareka. Funguje v Java aj webovom prehrávači; v knižnici je
`rhythms/djembe-demo.yaml` s ukážkou všetkých úderov aj pôvodného zápisu.

```yaml
djembe-demo:
  name: "Djembe"
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
Ostatné bubny naďalej povoľujú iba `x` a `.`.

Zachovaný je aj pôvodný zápis AfroPlayer: `D/o/x/i`. Znaky `E/p/y` hrajú bas/tón/slap
s pravdepodobnosťou 60 %, `F/q/z` s 12 %; neznamenajú nižšiu hlasitosť.
Každý úder náhodne vyberá jeden multisample. Humanize navyše mení hlasitosť
(pri 100 ±0,07) a ladenie/rýchlosť (±0,5 %); pri 0 sú tieto odchýlky vypnuté.
Výber samplu aj parametre zostávajú nemenné počas celého doznievania úderu.

Zdroje a prevod audio súborov sú zdokumentované v
`src/main/resources/samples/djembe/README.md`. Spoločný manifest `samples/djembe.json`
riadi mapovanie a hlasitosti v oboch prehrávačoch. Pri používaní nástroja nie je
potrebný AfroPlayer, Sonic Pi ani FFmpeg.

### Nahraté zvuky bubnov

Nahraté nástroje `DundunInstrument`, `SangbanInstrument` a `KensereniInstrument`
sú v balíku `picosoft.javafro.instruments.sample`.
Každý nástroj má bezparametrový konštruktor a používa reálnu nahrávku pribalenú
v JAR. Dekódovaný sample sa zdieľa medzi inštanciami a pri údere znie celý,
vrátane dozvuku. Nepotrebuje internet ani externý audio prehrávač.

V YAML použi `dundun`, `sangban` a `kensereni`; tieto identifikátory teraz
načítajú nahraté sample. Syntetické varianty sú naďalej dostupné cez Java triedy
v balíku `picosoft.javafro.instruments.synth`. V GUI otvor
`rhythms/djole-samples.yaml` na vyskúšanie všetkých troch.

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

Rytmy možno definovať v YAML (ukážka `src/main/resources/rhythms/djole.yaml`):

```yaml
Djole:
  name: "Djole"
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
Povinné polia sú `name`, `bpm` a `tracks`;
podporované nástroje sú `dundun`, `sangban` a `kensereni`.
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
Ručné definície Djole a Soko sú v `rhythms/djole.yaml` a `rhythms/soko.yaml`
s kľúčmi `djole` a `soko`.
Načítanie napríklad:

```java
try (var input = AfroJavaPlayer.class.getResourceAsStream("/rhythms/imported/Djole.yaml")) {
    Rhythm rhythm = RhythmLoader.load(input).get("Djole");
    new BeepSequencer().playPattern(rhythm, 32, 40);
}
```

Importované tracky obsahujú tri bubny; starý `kenken` je nový `kensereni`.
Otvorené, zatvorené a pravdepodobnostné údery sú zjednotené na `x`, samostatný
zvonček `b` sa v prehrateľnej časti vynecháva. Pôvodné symboly, ostatné nástroje,
sekvencie, echauff časti, swing a väzby variácií sú bez zjednodušenia uložené
v `legacy.definition`. Načítavač `legacy` akceptuje ako archív; neovplyvňuje prehrávanie.

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

## Humanizácia patternov

`BeepSequencer.playPattern` prijíma voliteľný štvrtý parameter `humanize` (celé číslo 0–100),
pre jeden pattern aj zoznam trackov:

```java
new BeepSequencer().playPattern("x...x...x.x.x...", 8, 116, 40);
// Alebo: sequencer.playPattern(tracks, repetitions, bpm, 40);
```

Hodnota `0` zachová presné časovanie a je predvolená pri pôvodnom trojparametrovom volaní.
Každý úder dostane nezávislý náhodný posun dopredu alebo dozadu. Pri `100` je rozsah
±30 ms, najviac však štvrtina šestnástiny; nižšie hodnoty ho úmerne zmenšujú.
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

## Windows aplikácia s pribalenou Javou

Na Windows spusti z koreňa projektu:

```powershell
.\build-windows.ps1
```

Skript vykoná `mvn package` a pomocou nástroja `jpackage` z `JAVA_HOME`
vytvorí `target/windows/JavAfro/JavAfro.exe`.
Spúšťač otvorí aplikáciu bez konzolového okna.

Na iný Windows počítač s rovnakou architektúrou skopíruj **celý priečinok
`target/windows/JavAfro`**, vrátane podpriečinkov `app` a `runtime`.
Používateľ potom spustí `JavAfro.exe`; nemusí mať nainštalovanú Javu ani IntelliJ IDEA.
Samotný EXE súbor nestačí. Toto je prenosná aplikácia, nie inštalátor.

Podrobnosti k baleniu: [oficiálna dokumentácia jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html).

Opakovaný build nahradí vygenerovaný priečinok aplikácie. Pred buildom ju zatvor.
`mvn clean` odstráni všetky výstupy v `target`, vrátane Windows aplikácie.
