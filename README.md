# Focus Timer (JavAfro)

Pomodoro časovač so Swing GUI a zvukovým upozornením.

## Swing prehrávač rytmov

`picosoft.javafro.AfroJavaPlayer` otvorí editor YAML a ovládanie prehrávania:

```powershell
mvn package
& "$env:JAVA_HOME/bin/java.exe" -cp target/FocusTimer.jar picosoft.javafro.AfroJavaPlayer
```

Voliteľný argument je cesta k YAML súboru. V okne je aj tlačidlo **Otvoriť YAML…**.
Predvolene sa načíta `rhythms/djole.yaml`. Editor RSyntaxTextArea zvýrazňuje YAML syntax,
zobrazuje čísla riadkov a pri stlačení Tab odsádza dvoma medzerami.
Má veľkú plochu s neproporcionálnym
písmom a posuvníkmi pre dlhšie zápisy. Musí obsahovať práve jeden rytmus.

**Play** načíta aktuálny YAML a prehráva základné patterny nepretržite až po **Stop**.
Posuvníky **Tempo** a **Humanize** fungujú aj počas prehrávania bez reštartovania
rytmu. Zmena sa použije od ďalšieho generovaného kroku, s malým oneskorením daným
už pripraveným zvukom. Tempo posuvníka má pri prehrávaní prednosť pred BPM v editore;
pri otvorení súboru sa posuvník inicializuje jeho BPM. Zmeny YAML sa použijú pri
ďalšom Play. Zatvorenie okna zastaví aj zvuk. Existujúci spúšťač FocusTimer zostáva
Pomodoro aplikáciou.

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

## Rhythm

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
  tracks:
    dundun:    "x.......|x.x....."
    sangban:   "....x...|....x..."
    kensereni: "..xx..xx|..xx..xx"
```

`RhythmLoader.load(Path)` alebo `RhythmLoader.load(InputStream)` v balíku
`picosoft.javafro.io` vráti mapu rytmov podľa identifikátora.
Každý dodávaný súbor obsahuje jeden rytmus, ktorého hlavný kľúč sa presne zhoduje
s názvom súboru bez prípony `.yaml` (vrátane veľkosti písmen).
Povinné polia sú `name`, `bpm` a `tracks`;
podporované nástroje sú `dundun`, `sangban` a `kensereni`.
`timeSignature` je voliteľné, predvolene `"4/4"`. Podporované sú kladné metrá
s menovateľom 4 alebo 8. V `Rhythm` každý znak `x` alebo `.` predstavuje osminu;
`|` oddeľuje takty, nezaberá čas a nespúšťa zvuk. Každý takt musí byť úplný:

- `4/4`: 8 znakov na takt, napr. `"x.......|x.x....."`.
- `8/4`: 16 znakov na takt, napr. `"x.......x.x....."`.
- `12/8`: 12 znakov na takt, napr. `"x..x..x..x.."`.

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
& "$env:JAVA_HOME/bin/java.exe" -jar target/FocusTimer.jar
```

Výsledný `target/FocusTimer.jar` možno spustiť bez IntelliJ IDEA.
Na cieľovom počítači musí byť Java 25 alebo novšia; príkaz `java` musí používať túto verziu.
Ak je Java 25 nastavená v `PATH`, stačí `java -jar target/FocusTimer.jar`.
Dvojklik na JAR funguje, ak je vo Windows nastavená príslušná asociácia súborov s Javou.

## Windows aplikácia s pribalenou Javou

Na Windows spusti z koreňa projektu:

```powershell
.\build-windows.ps1
```

Skript vykoná `mvn package` a pomocou nástroja `jpackage` z `JAVA_HOME`
vytvorí `target/windows/FocusTimer/FocusTimer.exe`.
Spúšťač otvorí aplikáciu bez konzolového okna.

Na iný Windows počítač s rovnakou architektúrou skopíruj **celý priečinok
`target/windows/FocusTimer`**, vrátane podpriečinkov `app` a `runtime`.
Používateľ potom spustí `FocusTimer.exe`; nemusí mať nainštalovanú Javu ani IntelliJ IDEA.
Samotný EXE súbor nestačí. Toto je prenosná aplikácia, nie inštalátor.

Podrobnosti k baleniu: [oficiálna dokumentácia jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html).

Opakovaný build nahradí vygenerovaný priečinok aplikácie. Pred buildom ju zatvor.
`mvn clean` odstráni všetky výstupy v `target`, vrátane Windows aplikácie.
