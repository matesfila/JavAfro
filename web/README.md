# JavAfro web

Samostatný frontend: React + TypeScript + Vite, CodeMirror 6 pre YAML a Web Audio API
pre zvuk. Na prehrávanie nepotrebuje Java server. Java aplikácia zostáva nezmenená.

## Konfigurácia

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

## Ovládanie

Jeden YAML súbor môže obsahovať viac rytmov pod samostatnými hlavnými kľúčmi.
Po načítaní je vybraný prvý. Play prehrá definíciu pod kurzorom a zostáva dostupné
aj počas hrania. Ďalšie stlačenie zastaví aktuálny rytmus a od začiatku spustí
nový výber vrátane úprav YAML. Stop zruší aj rozpracované načítavanie samplov.
Medzery a komentáre medzi definíciami
patria predchádzajúcemu rytmu. Uloženie zachová celý súbor a zapíše ovládacie
nastavenia iba vybranému rytmu (počas hrania prehrávanému rytmu).

- Knižnica obsahuje všetky rytmy z koreňového `rhythms/` vrátane importovaných dát.
- Vlastný YAML otvoríš tlačidlom alebo presunutím jedného súboru do okna.
  Súbor sa číta lokálne v prehliadači; nikam sa neodosiela.
- Editor zvýrazňuje YAML a priebežne kontroluje jeho platnosť.
- Play opakuje základné patterny nepretržite. Stop zruší aj naplánované údery.
- Tempo, humanize a nezávislé posuvníky dialectu fungujú za behu. Zmena sa prejaví
  pri ďalšom plánovanom beate; už naplánovaný zvuk má krátky predstih.
- Úpravy zdrojového YAML sa použijú pri ďalšom Play. Pred otvorením iného súboru
  zastav prehrávanie. Uložiť stiahne YAML vrátane aktuálneho BPM a dialectu,
  pričom zachová aj dáta `legacy` a komentáre. Humanize je nastavenie prehrávača.

## Časovanie a formát

Formát zodpovedá Java triede Rhythm: `timeSignature`, `stepsPerBeat`, `dialect`,
tracky `dundun`, `sangban`, `kensereni`, `djembe`, string alebo objekt `base`/`variations`.
Znak `x` hrá úder, `.` ticho, `|` oddeľuje celé takty bez pridaného času.
Variácie a pôvodné `legacy` dáta zostávajú zachované, automaticky sa nehrajú.

Úsek medzi `|` môže mať aj presne polovicu krokov taktu; prehrá sa dvakrát
za sebou, aby vyplnil celý takt. Platí to pre základné patterny aj variácie.
Pri `4/4` a `stepsPerBeat: 4` teda `"d.tt..s."` znie ako `"d.tt..s.d.tt..s."`.

Djembe používa 29 samplov Tomáša Mareka z AfroPlayer, spoločných s Java verziou.
`d/t/s` = bas/tón/slap, `D/T/S` = akcenty; `i/I`, `c/C`, `a/A` = tlmený tón,
tlmený slap a alternatívny slap. Funguje aj pôvodný zápis `D/o/x/i` a pravdepodobnostné
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

Nahrávky troch dununov JIMMYJAMES112 / Freesound a ikona djembe / SVG Repo sú CC0.
Djembe má samostatný pôvod a podmienky zo starého AfroPlayer; detaily sú v
`generated/samples/djembe/README.md`, jeho mapovanie v spoločnom `samples/djembe.yaml`.
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
7. Otvor novú ikonku djembe a počkaj na **Pripravené offline**. Až potom vypni PC.
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
Stav **Pripravené offline** sa zobrazí až po overení úplnej cache. Pri zlyhaní
je dostupné opakovanie; neúplná nová verzia nenahradí funkčnú starú verziu.
Úpravy vlastného YAML si naďalej ulož tlačidlom **Uložiť** do Súborov.

Na aktualizáciu spusti na PC znovu `npm run phone` a otvor aplikáciu na telefóne
pri zapnutom PC. Nová verzia sa stiahne na pozadí a zobrazí sa upozornenie.
Prehrávanie sa nepreruší; nová verzia sa použije po zatvorení všetkých okien
JavAfro (vrátane Safari tabu) a opätovnom otvorení. Pri zmene IP treba na nové
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
