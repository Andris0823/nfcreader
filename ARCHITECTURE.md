# NFC Reader - Fejlesztői Dokumentáció

## Architektúra Részletek

### MVVM Pattern Megvalósítása

Az alkalmazás a Model-View-ViewModel (MVVM) architektúrát követi, amely három fő komponensre osztja a kódot:

#### 1. Model (Adatkezelés)
```
data/
├── database/
│   ├── NFCTag.kt         # Entitás - adatmodell
│   ├── NFCTagDao.kt      # DAO - adatbázis műveletek
│   └── NFCDatabase.kt    # Database - Room konfiguráció
└── repository/
    └── NFCRepository.kt  # Repository - adathozzáférési réteg
```

**Előnyök:**
- Egyetlen forrása az igazságnak (Single Source of Truth)
- Perzisztens adattárolás Room-mal
- Reaktív adatfrissítés Flow-val

#### 2. ViewModel (Üzleti logika)
```
domain/
├── NFCViewModel.kt       # UI állapot kezelés
└── NFCReader.kt          # NFC olvasási logika
```

**Előnyök:**
- Configuration change túlélés
- UI logika szeparálása
- Coroutine lifecycle kezelés

#### 3. View (Megjelenítés)
```
presentation/
└── NFCReaderScreen.kt    # Compose UI komponensek

MainActivity.kt           # Activity - NFC kezelés
```

**Előnyök:**
- Deklaratív UI Compose-zal
- Automatikus recomposition
- Állapot hoisting

## Adatfolyam

```
NFC Tag
   ↓
MainActivity (NFC Intent)
   ↓
NFCReader.readTag() → Hex dekódolás
   ↓
ViewModel.addTag()
   ↓
Repository.insertTag()
   ↓
Room Database
   ↓
Flow<List<NFCTag>>
   ↓
StateFlow in ViewModel
   ↓
Compose UI (automatikus frissítés)
```

## Dependency Injection

Jelenleg **manuális DI**-t használunk:
- Database singleton a companion object-ben
- Repository példány a ViewModel-ben
- ViewModel az Activity-ben ViewModels() delegáttal

**Jövőbeli fejlesztés:** Hilt vagy Koin használata

## Coroutines és Flow

### ViewModelScope
```kotlin
viewModelScope.launch {
    repository.insertTag(tag)
}
```
- Automatikusan törli a coroutine-t, amikor a ViewModel clear-elődik
- Lifecycle-aware

### StateFlow
```kotlin
val allTags: StateFlow<List<NFCTag>> = repository.allTags.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
)
```
- Hot flow cache-eléssel
- Mindig van aktuális értéke
- Több collector is feliratkozhat

## Room Database

### Entity
```kotlin
@Entity(tableName = "nfc_tags")
data class NFCTag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tagId: String,
    val rawData: String,
    val decodedData: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

### DAO
```kotlin
@Dao
interface NFCTagDao {
    @Query("SELECT * FROM nfc_tags ORDER BY timestamp DESC")
    fun getAllTags(): Flow<List<NFCTag>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: NFCTag)
}
```

### Migration (jövőbeli verzióknál)
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE nfc_tags ADD COLUMN category TEXT")
    }
}
```

## NFC Olvasás Folyamata

### 1. Manifest Konfiguráció
```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />

<intent-filter>
    <action android:name="android.nfc.action.TAG_DISCOVERED" />
</intent-filter>
```

### 2. Foreground Dispatch
```kotlin
override fun onResume() {
    nfcAdapter?.enableForegroundDispatch(
        this, pendingIntent, intentFilters, null
    )
}
```

### 3. Tag Olvasás
```kotlin
override fun onNewIntent(intent: Intent) {
    val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    tag?.let { readTag(it) }
}
```

### 4. Mifare Ultralight Specifikus Olvasás
```kotlin
val mifareUltralight = MifareUltralight.get(tag)
mifareUltralight.connect()
for (page in 4..15) {
    val pageData = mifareUltralight.readPages(page)
    // Feldolgozás
}
```

## Jetpack Compose Best Practices

### State Hoisting
```kotlin
@Composable
fun NFCReaderApp(viewModel: NFCViewModel) {
    val tags by viewModel.allTags.collectAsState()
    
    NFCReaderScreen(
        tags = tags,
        onClearAll = { viewModel.clearAllTags() }
    )
}
```

### Reusable Composables
```kotlin
@Composable
fun TagCard(tag: NFCTag, onDelete: () -> Unit) {
    // UI komponens
}
```

### Preview
```kotlin
@Preview(showBackground = true)
@Composable
fun TagCardPreview() {
    NFCReaderTheme {
        TagCard(
            tag = NFCTag(/*...*/),
            onDelete = {}
        )
    }
}
```

## Testing Stratégia

### Unit Tesztek
```kotlin
class NFCViewModelTest {
    @Test
    fun `addTag should insert tag to repository`() {
        // Given
        val viewModel = NFCViewModel(app)
        
        // When
        viewModel.addTag("123", "ABCD", "text")
        
        // Then
        // Assert repository called
    }
}
```

### Instrumentation Tesztek
```kotlin
@Test
fun testDatabaseInsert() = runBlocking {
    val tag = NFCTag(tagId = "123", rawData = "ABC", decodedData = "text")
    dao.insertTag(tag)
    
    val tags = dao.getAllTags().first()
    assertThat(tags).contains(tag)
}
```

### Compose UI Tesztek
```kotlin
@Test
fun testTagCardDisplaysData() {
    composeTestRule.setContent {
        TagCard(tag = testTag, onDelete = {})
    }
    
    composeTestRule
        .onNodeWithText("123")
        .assertIsDisplayed()
}
```

## Performance Optimizálás

### LazyColumn
- Csak a látható elemeket rendereli
- Automatikus recycling

### remember és derivedStateOf
```kotlin
val filteredTags = remember(tags, searchQuery) {
    tags.filter { it.tagId.contains(searchQuery) }
}
```

### Flow Optimalizálás
```kotlin
allTags = repository.allTags
    .distinctUntilChanged()
    .stateIn(/* ... */)
```

## Hibakezelés

### Try-Catch blokkok
```kotlin
try {
    val (hexData, decodedData) = NFCReader.readTag(tag)
    viewModel.addTag(tagId, hexData, decodedData)
} catch (e: Exception) {
    Toast.makeText(this, "Error reading tag", Toast.LENGTH_SHORT).show()
}
```

### Null Safety
```kotlin
nfcAdapter?.let { adapter ->
    if (adapter.isEnabled) {
        enableForegroundDispatch()
    }
}
```

## Security Considerations

### Input Validation
```kotlin
fun decodeHexToUtf8(hexString: String): String {
    return try {
        val bytes = hexString.chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toByte() }
            .toByteArray()
        String(bytes, StandardCharsets.UTF_8)
            .filter { it.isLetterOrDigit() || it.isWhitespace() || /* ... */ }
    } catch (e: Exception) {
        "Unable to decode"
    }
}
```

### ProGuard Rules
```
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
```

## Gyakori Problémák és Megoldások

### 1. NFC nem működik
**Probléma:** Tag nem olvasható
**Megoldás:**
- Ellenőrizd az NFC engedélyt
- Foreground dispatch helyesen van-e beállítva
- Tech filter tartalmazza-e a megfelelő technológiákat

### 2. Database nem frissül
**Probléma:** UI nem frissül új tag beszúrásakor
**Megoldás:**
- Flow-t használj LiveData helyett
- collectAsState() Compose-ban

### 3. Memory Leak
**Probléma:** Activity nem szabadul fel
**Megoldás:**
- ViewModelScope használata
- Foreground dispatch disable onPause-ban

## Fejlesztési Roadmap

### v1.1
- [ ] Tag kategóriák
- [ ] Keresés és szűrés
- [ ] Export/Import

### v1.2
- [ ] NFC írás támogatás
- [ ] NDEF message parsing
- [ ] Multi-language

### v2.0
- [ ] Cloud sync
- [ ] Widget támogatás
- [ ] Dark mode

## Források

- [Android Developers](https://developer.android.com/)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [NFC Basics](https://developer.android.com/guide/topics/connectivity/nfc)
