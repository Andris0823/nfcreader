# NFC Reader - Android Alkalmazás

## 📱 Áttekintés

Ez egy professzionális NFC tag (Mifare Ultralight) olvasó alkalmazás Android platformra, amely Kotlin nyelven készült és modern Android fejlesztési gyakorlatokat követ.

## ✨ Főbb Funkciók

- **NFC Tag Olvasás**: Mifare Ultralight és más NFC tagek beolvasása
- **CAEN qLOG RT0013 Támogatás**: Optimalizált hőmérséklet és páratartalom szenzor olvasás
- **Hőmérséklet és Páratartalom**: Valós idejű környezeti adatok megjelenítése
- **Hex → UTF-8 Dekódolás**: Automatikus adatkonverzió hexadecimális formátumból UTF-8 szöveggé
- **Adatbázis Tárolás**: Beolvasott tagek perzisztens tárolása Room adatbázisban szenzor adatokkal
- **Modern UI**: Jetpack Compose alapú, Material Design 3 felhasználói felület
- **MVVM Architektúra**: Tiszta kódszervezés és szeparált felelősségi körök

## 🏗️ Alkalmazás Architektúra

Ez az alkalmazás az **MVVM (Model-View-ViewModel)** architektúrát követi, amely három fő rétegre osztja a kódot:

### 1. **Data Layer (Adat Réteg)**
Ez a réteg felelős az adatok tárolásáért és kezeléséért.

- **Entity (`NFCTag.kt`)**: Room adatbázis entitás, amely egy NFC tag adatait reprezentálja
  - `id`: Automatikusan generált egyedi azonosító
  - `tagId`: Az NFC tag UID-je hexadecimális formátumban
  - `rawData`: Nyers hexadecimális adat
  - `decodedData`: UTF-8 dekódolt szöveg
  - `temperature`: Hőmérséklet adat (Celsius), ha elérhető (CAEN qLOG RT0013)
  - `humidity`: Páratartalom adat (%), ha elérhető (CAEN qLOG RT0013)
  - `timestamp`: Beolvasás időpontja

- **DAO (`NFCTagDao.kt`)**: Data Access Object - adatbázis műveletek definiálása
  - `getAllTags()`: Összes tag lekérése Flow formában
  - `insertTag()`: Új tag beszúrása
  - `deleteAllTags()`: Összes tag törlése
  - `deleteTag()`: Egy adott tag törlése

- **Database (`NFCDatabase.kt`)**: Room adatbázis singleton implementáció
  - Thread-safe adatbázis példány kezelés
  - Automatikus séma migráció

- **Repository (`NFCRepository.kt`)**: Absztrakciós réteg az adatforrás és az üzleti logika között
  - Egyszerűsített interfész a DAO műveletek eléréséhez
  - Lehetővé teszi az adatforrás későbbi cseréjét

### 2. **Domain Layer (Üzleti Logika Réteg)**
Ez a réteg tartalmazza az üzleti logikát és az adatok feldolgozását.

- **ViewModel (`NFCViewModel.kt`)**: UI állapot kezelés és üzleti logika koordináció
  - StateFlow használata az UI reaktív frissítéséhez
  - Coroutine-ok kezelése ViewModelScope-pal
  - Repository műveletek koordinálása

- **NFCReader (`NFCReader.kt`)**: NFC tag olvasási és dekódolási logika
  - **CAEN qLOG RT0013 optimalizált olvasás** (NfcV/ISO15693 protokoll)
  - Hőmérséklet és páratartalom szenzor támogatás
  - Mifare Ultralight specifikus olvasás (fallback)
  - NTAG21x T hőmérséklet szenzor támogatás (fallback)
  - NDEF formátum támogatás
  - Hex → UTF-8 dekódolás
  - Többféle NFC technológia támogatása (NfcV, NfcA, MifareUltralight)

### 3. **Presentation Layer (Megjelenítési Réteg)**
Ez a réteg felelős a felhasználói felületért.

- **MainActivity (`MainActivity.kt`)**: Fő Activity, NFC adapter kezelés
  - NFC Intent kezelés
  - Foreground Dispatch beállítása
  - Tag olvasás koordinálása

- **NFCReaderScreen (`NFCReaderScreen.kt`)**: Jetpack Compose UI komponensek
  - `NFCReaderScreen`: Fő képernyő scaffolding
  - `InstructionCard`: Használati utasítás kártya
  - `TagList`: Tag lista megjelenítés
  - `TagCard`: Egyedi tag kártya formázott adatokkal
  - `EmptyState`: Üres állapot megjelenítés

## 📁 Fájlszerkezet

```
nfcreader/
├── app/
│   ├── build.gradle.kts              # App szintű Gradle konfiguráció
│   ├── proguard-rules.pro            # ProGuard szabályok
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml   # App manifest NFC engedélyekkel
│           ├── java/com/nfcreader/
│           │   ├── data/             # 📦 DATA LAYER
│           │   │   ├── database/
│           │   │   │   ├── NFCTag.kt          # Room Entity
│           │   │   │   ├── NFCTagDao.kt       # Data Access Object
│           │   │   │   └── NFCDatabase.kt     # Room Database
│           │   │   └── repository/
│           │   │       └── NFCRepository.kt   # Repository pattern
│           │   │
│           │   ├── domain/           # 🎯 DOMAIN LAYER
│           │   │   ├── NFCReader.kt           # NFC olvasási logika
│           │   │   └── NFCViewModel.kt        # ViewModel
│           │   │
│           │   ├── presentation/     # 🎨 PRESENTATION LAYER
│           │   │   └── NFCReaderScreen.kt     # Compose UI komponensek
│           │   │
│           │   ├── ui/theme/         # 🎨 UI THEME
│           │   │   ├── Color.kt               # Színek definiálása
│           │   │   ├── Theme.kt               # Material Theme
│           │   │   └── Type.kt                # Typography
│           │   │
│           │   └── MainActivity.kt            # Fő Activity
│           │
│           └── res/                  # 📱 RESOURCES
│               ├── values/
│               │   ├── strings.xml            # Szövegek (magyarul)
│               │   ├── colors.xml             # Színek
│               │   └── themes.xml             # App témák
│               ├── xml/
│               │   ├── nfc_tech_filter.xml    # NFC technológia szűrők
│               │   ├── backup_rules.xml       # Backup szabályok
│               │   └── data_extraction_rules.xml
│               └── mipmap-*/                  # App ikonok
│
├── build.gradle.kts                  # Project szintű Gradle
├── settings.gradle.kts               # Gradle beállítások
├── gradle.properties                 # Gradle tulajdonságok
└── README.md                         # Ez a fájl
```

## 🔧 Technológiai Stack

### Core Technologies
- **Kotlin**: Modern, biztonságos programozási nyelv
- **Android SDK**: Minimum API 24 (Android 7.0), Target API 34 (Android 14)
- **Gradle**: Build automatizálás (Kotlin DSL)

### Jetpack Libraries
- **Jetpack Compose**: Deklaratív UI framework
  - Material Design 3 komponensek
  - State management
  - Reactive UI updates
  
- **Room Database**: SQLite absztrakciós réteg
  - Type-safe adatbázis hozzáférés
  - Compile-time SQL ellenőrzés
  - Flow/LiveData támogatás

- **ViewModel**: UI állapot megőrzés
  - Configuration change túlélés
  - Lifecycle-aware komponens

- **Lifecycle**: Lifecycle-aware komponensek
  - Automatikus coroutine kezelés
  - Memory leak megelőzés

### Other Libraries
- **Kotlin Coroutines**: Aszinkron programozás
  - Flow API reaktív streamekhez
  - Suspend függvények háttérműveletekhez

- **KSP (Kotlin Symbol Processing)**: Annotation processing
  - Room code generation

## 🚀 Első Lépések

### Előfeltételek
- Android Studio (legújabb verzió ajánlott)
- JDK 17 vagy újabb
- Android SDK 24+ (Android 7.0+)
- NFC-képes Android eszköz teszteléshez

### Telepítés és Futtatás

1. **Projekt megnyitása**
   ```bash
   git clone <repository-url>
   cd nfcreader
   ```

2. **Android Studio-ban megnyitás**
   - File → Open → Válaszd ki a projekt mappát
   - Várj, amíg a Gradle szinkronizáció befejeződik

3. **Futtatás**
   - Csatlakoztass egy NFC-képes Android eszközt vagy használj emulátort
   - Kattints a Run (▶️) gombra
   - Az app települ és elindul az eszközön

### Build Variánsok
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Tesztek futtatása
./gradlew test
```

## 📖 Használat

1. **App indítás**: Nyisd meg az alkalmazást
2. **NFC engedélyezés**: Győződj meg róla, hogy az NFC be van kapcsolva az eszközön
3. **Tag olvasás**: Érintsd a telefon hátlapját egy NFC taghez
4. **Adat megjelenítés**: A beolvasott adat azonnal megjelenik a listában
5. **Hőmérséklet**: Ha a tag támogatja (NTAG21x T variánsok), a hőmérséklet is megjelenik
6. **Törlés**: Törölhetsz egyedi tageket vagy az összes tárolt adatot

### CAEN qLOG RT0013 Szenzor Támogatás

Az alkalmazás **elsődlegesen a CAEN RFID qLOG RT0013 NFC hőmérséklet és páratartalom logger-re van optimalizálva**:

#### CAEN qLOG RT0013 Specifikációk
- **Protokoll**: ISO15693 / NfcV (NFC)
- **Szenzorok**: Hőmérséklet + Páratartalom
- **Mérési tartomány**: 
  - Hőmérséklet: -40°C - +85°C
  - Páratartalom: 0% - 100% RH
- **Pontosság**: 
  - Hőmérséklet: ±0.3°C (tipikus)
  - Páratartalom: ±2% RH
- **Felbontás**: 0.01°C / 0.01% RH
- **Memória cím**: Blokk 0x0A (legutóbbi mérés)
- **Megjelenítés**: Hőmérséklet (🌡️) és páratartalom (💧) automatikusan megjelenik

#### Fallback Támogatás (NTAG21x T)
Ha a tag nem CAEN qLOG RT0013, az alkalmazás automatikusan megpróbálja NTAG21x T-ként olvasni:
- **Támogatott tagek**: NTAG210μ, NTAG213 TT, NTAG215 TT
- **Mérési tartomány**: -25°C - +70°C
- **Pontosság**: ±2°C
- **Memória cím**: 0x29 (41) oldal

Az alkalmazás automatikusan felismeri a tag típusát és a megfelelő olvasási módszert alkalmazza.

## 💡 Fejlesztési Tippek és Tanácsok

### MVVM Architektúra Előnyei
- **Szeparált felelősségek**: Minden réteg jól definiált feladattal rendelkezik
- **Tesztelhetőség**: Az üzleti logika függetlenül tesztelhető az UI-tól
- **Újrafelhasználhatóság**: A ViewModel és Repository könnyen újrafelhasználható
- **Karbantarthatóság**: Tiszta kód struktúra, könnyebb változtatások

### Room Database Best Practices
- **Flow használata**: Automatikus UI frissítés adatváltozáskor
- **Suspend függvények**: Háttérszálú műveletek coroutine-okkal
- **Type Converters**: Komplex adattípusok tárolása
- **Migration Strategy**: Séma változások kezelése

### Jetpack Compose Tips
- **State Hoisting**: Állapot kezelés a komponens fa tetején
- **Recomposition**: Csak a változó részek újrarajzolása
- **Preview**: `@Preview` annotációval gyors UI tesztelés
- **Theming**: Material Design 3 konzisztens megjelenéshez

### NFC Fejlesztés
- **Foreground Dispatch**: Prioritás adás az appnak NFC olvasáskor
- **Tech Filter**: Megfelelő NFC technológiák szűrése
- **Error Handling**: Robusztus hibakezelés (tag távolítás, időtúllépés)
- **Permissions**: NFC permission és feature deklarálás

### Coroutines és Flow
- **ViewModelScope**: Automatikus törlés a ViewModel megszűnésekor
- **StateFlow**: Hot stream cache-eléssel az utolsó értékre
- **Flow operators**: map, filter, combine stb. adattranszformációhoz

## 🔐 Biztonsági Szempontok

- **NFC Permission**: Manifest-ben deklarált engedély
- **Input Validation**: Bejövő NFC adatok validálása
- **SQL Injection**: Room automatikusan véd ellene
- **ProGuard**: Code obfuscation release buildben

## 🧪 Tesztelés

### Unit Tesztek
- ViewModel logika tesztelése
- Repository műveletek tesztelése
- Utility függvények (hex dekódolás) tesztelése

### Instrumentation Tesztek
- Room DAO műveletek tesztelése
- UI komponensek tesztelése Compose Test API-val

### Manual Tesztelés
- Különböző NFC tag típusok tesztelése
- Edge case-ek (üres tag, hibás adat)
- UI flow tesztelése

## 📝 További Fejlesztési Lehetőségek

1. **NFC Írás funkció**: Tag-ekre írás lehetősége
2. **Export/Import**: Adatok exportálása és importálása (JSON, CSV)
3. **Szűrés és keresés**: Tag-ek szűrése dátum, ID vagy tartalom szerint
4. **Statisztikák**: Beolvasott tag-ek statisztikái
5. **Cloud Sync**: Adatok szinkronizálása felhővel
6. **QR Code**: QR kód generálás tag adatokból
7. **Különböző NFC formátumok**: NDEF rekord parsing és megjelenítés
8. **Multi-language**: Többnyelvű támogatás
9. **Dark Mode**: Teljes dark mode támogatás
10. **Widget**: Home screen widget ngyors beolvasáshoz

## 🤝 Közreműködés

Ha szeretnél hozzájárulni a projekthez:
1. Fork-old a repository-t
2. Készíts egy feature branch-et
3. Commit-old a változtatásokat
4. Push-old a branch-et
5. Nyiss egy Pull Request-et

## 📄 Licenc

Ez a projekt oktatási célokra készült. Szabadon használható és módosítható.

## 👤 Kapcsolat

Ha kérdésed van vagy segítségre van szükséged, nyiss egy Issue-t a GitHub-on.

---

## 🎓 Tanulási Források

### Android Fejlesztés
- [Android Developer Documentation](https://developer.android.com/)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Android Codelabs](https://developer.android.com/courses)

### Jetpack Compose
- [Compose Pathway](https://developer.android.com/courses/pathways/compose)
- [Compose Samples](https://github.com/android/compose-samples)

### Room Database
- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [Room Codelab](https://developer.android.com/codelabs/android-room-with-a-view)

### NFC Fejlesztés
- [NFC Basics](https://developer.android.com/guide/topics/connectivity/nfc/nfc)
- [Advanced NFC](https://developer.android.com/guide/topics/connectivity/nfc/advanced-nfc)

### MVVM Architektúra
- [Guide to App Architecture](https://developer.android.com/topic/architecture)
- [ViewModel Overview](https://developer.android.com/topic/libraries/architecture/viewmodel)

---

**Készítve ❤️-tel és Kotlin-nal**