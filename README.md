# Focus Timer (JavAfro)

Pomodoro časovač so Swing GUI a zvukovým upozornením.

## Rhythm

`Rhythm` z balíka `picosoft.javafro.base` spája názov, tracky a tempo rytmu:

```java
Rhythm rhythm = new Rhythm("Djole", tracks, 116);
new BeepSequencer().playPattern(rhythm, 32, 70);
```

Poradie parametrov je `name, tracks, bpm`.
Názov nesmie byť prázdny, zoznam musí obsahovať aspoň jeden nenulový track,
tempo musí byť kladné. Rhythm si uloží nemennú kópiu zoznamu trackov.
Počet opakovaní a humanizácia patria k prehrávaniu. `playPattern(rhythm)` prehrá
rytmus raz bez humanizácie; `playPattern(rhythm, 32)` ho prehrá 32-krát bez humanizácie.

Rytmy možno definovať v YAML (ukážka `src/main/resources/rhythms/djole.yaml`):

```yaml
Djole:
  name: "Djole"
  bpm: 116
  tracks:
    dundun:    "x...xx.."
    sangban:   "..x...x."
    kensereni: "x.x.x.x."
```

`RhythmLoader.load(Path)` alebo `RhythmLoader.load(InputStream)` v balíku
`picosoft.javafro.io` vráti mapu rytmov podľa identifikátora (napr. `Djole`).
Jeden súbor môže obsahovať viac rytmov. Povinné polia sú `name`, `bpm` a `tracks`;
podporované nástroje sú `dundun`, `sangban` a `kensereni`.
Neznáme polia, nástroje, duplicitné kľúče a neplatné hodnoty vyvolajú chybu.
Kompletné načítanie zo zdrojov a prehrávanie je v `AfroJavaPlayer`.

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
