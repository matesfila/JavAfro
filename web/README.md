# JavAfro web

Samostatný frontend: React + TypeScript + Vite, CodeMirror 6 pre YAML a Web Audio API
pre zvuk. Na prehrávanie nepotrebuje Java server. Java aplikácia zostáva nezmenená.

## Konfigurácia

Verziu a autorstvo pre okno **O aplikácii** preberá web aj Java z `../pom.xml`.
Pri vydaní aktualizuj hlavné `<version>` v POM a verziu balíka vo
`package.json` aj `package-lock.json`.

Web preberá spoločný [javafro.yaml](../javafro.yaml) pri `npm run dev` alebo
`npm run build`. Používa predvolenú humanizáciu, počiatočné tempo a rozsah jeho
posuvníka, veľkosť písma editora, priečinok knižnice a prvý vybraný rytmus.
Nastavenia `window` platia iba pre Java aplikáciu. BPM a dialect načítaného rytmu
majú prednosť pred počiatočným tempom; humanizácia zostáva globálnym nastavením.
Ak rytmus vynechá `bpm`, použije sa `playback.tempo.initial` z konfigurácie.
Vynechaný `dialect` sa doplní nulami podľa `stepsPerBeat`.
Pre už nainštalovanú offline aplikáciu treba po zmene konfigurácie vytvoriť nový
build a nechať ho aktualizovať. Podrobný popis položiek je v hlavnom README.

## Spustenie

Použi Node.js 20.19+ alebo 22.12+ a npm. Z koreňa JavAfro:

```powershell
cd web
npm ci
npm run dev
```

Otvor http://localhost:5173 a stlač **Play**. Vite vypíše aj sieťovú adresu,
ktorú môže otvoriť telefón na rovnakej Wi-Fi, ak pripojenie povoľuje firewall.
Server zastavíš Ctrl+C.

```powershell
npm test
npm run build
npm run preview
```

Produkčným výstupom je `dist/`: statické HTML, JS, CSS, fonty, YAML a zvuky.
Na neskoršie nasadenie stačí statický hosting s HTTPS. `base: './'` umožňuje
aj hosting v podadresári. `dist/index.html` otváraj cez HTTP server.

Verzia webovej aplikácie má tvar `0.6.2.69`. Prvé tri čísla pochádzajú z `pom.xml`.
Štvrté číslo udáva počet commitov dostupných z práve zostavovaného commitu
(`git rev-list --count HEAD`). Opakované zostavenie toho istého commitu má rovnakú verziu.
Verzia sa vypíše pri zostavení a zobrazí sa v **O aplikácii**. Java používa tri čísla z `pom.xml`.

Na zostavenie potrebuješ Git a klon s úplnou históriou. Ak máš plytký klon,
doplň históriu príkazom `git fetch --unshallow`. GitHub Actions načíta úplnú históriu
pri prevzatí kódu. Pri zostavení na Netlify si aplikácia chýbajúcu históriu stiahne z `origin`.

### Zostavenie s obfuskáciou

```powershell
npm run build:obfuscated
```

Výstup je v rovnakom `dist/`. Lokálny `javascript-obfuscator` obfuskuje aplikačné
JS moduly po minifikácii; knižnice React, CodeMirror a YAML zostávajú minifikované.
Následne sa vypočítajú finálne názvy súborov a vygeneruje offline cache.
Source mapy sa nevytvárajú. Nastavenia sú v `scripts/obfuscate.ts`; náročné
transformácie toku programu a ochrany proti debuggeru sú vypnuté kvôli mobilom.
Obfuskácia sťažuje čítanie JavaScriptu; YAML rytmy a WAV sample zostávajú dostupné.

Pre lokálny prenos tejto verzie na telefón použi `npm run phone:obfuscated`.
Bežné `npm run build`, `npm run phone` a vývoj cez `npm run dev` používajú
štandardné zostavenie. Java JAR a Windows balenie tento webový režim nemení.

## Ovládanie

### Ochrana rozpracovaného dokumentu

Web priebežne ukladá koncept do úložiska tohto prehliadača: celý text (aj prázdny
alebo neplatný YAML), názov súboru, polohu kurzora a rozpracované tempo/dialect
aktuálneho rytmu. Pri ďalšom spustení sa uložený koncept automaticky otvorí pod
pôvodným názvom. Ak koncept chýba, je prázdny alebo obsahuje iba biele znaky,
načíta sa predvolený súbor so zbalenými rytmami.
Koncept sa neposiela na server; patrí konkrétnej adrese a prehliadaču.

Pri nahradení upraveného dokumentu cez **Nový rytmus**, **Otvoriť**,
**Obnoviť posledný súbor** alebo presunutím súboru
sa ponúkne **Zrušiť**, **Uložiť a pokračovať** alebo **Zrušiť zmeny**.
Zrušenie zachová editor aj undo. **Uložiť ako…** umožňuje stiahnuť aj neplatný YAML bez zmeny textu.
Pri platnom dokumente naďalej zohľadňuje tempo a dialect vybraného rytmu.

Ak ukladanie zlyhá (napríklad plné alebo blokované úložisko), zobrazí sa upozornenie.
Pri zistenej zmene konceptu v inom okne sa novšia uložená verzia neprepíše;
prácu z konfliktného okna treba stiahnuť pred jeho obnovením. Prehliadač môže
pri odchode s nestiahnutými zmenami zobraziť aj vlastné varovanie.
Vymazanie dát stránky alebo ukončenie súkromného prehliadania môže koncept
odstrániť. Dôležité rytmy si preto naďalej ukladaj do súborov. Príkaz Uložiť ako…
vyvoláva sťahovanie; aplikácia nevie potvrdiť jeho dokončenie na disku.

Pri otvorení sa priamo načíta `basicRhythms.yaml` (alebo súbor z
`library.initialRhythm`), ak neexistuje uložený koncept. Rozhranie nemá zoznam knižnice. Vždy pracuješ s jedným
otvoreným YAML súborom; ďalší môžeš otvoriť tlačidlom **Otvoriť** alebo presunutím
do okna.

Na telefóne sa hlavná plocha zmestí na jednu obrazovku bez posúvania stránky:
približne polovicu zaberá editor a druhú názov rytmu, **Play**, **Stop** a **Tempo**.
**Humanize**, **Mixer** a **Dialect** sa otvárajú tlačidlami v samostatných dialógoch;
**Súbor** v hlavičke sa otvára ako dialóg, **Editor** v lište editora ako rozbaľovacia ponuka.
Dialóg zatvoríš tlačidlom **Zavrieť**, klávesom Escape alebo kliknutím na pozadie.
Zatvorenie zachová nastavenia aj prehrávanie. Dlhé riadky editora sa na mobile
zalamujú bez zmeny uloženého textu. Posúva sa iba text v editore alebo
obsah dlhého dialógu; jeho tlačidlo Zavrieť zostáva dostupné.
Na šírku sú editor a prehrávač vedľa seba. Rozloženie rešpektuje výrezy displeja
a dostupnú výšku pri otvorení softvérovej klávesnice; vtedy dostane prednosť editor
a hlavné ovládanie prehrávania. Na počítači zostávajú rozšírené ovládače v rozbaľovacích paneloch.

Tempo sa nastavuje okrúhlym ovládačom: potiahnutie hore zrýchľuje, dole spomaľuje
(4 px na 1 BPM, bez skoku pri dotyku). Veľké tlačidlá **−** a **+** menia tempo
presne o 1 BPM; číslo BPM sa dá upraviť aj priamo. Ovládač podporuje šípky
(1 BPM), Page Up/Down (10 BPM) a Home/End (predvolené hranice tempa).

Jeden YAML súbor môže obsahovať viac rytmov pod samostatnými hlavnými kľúčmi.
Po načítaní je vybraný prvý. Play prehrá definíciu pod kurzorom a zostáva dostupné
aj počas hrania. Ďalšie stlačenie zastaví aktuálny rytmus a od začiatku spustí
nový výber vrátane úprav YAML. Stop zruší aj rozpracované načítavanie samplov.
Medzery a komentáre medzi definíciami
patria predchádzajúcemu rytmu. Uloženie zachová celý súbor a zapíše ovládacie
nastavenia iba vybranému rytmu (počas hrania prehrávanému rytmu).

- Predvolený súbor obsahuje viac rytmov; prehrávaný rytmus určuje kurzor v editore.
- Vlastný YAML otvoríš tlačidlom alebo presunutím jedného súboru do okna.
  Súbor sa číta lokálne v prehliadači; nikam sa neodosiela.
- Editor zvýrazňuje YAML a priebežne kontroluje jeho platnosť.
- Editor sa otvorí v režime **Plocha**. V režime **Noty** panel ponúka všetky povolené
  noty nástroja na aktuálnom riadku kurzora vrátane akcentov a pravdepodobnostných
  úderov; funguje aj v jeho `base` a `variations`. Mimo tracku sa noty skryjú.
  Vpravo dole sú šípky v tvare obráteného T (`← ↓ →`, nad `↓` je `↑`), nad `←`
  je medzera `␣`, nad `→` nový riadok `↵` (Enter, zachová odsadenie) a vpravo hore
  Backspace. Dolný rad pokračuje vľavo od `←` oddeľovačom taktov `|` a pred ním pauzou `.`.
  Malé tlačidlo **×** nad Backspace panel zavrie a prepne editor do režimu **Plocha**,
  podobne ako Hotovo na systémovej klávesnici.
  Režim prepína prvá položka ponuky **Editor**, ktorá ukazuje aktuálny režim.
  Pri ovládaní myšou alebo touchpadom prepína medzi **Plocha** a **Noty**.
  Na primárne dotykových zariadeniach cyklicky prepína **Plocha → Noty → Klávesnica → Plocha** a zobrazuje
  aktuálny režim. **Klávesnica** priamo pri kliknutí zameria editor a vyvolá
  systémovú klávesnicu na úpravu ostatného YAML. Keď systémovú klávesnicu skryješ
  (Hotovo, Done alebo späť), aplikácia sa sama prepne do režimu **Plocha**. **Plocha** skryje panel nôt aj
  systémovú klávesnicu; editor využije uvoľnenú výšku nad kompaktným prehrávačom.
  Dotyk editora v režime Plocha systémovú klávesnicu nevyvoláva.
  Prepnutie zachová dokument, výber aj históriu úprav. Posúva sa iba obsah dokumentu.
- Šípky **↑/↓** vpravo v lište editora presunú rytmus pod kurzorom nad predchádzajúci
  alebo pod nasledujúci rytmus (s komentármi pod ním). Kurzor zostane v presunutom rytme,
  zbalené rytmy zostanú zbalené a presun sa vráti jedným krokom späť.
- Vpravo v lište editora je ponuka **Editor**: režim (**Plocha** / **Noty** / **Klávesnica**),
  **Prepisovanie**, oddeľovač, **Rozbaliť všetko** a **Zbaliť všetko**. Zbaľujú sa iba celé
  rytmy, rovnako ako pri otvorení súboru. Režim a Prepisovanie nechajú ponuku otvorenú,
  zbalenie a rozbalenie ju zavrú; zavrie sa aj klávesom Escape alebo kliknutím mimo nej.
  V hlavičke je **Súbor** s tromi skupinami: **Súbory** (**Nový rytmus**, **Otvoriť…**,
  **Obnoviť posledný súbor**, **Uložiť ako…**), **Hranie** (**Živé hranie**) a **Pomoc**
  (**Dokumentácia**, **Obrázkový návod**, **O aplikácii**).
  Názov otvoreného súboru sa nezobrazuje, pri ukladaní zostáva zachovaný.
- Zapnuté **Prepisovanie** (zaškrtnuté v ponuke Editor) prepisuje znaky pod kurzorom, vypnuté vkladá.
  Režim prepína aj kláves **Insert** a tlačidlo Prepisovanie na klávesnici nôt.
  Prepisovanie neodstraňuje
  koniec riadka; na jeho konci sa znaky pridávajú. Označený text sa nahrádza bežne.
- Play opakuje základné patterny nepretržite. Stop zruší aj naplánované údery.
- Opätovné **Play** zaradí zvolený rytmus na prehratie po skončení aktuálneho
  patternu, vrátane všetkých jeho taktov. Na tejto hranici začne nový rytmus
  od začiatku bez pridanej pauzy; doznievajúce údery sa neprerušia.
  Ďalšie **Play** nahradí čakajúcu voľbu a **Stop** ju zruší.
  Ak nové sample ešte nie sú pripravené, pôvodný pattern sa opakuje až do
  najbližšej pripravenej hranice. Chyba YAML alebo načítania ho nezastaví.
  Prvý štart stále závisí od načítania samplov a prebudenia zvuku v prehliadači;
  prirodzené pauzy zapísané v patterne zostávajú zachované.
- Tempo, humanize a nezávislé posuvníky dialectu fungujú za behu. Zmena sa prejaví
  pri ďalšom plánovanom beate; už naplánovaný zvuk má krátky predstih.
- Úpravy zdrojového YAML sa použijú pri ďalšom Play. Pred otvorením iného súboru
  zastav prehrávanie. Uložiť ako… stiahne YAML vrátane aktuálneho BPM a dialectu,
  pričom zachová aj dáta `legacy` a komentáre. Humanize je nastavenie prehrávača.

## Časovanie a formát

Formát zodpovedá Java triede Rhythm: `timeSignature`, `stepsPerBeat`, `dialect`,
tracky `dundun_withBell`, `sangban_withBell`, `kensereni_withBell`, `djembe`, `djembe2`
a samostatné `dundunBell`, `sangbanBell`, `kensereniBell`; string alebo objekt `base`/`variations`.
Znak `x` hrá iba zvonček; `D/d` úder dundunu, `S/s` sangbanu a `K/k` kensereni, vždy iba na príslušnom tracku. `.` je ticho, `|` oddeľuje celé takty bez pridaného času.
Variácie a pôvodné `legacy` dáta zostávajú zachované, automaticky sa nehrajú.

Basové bubny používajú pôvodné multisample AfroPlayer: `D/d`, `S/s`, `K/k` sú otvorené údery príslušného bubna,
`i/I` uzavretý a `x` iba zvonec. Každý úspešný úder bubna pridá aj jeho zvonec.
`A/B/C` majú pravdepodobnosti 12/40/80 % vrátane zvonca. `djembe2` zachováva
zvuky `B/o/S/i`, pravdepodobnostné `E/p/y/F/q/z` a prijíma aj `b/t/s`.
Mapovanie, gain, rate a pan sú v spoločnom `samples/afroplayer-samples.yaml`.
Staršie CC0 nahrávky sú dostupné ako `dundun_freesound`, `sangban_freesound`, `kensereni_freesound`.

Úsek medzi `|` môže mať aj presne polovicu krokov taktu; prehrá sa dvakrát
za sebou, aby vyplnil celý takt. Platí to pre základné patterny aj variácie.
Pri `4/4` a `stepsPerBeat: 4` teda `"b.tt..s."` znie ako `"b.tt..s.b.tt..s."`.

Djembe používa 29 nahrávok Matúša Filu z AfroPlayer, na ktorých hrá Tomáš Marek
na svojom djembe; nahrávky sú spoločné s Java verziou.
`b/t/s` = bas/tón/slap, `B/T/S` = akcenty; `i/I`, `c/C`, `a/A` = tlmený tón,
tlmený slap a alternatívny slap. Bas sa zapisuje `B/b`, pôvodný tón `o` zostáva podporovaný, slap používa `S/s`; podporované sú aj pravdepodobnostné
údery `E/p/y` (60 %) a `F/q/z` (12 %). Humanize ovplyvňuje čas aj jemné zmeny
dynamiky a ladenia. V knižnici vyber **djembe-demo**. Prehrávač načítava iba
sample nástrojov aktuálneho rytmu a dekódované zvuky používa opakovane.

Dialect má jednu hodnotu od −1 do +1 pre každý pulz: 0 je presná poloha,
±1 posúva o polovicu rovného kroku. Posuny aj humanizácia rešpektujú tempo.
Pri 12/8 je predvolený počet pulzov 3 a BPM počíta bodkované štvrťové doby.
Web Audio plánuje údery podľa zvukových hodín; časovač iba dopĺňa plán.

## Zdieľané dáta a licencie

`predev` a `prebuild` spúšťajú `scripts/sync-assets.mjs`, ktorý kopíruje aktuálne
`../rhythms/`, `../src/main/resources/samples/` a `icons/` do ignorovaného
`public/generated/`. Zdroje upravuj v pôvodných adresároch, nie v generovanej kópii.
Knižnica aj zvuky sa teda aktualizujú pri ďalšom spustení/buildovaní.

Staršie nahrávky troch dununov JIMMYJAMES112 / Freesound a ikona djembe / SVG Repo sú CC0.
Nahrávky djembe a basových bubnov osobne vyhotovil Matúš Fila. Na prvom djembe
hral Tomáš Marek na svojom nástroji. Dundun, sangban, kensereni a ich zvonce
nahral aj zahral Matúš Fila na nástrojoch pražskej skupiny Aneboafro, ktorej bol
v tom čase členom. Verejná licencia na ďalšie používanie týchto samplov nie je určená.
Sedem samplov `djembe2` má neznámy pôvod a je z licenčného vyhlásenia vynechaných;
technicky zostávajú pribalené. Podrobnosti sú v `generated/samples/AFROPLAYER.md`.
Detaily prvého djembe sú v
`generated/samples/djembe/README.md`, jeho mapovanie v spoločnom `samples/afroplayer-samples.yaml`.
Podrobné zdroje a úpravy sú v skopírovaných `generated/samples/README.md`
a `generated/icons/README.txt`. Fonty Manrope a DM Sans sú pribalené lokálne
cez Fontsource pod SIL Open Font License (licencie v príslušných npm balíkoch).

## Telefóny

### Stiahnutie z vlastného PC na iPhone

Nepotrebuješ verejný hosting. Na PC spusti:

```powershell
cd C:\Projects\JavAfro\web
npm run phone
```

Príkaz zostaví produkčnú PWA a spustí dva lokálne servery: inštalačnú stránku
na HTTP porte **4180** a prehrávač na HTTPS porte **4181**. iPhone aj PC musia byť
na rovnakej Wi-Fi. V termináli vyber adresu bežného Wi-Fi/LAN adaptéra,
napríklad `http://192.168.1.66:4180/`; virtuálne adaptéry sa na telefóne nepoužívajú.

1. Otvor túto **HTTP inštalačnú adresu v Safari**. Stránka obsahuje celý postup.
2. Stiahni certifikát **JavAfro Local CA**, ktorý vygeneroval tvoj PC.
3. V Nastaveniach iPhone otvor **Profil stiahnutý** (prípadne Všeobecné → VPN
   a správa zariadenia) a profil nainštaluj.
4. Otvor **Všeobecné → Informácie → Nastavenia dôveryhodnosti certifikátov**
   a zapni plnú dôveru pre **JavAfro Local CA**.
5. Vráť sa na inštalačnú stránku a otvor jej odkaz na **HTTPS aplikáciu**.
6. V Safari zvoľ **Zdieľať → Pridať na plochu → Pridať**. Ak sa ponúka prepínač
   otvorenia ako webovej aplikácie, zapni ho.
7. Otvor novú ikonku djembe a nechaj aplikáciu pri zapnutom PC stiahnuť offline dáta na pozadí.
   Odporúčané overenie: zapni na telefóne režim lietadlo, aplikáciu zatvor a znovu
   otvor z ikonky, vyber iný rytmus a stlač Play.

Ručné povolenie dôvery je požiadavka iOS pre vlastný lokálny certifikát:
[postup Apple](https://support.apple.com/en-gb/102390). HTTP adresa počítača
samotná offline PWA neumožňuje; HTTPS nevyžaduje verejný internetový server.
Ak Safari ukazuje varovanie certifikátu, neobchádzaj ho; skontroluj inštaláciu,
plnú dôveru a správnu IP adresu. Odtlačok SHA-256 verejného certifikátu je v termináli
aj na inštalačnej stránke.

Súkromné kľúče a certifikáty zostávajú v ignorovanom **`web/.local/phone/`**.
Server neposkytuje súkromné kľúče a nemení dôveryhodné certifikáty Windows.
Celý tento priečinok nezdieľaj; telefón potrebuje iba verejný `javafro-ca.cer`.
CA platí rok, serverový certifikát 90 dní; serverový certifikát sa pri ďalšom
spustení automaticky obnoví alebo prispôsobí novej IP, s rovnakou CA.
Ak vyprší CA, treba vytvoriť novú sadu a nahradiť profil na iPhone. Profil môžeš
odstrániť v správe zariadenia, keď už lokálny prenos nepotrebuješ.

Script používa OpenSSL z Git for Windows (na tomto PC `C:/usr/Git/usr/bin/openssl.exe`),
prípadne z PATH alebo premennej `JAVAFRO_OPENSSL`. Žiadne certifikáty neposiela
externej službe. Ak Windows zobrazí firewall dialóg, na spojenie z iPhone potrebuješ
povoliť Node pre vlastnú súkromnú sieť. Script firewall automaticky nemení.
Pri guest Wi-Fi s izoláciou klientov sa telefón s PC nemusí spojiť.

### Offline dáta a aktualizácie

Každý produkčný build vytvorí manifest a service worker `dist/sw.js`. Ten uloží
všetky JS/CSS/fonty, ikony, všetky YAML a všetky WAV vrátane 29 djembe samplov.
Offline dáta sa pripravujú automaticky na pozadí bez stavového panelu alebo ponuky Inštalácia.
Neúplná nová verzia nenahradí funkčnú starú verziu.
Úpravy vlastného YAML si naďalej ulož tlačidlom **Uložiť ako…** do Súborov.

Na Netlify použi **Súbor → O aplikácii → Aktualizovať aplikáciu** vedľa čísla verzie. Tlačidlo skontroluje server,
počká na úplné stiahnutie novej verzie a obnoví aplikáciu. Ak už je verzia
pripravená, zobrazí **Nainštalovať novú verziu**. Pred aktualizáciou zastav
prehrávanie. Rozpracovaný dokument, kurzor a uložené nastavenia tempa/dialectu
sa obnovia z konceptu; ak sa koncept nedá bezpečne uložiť, aplikácia sa neobnoví
a zobrazí chybu. Bez novej verzie sa zobrazí potvrdenie, bez pripojenia chyba.
Aktualizácia nemaže koncepty ani všetky offline dáta a neobnovuje ostatné otvorené okná.

Pri lokálnom hostovaní najprv spusti na PC znovu `npm run phone` a otvor
aplikáciu na telefóne pri zapnutom PC. Nová verzia sa sťahuje aj na pozadí.
Bez stlačenia tlačidla sa prehrávanie nepreruší; nová verzia sa použije po
zatvorení všetkých okien JavAfro (vrátane Safari tabu) a opätovnom otvorení.
Staršiu inštaláciu, ktorá tlačidlo ešte nemá, takto jednorazovo obnov.
Pri zmene IP treba na nové
stiahnutie otvoriť novú HTTPS adresu zo servera; pôvodná offline inštalácia tým
nezíska novú adresu automaticky.

Safari/iOS môže dáta aplikácie časom odstrániť, napríklad pri nedostatku miesta
alebo ručnom vymazaní dát. Vtedy treba aplikáciu znovu otvoriť pri zapnutom PC.
Vývojový `npm run dev` service worker neinštaluje. Na PC možno PWA otestovať cez
`npm run build` a `npm run preview` na `localhost`, ktorý prehliadač povoľuje aj bez HTTPS.

Rozhranie má mobilné rozloženie a zvuk sa aktivuje priamo kliknutím na Play,
čo je potrebné aj v Safari na iOS. Ak systém preruší AudioContext, prehrávanie
sa zastaví a zobrazí výzvu na opätovné Play. Spoľahlivé hranie na zamknutom
telefóne alebo na pozadí sa negarantuje.
Testy v prehliadači na počítači nenahrádzajú overenie na fyzickom iPhone.

Automatické testy kontrolujú všetky zdieľané YAML, pravidlá taktu, dialect,
humanizáciu, nepretržité časovanie a zastavenie zvuku počas načítavania/prerušenia.

Tracky `dundun`, `sangban` a `kensereni` hrajú iba bubon: rovnaké údery a pravdepodobnosti ako `*_withBell`, bez automatického zvonca a bez podpory `x`.

Samostatné zvonce `dundunBell`, `sangbanBell`, `kensereniBell` podporujú iba `x` (bežný úder) a `X` (hlasnejší úder, 1,5× hlasitosť).
