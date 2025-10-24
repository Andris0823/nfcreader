# CAEN qLOG RT0013 és Hőmérséklet Szenzor Tesztelési Útmutató

## Gyors Áttekintés

Ez az útmutató segít a **CAEN qLOG RT0013** hőmérséklet és páratartalom szenzor, valamint általános NFC hőmérséklet szenzor funkciók tesztelésében.

## Előfeltételek

### Hardver
- ✅ Android eszköz NFC támogatással (API 24+)
- ✅ **CAEN RFID qLOG RT0013** (elsődleges, ajánlott) - hőmérséklet ÉS páratartalom
- ✅ Mifare Ultralight NTAG21x T variáns NFC tag (fallback) - csak hőmérséklet
  - Alternatíva: NTAG213 TT, NTAG215 TT, vagy NTAG216 TT

### Szoftver
- ✅ Android Studio telepítve
- ✅ NFC Reader alkalmazás forráskódja
- ✅ NFC bekapcsolva az Android eszközön

## NFC Tag Beszerzés

### CAEN qLOG RT0013 (Elsődleges, Ajánlott)

1. **Hivatalos Viszonteladók**:
   - [CAEN RFID hivatalos oldal](https://www.caenrfid.com/)
   - Európai viszonteladók
   - Helyi RFID/NFC szaküzletek

2. **Online Platformok**:
   - Specializált RFID áruházak
   - Ipari automatizálási kereskedők

3. **Keresési Kulcsszavak**:
   - "CAEN qLOG RT0013"
   - "CAEN qLOGhumidity"
   - "NFC temperature humidity logger"
   - "ISO15693 temperature sensor"

4. **Ár**: 
   - Általában 15-30 EUR/darab
   - Professzionális minőség, pontos szenzorok

### NTAG21x T (Fallback Opció)

1. **Online Áruházak**:
   - Amazon
   - eBay  
   - AliExpress (olcsóbb, de lassabb szállítás)

2. **Elektronikai Alkatrész Boltok**:
   - Mouser Electronics
   - DigiKey
   - Farnell

3. **Keresési Kulcsszavak**:
   - "NTAG213 TT"
   - "NTAG215 temperature tag"
   - "NFC temperature sensor tag"

4. **Ár**: 
   - Általában 1-5 EUR/darab
   - Nagyobb mennyiségben olcsóbb

## Tesztelési Lépések

### 1. Alkalmazás Telepítés

```bash
# Android Studio-ban:
1. Nyisd meg a projektet
2. Csatlakoztasd az Android eszközt USB-n keresztül
3. Engedélyezd az USB debuggingot az eszközön
4. Kattints a "Run" (▶️) gombra
5. Várd meg a telepítést
```

### 2. NFC Engedélyek Ellenőrzése

```bash
# Eszközön:
Beállítások → Kapcsolatok → NFC és fizetés → NFC BE
```

### 3. Alapvető Tag Olvasás

1. **Indítsd el az alkalmazást**
2. **Tartsd a tag-et a telefon hátlapjához**
   - Az NFC antenna általában a telefon hátlapjának felső részén van
   - Próbálj különböző pozíciókat
3. **Várj az értesítésre**: "NFC tag észlelve!"

### 4. Hőmérséklet Ellenőrzés

Ha a tag támogatja a hőmérséklet szenzort:

✅ **Sikeres olvasás**:
```
Tag ID: 04A1B2C3D4E5F6
Adat (HEX): ...
Adat (UTF-8): ...
Hőmérséklet: 🌡️ 23.75 °C  ← Ez jelenik meg
```

❌ **Nem támogatott tag**:
```
Tag ID: 04A1B2C3D4E5F6
Adat (HEX): ...
Adat (UTF-8): ...
(Hőmérséklet nem jelenik meg)
```

### 5. Hőmérséklet Változás Tesztelése

#### Módszer 1: Kézi Melegítés
1. Olvasd be a tag-et (jegyzd fel a hőmérsékletet)
2. Fogd a tag-et az ujjaid között 30-60 másodpercig
3. Olvasd be újra
4. A hőmérsékletnek növekednie kell (~2-5°C)

#### Módszer 2: Hűtés
1. Olvasd be a tag-et (jegyzd fel a hőmérsékletet)
2. Tedd a tag-et hideg felületre (pl. fém asztal)
3. Várj 1-2 percet
4. Olvasd be újra
5. A hőmérsékletnek csökkennie kell

#### Módszer 3: Pontos Teszt (Speciális Felszerelés)
1. Használj hőmérőt a környezeti hőmérséklet mérésére
2. Olvasd be a tag-et
3. Hasonlítsd össze az értékeket (±2°C eltérés normális)

## Várt Eredmények

### Szobahőmérséklet (~20-25°C)
```
Olvasás 1: 22.50 °C
Olvasás 2: 22.56 °C (kis eltérés normális)
Olvasás 3: 22.44 °C
```

### Melegítés után
```
Kiindulás: 22.50 °C
Ujjal melegítve (30s): 27.75 °C
Stabilizálódás után (2 perc): 23.00 °C
```

## Hibaelhárítás

### Probléma: "NFC tag észlelve!" de nincs hőmérséklet

**Ok**: A tag nem támogatja a hőmérséklet szenzort

**Megoldás**: 
- Ellenőrizd, hogy NTAG21x T variánst használsz-e
- Nézd meg a tag specifikációját
- Próbálj másik tag-et

### Probléma: Alkalmazás nem észleli a tag-et

**Ok**: NFC nincs bekapcsolva vagy rossz pozíció

**Megoldás**:
- Kapcsold be az NFC-t a beállításokban
- Próbálj különböző pozíciókat a telefon hátlapján
- Mozgasd lassan a tag-et
- Tartsd 1-2 cm távolságban

### Probléma: Hőmérséklet érték nem változik

**Ok**: A tag késleltetése vagy túl kicsi hőmérséklet változás

**Megoldás**:
- Várj 1-2 másodpercet az újbóli olvasás előtt
- Használj nagyobb hőmérséklet különbséget (>5°C)
- Ellenőrizd, hogy a tag képes-e gyorsan reagálni

### Probléma: Alkalmazás összeomlik olvasáskor

**Ellenőrzés**:
```bash
# Nézd meg a logcat kimeneteket Android Studio-ban:
View → Tool Windows → Logcat

# Szűrj az "NFCReader" tag-re
# Keress "Error" vagy "Exception" bejegyzéseket
```

## Logok Ellenőrzése

```bash
# Android Studio Logcat-ben szűrj:
Tag: NFCReader

# Sikeres hőmérséklet olvasás:
D/NFCReader: Temperature read: 23.75 °C

# Nem támogatott tag:
W/NFCReader: Could not read temperature - tag may not support temperature sensor

# Olvasási hiba:
E/NFCReader: Error reading temperature
```

## Dokumentálás Szakdolgozathoz

### Készíts Képernyőképeket

1. **Üres állapot** (nincs tag beolvasva)
2. **Tag olvasás hőmérséklet nélkül** (normál tag)
3. **Tag olvasás hőmérséklettel** (NTAG21x T)
4. **Több beolvasás** (hőmérséklet változás látható)

### Rögzíts Adatokat

Készíts táblázatot a mérésekről:

| Időpont | Tag ID | Környezeti Hőm. | Olvasott Hőm. | Eltérés |
|---------|--------|-----------------|---------------|---------|
| 10:00   | 04... | 22.0°C         | 22.5°C        | +0.5°C  |
| 10:05   | 04... | 22.0°C         | 27.2°C        | +5.2°C  |
| 10:10   | 04... | 22.0°C         | 23.1°C        | +1.1°C  |

### Videó Készítése (Opcionális)

1. **Képernyő felvétel az eszközön**:
   - Android beépített képernyő rögzítő
   - Mutasd meg a teljes folyamatot: tag közelébe tartás → adat megjelenítés

2. **Szerkesztés**:
   - Kiemelheted a hőmérséklet értéket
   - Gyorsíthatod a várakozási időt

## Teljesítmény Mérések

### Olvasási Idő
- Tag felismerés: ~100-300ms
- Adatok olvasása: ~200-500ms
- Hőmérséklet olvasása: +50-100ms
- **Teljes**: ~500-900ms

### Megbízhatóság
- Sikeres olvasási arány: >95%
- Hőmérséklet pontosság: ±2°C

## Checklist Szakdolgozathoz

- [ ] NTAG21x T tag beszerzése
- [ ] Alkalmazás telepítése
- [ ] Alapvető tag olvasás tesztelése
- [ ] Hőmérséklet olvasás ellenőrzése
- [ ] Hőmérséklet változás tesztelése
- [ ] Képernyőképek készítése
- [ ] Mérési adatok táblázat létrehozása
- [ ] Logok dokumentálása
- [ ] Eredmények elemzése
- [ ] Videó készítése (opcionális)

## Kapcsolódó Dokumentumok

- `README.md` - Általános projektdokumentáció
- `TEMPERATURE_SENSOR_FEATURE.md` - Részletes technikai leírás
- `ARCHITECTURE.md` - Architektúra dokumentáció

## Segítség

Ha problémába ütközöl:
1. Nézd meg a `TEMPERATURE_SENSOR_FEATURE.md` hibakezelési szakaszát
2. Ellenőrizd a logokat Android Studio-ban
3. Győződj meg róla, hogy a megfelelő tag-et használod

---

**Készítette**: GitHub Copilot
**Utolsó frissítés**: 2025-10-22
**Státusz**: ✅ Tesztelésre kész
