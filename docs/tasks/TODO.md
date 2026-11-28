# TODO

## Úlohy a nápady

### Otvorené úlohy

- [x] Klik na pattern:
  - nastaví kurzor na začiatok taktu
  - otvorí hudobnú klávesnicu
  - atomaticky zapne prepisovací mód
- [x] sys keyb vyvolat iba buttonom v internej keyb ??
  - tlačidlo Sys. keyb. nad Prepisovaním; režim Klávesnica ostáva aj v ponuke Editor
- [x] chybová správa hore v hlavičke vedľa JavAfro, bez zmeny rozloženia (web aj desktop)
- [] sys keyb moznost zavret krizikom vpravo hore
- [] Dvojklik/dvojťuk na pattern má selektnúť daný pattern + dedikovaný editor pre pattern ???

- [] Spoločný repozitár pre dokumenty: ak sa striedajú devices, aby mali vždy aktuálny zdroj
- [] lockovanie parametrov (tempo, dialect... co sa da nastavit per pattern aj globalne)
- [] lock rytmu: ak sa zapne, nebude sa zobrazivat klavesnica a ani sa nijako nezmeni zdrojovy kod
- [] live view moznost zmeny tempa swipe up/down,
- [] bug: zase tempo dial, nerefreshuje sa info, refreshne sa az po kliknuti do editora
- [x] zrusit auto zobrazovanie klavesnice pri toceni tempom, aktivovat iba dvojklikom
- [] auto zap/vyp overwrite mod podla polohy kurzora
- [] pridat novy rytmus do aktualneho dokumentu
- [] dialog novy rytmus s uvodnymi parametrami
- [] pridat moznost zapnut africky ambient do pozadia
- [] Zoznam stôp dať ako list, nie mapu. Umožniť tak dať ten istý nástroj viackrát.
- [] Alternatívne názvy pre nástroje, povoliť aj menej presné názvy, využiť nejaký word distance algoritmus ?
  - cieľom je možnosť mať kenken, kensereni, kensedeni, kenken bell, kenken_withBell, kenken with bell, 
- [] Pomenované noty - bude jednoduchšie ich zadávať, pre djembe napr bum, ton, slap, closed sl, closed tn
- [] Dununba dialekty, dialekt má byť medzi druhý a tretím pulzom, ale má byť aj medzi prvým a druhým ?? Na djembe asi ano, ale na dunoch ??
- [] Podpora MIDI
- [] Djembe so sese ???
- [] Prihlasovanie/licencie ???
- [] Tlačidlo otvoriť súbor by nemalo byť disabled počas prehrávania, otvoriť súbor a hrať súčasne je štandardná činnosť
- [] Všetky parametre by mali byť nastaviteľné per pattern (humanize, dialect, ...)
  - pattern sa zo String zrejme zmení na triedu Pattern
- [] Desktop verzia: vstupný súbor nebude zakompilovaný, ale normálny externý súbor
- [] Generátor stringov - preskúmať, či existuje nejaká knižnica na generovanie stringov
  - cieľom by bolo mať možnosť v jednom riadku zapísať rôzne varianty patternu
- [] Zaviesť rodinu rytmov (meniť sa im bude zrejme dialekt)
  - názvy rodín by možno mohli byť jednoducho: Djole, Soko, Soli, Kon
  - možno by sa názov rodiny mohol dať nastaviť do parametra dialect, ktorý by tak podporoval aj ručný dialect aj odvodený
- [] Java CI workflow - GitHub Actions spúšťa iba webové testy, Java testy (`mvn test`) sa automaticky nespúšťajú
  - pridať `.github/workflows/java.yml` (JDK 25 temurin, `mvn -B test`)
  - najprv lokálne overiť, či Swing UI testy prejdú bez obrazovky (`-Djava.awt.headless=true`), inak použiť `xvfb-run`

### Dokumentácia TODO
- Prepisovanie button vedla backspacu v Sheets klávesnici
- Medzery v patternoch

### Nápady, inšpirácie

#### Offline verzia aplikácie
Offline verzia aplikácie môže byť výhoda oproti ostatným "appkám" pre telefóny. Človek bude mať svoje rytmy u seba,
nebude ich posielať nikam na žiadny server, bude zaručené že to je iba u nich.

## Changelist

### v0.7.0
- [x] stlacenie Done na sys keyb ju deaktuvuje aj v app
- [?] bug: swipe left/right nefunguje na stop buttone v live view
- [?] bug: slidre v mixpulte nejdu az do konca
- [x] Nezávislé stopy pre duny a zvonce
- [x] Balafon
- [x] Nové rozloženie klávesnice Noty: namiesto bodky medzera, namiesto `|` Enter; `|` a `.` v dolnom rade vľavo od šípky doľava
- [x] [Desktop: vzhľad a funkcie ako webová verzia](desktop-vzhlad-ako-web.md) – FlatLaf, dve karty, mixer v okne, klávesnica nôt, PDF návody (zostáva ručné overenie)
- [x] [Web: Next / predchádzajúci rytmus a Živé hranie](web-dalsi-rytmus-zive-hranie.md) – Play sa počas hrania mení na Next, dvojťuk = predchádzajúci, tlačidlo Live! v hlavičke
- [x] Doladiť fonty: farbu, typ fontu, vzdialenosti riadkov/písmen, ...
- [x] Presúvanie rytmu v yaml - možnosť switchnúť rytmy - aktuálny rytmus presun hore alebo presun dole
- [x] Pridať klávesu enter pod backspace, pridať aj medzerník
- [x] Upraviť font editora (farba, spacing)
- [x] Odklik sóla má unmutnuť ostatné tracky
  (zostáva ručné overenie na telefóne)
- [x] Panel na živé hranie - veľké buttony na play, stop, next rhythm, speed up, break...
  - web: play/next/predchádzajúci a stop hotové ([Živé hranie](web-dalsi-rytmus-zive-hranie.md)), zostáva speed up, break...
- [x] Web: malé Play sa počas hrania nemení na Next (ostáva Play, rytmy sa prepínajú potiahnutím)
- [x] Desktop: tlačidlo Next/predchádzajúci (dvojklik) a pohľad Živé hranie ako vo webe
- [x] Medzery v zápise patternu by sa mali ignorovať (umožní to vizuálne si prispôsobiť zápis)

### v0.6.2 (current)
- [x] Aktualizovať rytmy, nedokončené odstrániť z basicRhythms.```yaml
- [x] Fix tempo potenciometra
- [x] Info hláška o dokončenej aktualizácii
- [x] Preusporiadanie nôt na klávesnici Noty - najpoužívanejšie nech sú ako prvé
- [x] Dokumentácia v aplikácii - textová aj obrázková
- [x] Štvrté číslo verzie je git commit number, nie umelo počítaný build number
- [x] Zoomovanie textu v editore by malo zväčšovať/zmenšovať font
- [x] Button Prepisovanie bude aj v custom klávesnici Noty vedla backspace
- [x] Vytvoriť stable release a netlify presmerovať na ňu

### v0.5.3
- [x] Dá sa JavAfro zverejniť cez github pages ??
- [x] Prepnutie do režimu systémovej klávesnice musí hneď zobraziť klávesnicu, nie až vtedy keď editor znovuzíska focus
- [x] Bug: Skladba sa ulozila aj s dialektom aj ked v nej predtym nebol, navyse v multiriadkovó formate
- [x] Bug: nefungoval tempo potenciometer
- [x] Zjednodusit menu Subor: odstranit text, button na otvorenie predch suboru, pred about pridat separator

### v0.5.2
- [x] Preusporiadat klavesnicu: šipky, noty zobraziť iba pre daný nástroj, nad šipkou vľavo bude bodka, vpravo backspace
- [x] Pridať button na aktualizáciu aplikácie (do About dialogu vedla čísla verzie)
- [x] Verzionovanie, changelist

### v0.5.1
- [x] Doplniť do about info o pôvode samplov
- [x] Zobraziť meno aktuálne otvoreného súboru

### v0.5.0
- [x] Swing: tempo ovládač ±1 BPM a číselný vstup, väčšie úchyty, ukladanie a obnova konceptu, ochrana dokumentu a export aj neplatného YAML
- [x] Play zaradí nový rytmus na koniec aktuálneho patternu bez pridanej pauzy; pôvodné údery prirodzene doznejú (posluch na telefóne zostáva na overenie)
- [x] Zlepšiť editovatelnosť zápisu na PC aj mobile
- [x] Web: cyklické režimy Noty / Klávesnica / Plocha so zobrazením systémovej klávesnice a využitím uvoľnenej výšky editora
- [x] Nastavenie tempa otočným ovládačom, s jemným ťahaním a tlačidlami ±1 BPM
- [x] Zväčšiť úchyty všetkých posuvníkov na mobile a dotykovú plochu na 44 px
- [x] Na mobile tak aby sa všetko zmestihol na plochu, bez nutnosti scrollovania
- [x] Zmeniť zápis nôt: x bude zvonček, B bas na djembe, D/S/K dun/san/ken

### v0.1.0

- [x] Custom klávesy na písanie rytmus, zrušiť ios klávesnicu na telefóne nech sa nezobrazuje
- [x] Web: automatické uloženie a obnova konceptu, ochrana pred nahradením, záloha predchádzajúceho dokumentu a export neplatného YAML (fyzické mobilné skúšky zostávajú otvorené)
- [x] Aktívny riadok tmavší - teraz je dôležitý, rytmus pod ním sa prehráva
- [x] Globálny konfiguračný súbor (tam by malo byť defaultné humanize, možno defaultné dialekty pre dané rodiny rytmov...)
- [x] Zjednodušiť mobilné rozhranie
- [x] Ovládacie prvky oddeliť do podpanelov
- [x] Prehodnotiť zápis timeSignature a oddelovača taktov |
- [x] Vytvoriť Java aplikáciu so Swing rozhraním na prehrávanie rytmov.
- [x] Zaviesť YAML formát rytmov s tempom, metrom, nástrojmi a kontrolou platnosti.
- [x] Podporiť rôzne metrá a delenie doby vrátane 12/8 a šestnástinových patternov.
- [x] Pridať YAML editor so zvýraznením syntaxe a otváraním súborov aj presunutím do okna.
- [x] Prehrávať viac nástrojov súčasne a opakovať základné patterny až do zastavenia.
- [x] Umožniť meniť tempo a humanizáciu počas prehrávania.
- [x] Pridať nezávislé posuvníky dialectu pre posun jednotlivých pulzov.
- [x] Použiť reálne zvukové sample pre dundun, sangban a kensereni.
- [x] Pridať djembe s 29 samplami, rôznymi údermi, akcentmi a pravdepodobnostnými údermi.
- [x] Importovať rytmy z AfroPlayer a zachovať pôvodné dáta aj definície variácií.
- [x] Pripraviť ukážkové rytmy vrátane ukážky djembe úderov.
- [x] Vytvoriť spustiteľný JAR a Windows balenie s pribalenou Javou.
- [x] Vytvoriť samostatnú webovú verziu v Reacte a TypeScripte bez Java backendu.
- [x] Vo webovej verzii pridať knižnicu rytmov, YAML editor a otváranie aj ukladanie YAML súborov.
- [x] Zdieľať rytmy a zvukové sample medzi Java a webovou verziou.
- [x] Prispôsobiť webové rozhranie mobilom.
- [x] Pridať offline PWA s ukladaním rytmov a zvukov a oznamovaním novej verzie.
- [x] Pripraviť lokálny HTTPS server a návod na inštaláciu offline aplikácie na iPhone.
- [x] Doplniť automatické testy webového načítania rytmov, časovania, zvuku a offline cache.

### Rejected

- [] Po aktualizovaní verzie nezatvárať About dialóg
  - rejected: toto asi nemá zmysel, lebo zákonite sa musí appka znovu otvoriť ako celok po update, takže do aboutu by sa muselo preklikať od začiatku...
