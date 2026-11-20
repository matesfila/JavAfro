# Desktop: vzhľad a funkcie ako webová verzia

## Cieľ

Swing aplikácia vyzerá a ovláda sa ako webová verzia: rovnaká paleta, rozloženie do dvoch
stĺpcov, mixer priamo v okne, klávesnica nôt a pomocné PDF v ponuke Súbor.

## Čiastkové úlohy

- [x] FlatLaf Light s paletou z `web/src/style.css` (`SandTheme`), ProGuard pravidlá a natívny prístup
- [x] Ikony tlačidiel kreslené ako webové SVG (`UiIcons`), logo djembe v hlavičke
- [x] Rozloženie: hlavička (Otvoriť, Súbor), karta editora, karta prehrávania so šírkou ~320 px
- [x] Názov súboru s `*` pri neuložených zmenách, názov rytmu pod kurzorom
- [x] Mixer, Dialect a Humanize ako rozbaľovacie sekcie; mixer už nie je samostatné okno
- [x] Dialect: tlačidlo Vynulovať
- [x] Stavový riadok pod editorom pre chyby YAML a „Upravené · stlač Play“
- [x] Klávesnica nôt (prepínač Noty) podľa nástroja pod kurzorom, vrátane balafónu a prepisovania
- [x] Súbor → Dokumentácia, Obrázkový návod (PDF z `web/public`) a nové okno O aplikácii
- [x] Testy: `NoteContextTest`, `NotePadTest`, `DesktopLayoutTest`
- [ ] Ručne overiť na Windows: prehrávanie, mixer počas hrania, PDF, zmenšenie okna na minimum, škálovanie 125 %/150 %

## Hotové, keď

- `mvn test`, `tools/ObfuscationSmokeCheck.java` a `build-windows.ps1` prejdú.
- Spustená aplikácia (JAR aj jpackage) používa FlatLaf, nie predvolený Metal vzhľad.
- Klávesnica nôt ponúka rovnaké noty v rovnakom poradí ako web.

## Poznámky

- Mimo rozsahu: tmavý režim, vyhľadávanie v editore (Ctrl+F), klávesové skratky pre Play/Stop.
- Webové písma DM Sans/Manrope sa nepribaľujú; desktop používa systémové písmo (Segoe UI).
- Obrázkový návod zobrazuje webovú verziu; desktop má teraz zhodné rozloženie, ale bez režimov Plocha/Klávesnica.
