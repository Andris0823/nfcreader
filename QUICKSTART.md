# Gyors Útmutató - NFC Reader

## 🚀 5 Perces Beüzemelés

### 1. Klónozd a projektet
```bash
git clone https://github.com/Andris0823/nfcreader.git
cd nfcreader
```

### 2. Nyisd meg Android Studio-ban
- Indítsd el az Android Studio-t
- File → Open
- Válaszd ki a `nfcreader` mappát
- Várj, amíg a Gradle build befejeződik

### 3. Futtasd az alkalmazást
- Csatlakoztass egy NFC-képes eszközt USB-n keresztül
- Kattints a Play (▶️) gombra
- Válaszd ki az eszközt
- Az app települ és elindul

### 4. Használd az alkalmazást
- Kapcsold be az NFC-t az eszközön (Beállítások → Kapcsolatok)
- Érintsd a telefon hátlapját egy NFC tag-hez
- A beolvasott adat azonnal megjelenik

## 📱 Rendszerkövetelmények

### Fejlesztői környezet
- **Android Studio**: Giraffe (2022.3.1) vagy újabb
- **JDK**: 17 vagy újabb
- **Gradle**: 8.2
- **Kotlin**: 1.9.10

### Céleszköz
- **Android verzió**: 7.0 (API 24) vagy újabb
- **NFC**: Kötelező
- **Memória**: Minimum 100 MB szabad hely

## 🔧 Projekt Struktúra (Egyszerűsített)

```
nfcreader/
├── app/src/main/java/com/nfcreader/
│   ├── MainActivity.kt              # Fő belépési pont
│   ├── data/                        # Adatbázis réteg
│   ├── domain/                      # Üzleti logika
│   └── presentation/                # UI komponensek
├── build.gradle.kts                 # Gradle konfiguráció
└── README.md                        # Dokumentáció
```

## 🎯 Használati Esetek

### 1. Tag Beolvasása
```
1. Indítsd el az appot
2. Érintsd a telefont egy NFC tag-hez
3. A tag adatai megjelennek a listában
```

### 2. Tag Törlése
```
1. Keresd meg a tag-et a listában
2. Kattints a törlés (🗑️) ikonra
3. A tag eltávolításra kerül
```

### 3. Összes Tag Törlése
```
1. Kattints a felső menüsávban a törlés ikonra
2. Minden beolvasott tag törlődik
```

## 🐛 Gyakori Problémák

### Az app nem indul el
**Megoldás:**
```bash
./gradlew clean
./gradlew build
```

### NFC nem működik
1. Ellenőrizd, hogy az NFC be van-e kapcsolva
2. Próbálj meg különböző tag-eket
3. Győződj meg róla, hogy az eszköz támogatja az NFC-t

### Build hiba
1. File → Invalidate Caches / Restart
2. Sync Project with Gradle Files
3. Ellenőrizd az internet kapcsolatot (függőségek letöltéséhez)

## 📚 Következő Lépések

1. Olvasd el a [teljes README-t](README.md) a részletes dokumentációért
2. Nézd meg az [ARCHITECTURE.md](ARCHITECTURE.md) fájlt a technikai részletekért
3. Próbáld ki az alkalmazást különböző NFC tag-ekkel

## 💡 Tippek

- **Debug mode**: BuildConfig.DEBUG használata különböző környezetekhez
- **Logolás**: Használj Logcat-et problémák diagnosztizálásához
- **Testing**: Emulátorban nincs NFC, fizikai eszköz szükséges

## 🔗 Hasznos Linkek

- [Android NFC Guide](https://developer.android.com/guide/topics/connectivity/nfc)
- [Jetpack Compose Tutorial](https://developer.android.com/jetpack/compose/tutorial)
- [Room Database](https://developer.android.com/training/data-storage/room)

## 📞 Támogatás

Ha problémád van:
1. Nézd meg a [Gyakori Problémák](#-gyakori-problémák) szekciót
2. Nyiss egy [Issue-t](https://github.com/Andris0823/nfcreader/issues) GitHub-on
3. Ellenőrizd a [Dokumentációt](README.md)

---

**Boldog kódolást! 🎉**
