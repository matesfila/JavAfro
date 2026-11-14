# TODO



### Otvorené úlohy
- [] Dá sa JavAfro zverejniť cez github pages ??
- [] Prepnutie do režimu systémovej klávesnice musí hneď zobraziť klávesnicu, nie až vtedy keď editor znovuzíska focus
- [] Presúvanie rytmu v yaml - možnosť switchnúť rytmy - aktuálny rytmus presun hore alebo presun dole
- [] Panel na živé hranie - veľké buttony na play, stop, next rhythm, speed up, break...
- [] Podpora MIDI
- [] Pomenované noty - bude jednoduchšie ich zadávať, pre djembe napr bum, ton, slap, closed sl, closed tn
- [] Djembe so sese ???
- [] Prihlasovanie/licencie ???
- [] Medzery v zápise patternu by sa mali ignorovať (umožní to vizuálne si prispôsobiť zápis)
- [] Tlačidlo otvoriť súbor by nemalo byť disabled počas prehrávania, otvoriť súbor a hrať súčasne je štandardná činnosť
- [] Všetky parametre by mali byť nastaviteľné per pattern (humanize, dialect, ...)
  - pattern sa zo String zrejme zmení na triedu Pattern
- [] Desktop verzia: vstupný súbor nebude zakompilovaný, ale normálny externý súbor
- [] Generátor stringov - preskúmať, či existuje nejaká knižnica na generovanie stringov
  - cieľom by bolo mať možnosť v jednom riadku zapísať rôzne varianty patternu
- [] Zaviesť rodinu rytmov (meniť sa im bude zrejme dialekt)
  - názvy rodín by možno mohli byť jednoducho: Djole, Soko, Soli, Kon
  - možno by sa názov rodiny mohol dať nastaviť do parametra dialect, ktorý by tak podporoval aj ručný dialect aj odvodený




### v0.5.3 (current)
- [x] Bug: Skladba sa ulozila aj s dialektom aj ked v nej predtym nebol, navyse v multiriadkovó formate
- [x] Bug: nefungoval tempo potenciometer
- [x] Zjednodusit menu Subor: odstranit text, button na otvorenie predch suboru, pred about pridat separator

### v0.5.2
- [x] Preusporiadat klavesnicu: šipky, noty zobraziť iba pre daný nástroj, nad šipkou vľavo bude bodka, vpravo backspace
- [x] Pridať button na aktualizáciu aplikácie
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
