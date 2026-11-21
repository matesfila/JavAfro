# JavAfro – krátky návod

[Obrázkový návod (PDF)](../web/public/obrazovy-navod.pdf) ukazuje, kde spustiť rytmus, upraviť úder a nastaviť hlasitosť. V aplikácii ho otvoríš cez **Súbor → Obrázkový návod**.

## Prehratie rytmu

1. Pri otvorení webovej aplikácie uvidíš zbalené rytmy zo súboru `basicRhythms.yaml`. Šípkou vľavo pri názve rozbaľ napríklad **Ginefare**. Klikni do jeho zápisu a pri prehrávači si skontroluj názov vybraného rytmu.
2. Stlač **Play**. Rytmus sa bude opakovať, kým nestlačíš **Stop**.
3. Pomocou **Tempo** nastav rýchlosť prehrávania. Použi otočný ovládač alebo zadaj číslo priamo.

Ak chceš počas hrania prepnúť na iný rytmus, klikni doň a znova stlač **Play**. Nový rytmus začne po dohraní aktuálneho patternu.

## Editor nôt

Začínaš v režime **Plocha**. Na počítači s myšou alebo touchpadom prepínaš tlačidlom nad editorom medzi **Plocha** a **Noty**. V oboch režimoch môžeš kliknúť do zápisu a písať fyzickou klávesnicou. Na telefóne alebo tablete s dotykovým ovládaním sa režimy striedajú v poradí **Plocha → Noty → Klávesnica → Plocha**.

- V režime **Noty** klikni do riadka nástroja. Pod editorom sa zobrazí jeho názov a tlačidlá s údermi, ktoré vie zahrať. Na djembe skús `B` a `b`. Obe znamenajú basový úder, malé `b` znie tichšie. Bodkou `.` vložíš pauzu.
- Ak chceš na dotykovom zariadení napísať napríklad názov rytmu, v režime **Noty** klepni na tlačidlo **Noty**. Prepneš na **Klávesnica** a otvorí sa systémová klávesnica.
- **Prepisovanie** – keď je zapnuté, nové znaky nahrádzajú znaky pod kurzorom. Keď je vypnuté, vkladajú sa medzi existujúce znaky.

Na telefóne roztiahni dva prsty v editore, ak chceš zväčšiť písmo. Priblížením prstov ho zmenšíš. Dlhý riadok si posuň do strany prstom.

## Uloženie a návrat k práci

- Cez **Súbor → Uložiť ako…** si stiahneš vlastnú kópiu rytmov ako súbor YAML. Keď ju chceš znova otvoriť, vyber **Súbor → Otvoriť**. V ponuke **Súbor** môžeš vytvoriť aj nový rytmus. Cez **Úpravy → Zbaliť všetko** alebo **Rozbaliť všetko** naraz zbalíš alebo rozbalíš všetky rytmy v editore.
- Ak chceš po ďalšom otvorení aplikácie pokračovať tam, kde si skončil, v tom istom prehliadači vyber **Súbor → Obnoviť posledný súbor**. Pri otázke **Uložiť rozpracované zmeny?** vyber **Áno**, ak si chceš pred výmenou súboru stiahnuť kópiu. **Nie** pokračuje bez uloženia a **Zrušiť** ťa vráti k úpravám.
- Ak chceš aplikáciu aktualizovať, zastav prehrávanie a pri pripojení na internet otvor **Súbor → O aplikácii → Aktualizovať aplikáciu**. O výsledku ťa informuje hlásenie. Rozpracovaný rytmus zostane uložený.

## Funkcie pre náročnejších

- **Mixer** nastavuje hlasitosť jednotlivých nástrojov. **Mute** nástroj stíši, **Master** mení celkovú hlasitosť.
- **Humanize** pridáva drobné náhodné odchýlky v časovaní a hlasitosti, aby hra pôsobila prirodzenejšie. **Miera odchýlky** určuje veľkosť odchýlok, **Pravdepodobnosť** určuje, ako často sa použijú.
- **Dialect** mení rozloženie pulzov v dobe a tým charakter rytmu. Záporná hodnota posunie pulz skôr, kladná neskôr. Keď pohneš posuvníkom, novú hodnotu hneď uvidíš aj v zápise rytmu. **Vynulovať** obnoví rovnomerné rozloženie.

Tempo aj pokročilé nastavenia môžeš meniť počas prehrávania. Úpravy zápisu v editore sa použijú po ďalšom stlačení **Play**.

## Inštalácia na počítač alebo telefón

JavAfro sa dá spúšťať a používať priamo z internetového prehliadača (Chrome, Firefox, Safaťi, ...).
Praktickejšie ale je spúšťať JavAfro z vlastnej ikony.
Návod, ako na to: otvor v prehliadači webovú adresu prehrávača (začína sa na `https://`). Keď uvidíš editor rytmov, pokračuj podľa zariadenia.

- **Počítač – Chrome.** Klikni vpravo hore na tri bodky **⋮ → Preniesť, uložiť a zdieľať → Inštalovať stránku ako aplikáciu** a potvrď inštaláciu. JavAfro potom nájdeš medzi aplikáciami v počítači. [Pomocník pre Chrome](https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DDesktop&hl=sk)
- **iPhone – Safari.** Otvor ponuku **Zdieľať** (štvorec so šípkou nahor). Ak ikonu nevidíš, klepni najprv na tri bodky. V ponuke vyber **Pridať na plochu**. Ak sa zobrazí prepínač **Otvoriť ako webovú apku**, zapni ho. Klepni na **Pridať**. Na ploche pribudne ikona JavAfro.
- **Android – Chrome.** Klepni na tri bodky **⋮** vedľa adresy. Vyber **Inštalovať a vytvoriť odkaz → Inštalovať**. V niektorých verziách nájdeš túto voľbu pod **Pridať na plochu**. Potvrď inštaláciu a otvor JavAfro cez jeho ikonu. [Pomocník pre Android](https://support.google.com/chrome/answer/9658361?co=GENIE.Platform%3DAndroid&hl=sk)

Ak chceš hrať aj bez internetu, prvýkrát otvor JavAfro cez novú ikonu ešte počas pripojenia. Nechaj ho stiahnuť rytmy a zvuky na pozadí. Potom môžeš vypnúť pripojenie, znova otvoriť aplikáciu a vyskúšať **Play**.
