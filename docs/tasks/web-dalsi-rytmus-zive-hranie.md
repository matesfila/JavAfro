# Web: Next / predchádzajúci rytmus a Živé hranie

## Cieľ

Počas hrania sa dá jedným tlačidlom prepnúť na ďalší rytmus v súbore (Next) a dvojťukom
na predchádzajúci. Na živé hranie so skupinou je celoobrazovkový pohľad iba s dvoma veľkými
tlačidlami Play/Next a Stop a s poradím rytmov `predchádzajúci ← aktuálny → nasledujúci`.

## Čiastkové úlohy

- [x] Play sa počas hrania zmení na Next (ikona dvojitej šípky), Next zaradí nasledujúci rytmus na koniec patternu
- [x] Dvojklik / dvojťuk (do 350 ms) zaradí predchádzajúci rytmus
- [x] Poradie dookola: po poslednom prvý, pred prvým posledný
- [x] Kurzor editora sa presunie na zaradený rytmus
- [x] Inteligentné prepnutie: po úprave textu alebo presune kurzora na iný rytmus sa tlačidlo vráti na Play
- [x] Pohľad **Živé hranie** (Súbor → Živé hranie) s veľkými tlačidlami, info riadkom a tlačidlom ×
- [x] V Živom hraní sa po Stop druhé tlačidlo zmení na Next: vyberie ďalší rytmus bez prehrania (dvojťuk predchádzajúci), Play ho spustí
- [x] Testy (`rhythm-navigation.test.ts`, `App.test.ts`) a dokumentácia
- [ ] Ručné overenie na telefóne (dvojťuk na iPhone, portrait/landscape)

## Hotové, keď

- Počas hrania tlačidlo ukazuje Next a jeho stlačenie prepne na ďalší rytmus na konci patternu.
- Dvojťuk prepne na predchádzajúci rytmus aj na iPhone, bez zoomu stránky.
- Živé hranie sa dá otvoriť zo Súbor, ovládať veľkými tlačidlami a zavrieť × alebo Esc bez zastavenia hudby.

## Poznámky

Iba web. Desktopová verzia (Swing) to zatiaľ nemá, je to samostatná úloha v TODO.
Súvisiace súbory: `web/src/App.tsx`, `web/src/LiveView.tsx`, `web/src/rhythm-navigation.ts`,
`web/src/icons.tsx`, `web/src/Editor.tsx` (`setCaret()`), `web/src/style.css`.
Neskôr možno do Živého hrania doplniť tempo, break alebo Wake Lock (aby telefón nezhasol).
