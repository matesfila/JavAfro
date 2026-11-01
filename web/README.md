# JavAfro web

Samostatný frontend: React + TypeScript + Vite, CodeMirror 6 pre YAML a Web Audio API
pre zvuk. Na prehrávanie nepotrebuje Java server. Java aplikácia zostáva nezmenená.

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

Djembe používa 29 samplov Tomáša Mareka z AfroPlayer, spoločných s Java verziou.
`d/t/s` = bas/tón/slap, `D/T/S` = akcenty; `i/I`, `c/C`, `a/A` = tlmený tón,
tlmený slap a alternatívny slap. Funguje aj pôvodný zápis `D/o/x/i` a pravdepodobnostné
údery `E/p/y` (60 %) a `F/q/z` (12 %). Humanize ovplyvňuje čas aj jemné zmeny
dynamiky a ladenia. V knižnici vyber **Djembe – ukážka**. Prehrávač načítava iba
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
`generated/samples/djembe/README.md`, jeho mapovanie v spoločnom `samples/djembe.json`.
Podrobné zdroje a úpravy sú v skopírovaných `generated/samples/README.md`
a `generated/icons/README.txt`. Fonty Manrope a DM Sans sú pribalené lokálne
cez Fontsource pod SIL Open Font License (licencie v príslušných npm balíkoch).

## Telefóny

Rozhranie má mobilné rozloženie a zvuk sa aktivuje priamo kliknutím na Play,
čo je potrebné aj v Safari na iOS. Ak systém preruší AudioContext, prehrávanie
sa zastaví a zobrazí výzvu na opätovné Play. Spoľahlivé hranie na zamknutom
telefóne alebo na pozadí sa negarantuje. Táto verzia ešte nemá PWA ani offline cache.
Testy v prehliadači na počítači nenahrádzajú overenie na fyzickom iPhone.

Automatické testy kontrolujú všetky zdieľané YAML, pravidlá taktu, dialect,
humanizáciu, nepretržité časovanie a zastavenie zvuku počas načítavania/prerušenia.
