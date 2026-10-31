# Focus Timer (JavAfro)

Pomodoro časovač so Swing GUI a zvukovým upozornením.

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
