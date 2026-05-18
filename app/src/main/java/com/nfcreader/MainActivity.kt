package com.nfcreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.nfcreader.domain.NFCReader
import com.nfcreader.domain.NFCViewModel
import com.nfcreader.presentation.NFCReaderScreen
import com.nfcreader.ui.theme.NFCReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val viewModel: NFCViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "Az NFC nem támogatott ezen az eszközön!", Toast.LENGTH_LONG).show()
        }

        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        setContent {
            NFCReaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NFCReaderApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter?.isEnabled == true) {
            enableForegroundDispatch()
        } else if (nfcAdapter != null) {
            Toast.makeText(this, "Az NFC ki van kapcsolva!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == action) {

            val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
            tag?.let {
                readTag(it)
            }
        }
    }

    private fun readTag(tag: Tag) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tagId = NFCReader.getTagId(tag)
                val (hexData, decodedData, sensorData) = NFCReader.readTag(tag)

                withContext(Dispatchers.Main) {
                    viewModel.addTag(tagId, hexData, decodedData, sensorData.temperature, sensorData.humidity)

                    val message = when {
                        sensorData.temperature != null && sensorData.humidity != null -> {
                            "Tag beolvasva! T: ${String.format("%.2f", sensorData.temperature)}°C, H: ${String.format("%.2f", sensorData.humidity)}%"
                        }
                        sensorData.temperature != null -> {
                            "Tag beolvasva! Hőmérséklet: ${String.format("%.2f", sensorData.temperature)}°C"
                        }
                        else -> {
                            "NFC Tag sikeresen beolvasva!"
                        }
                    }

                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Hiba történt a tag olvasása közben!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun enableForegroundDispatch() {
        val intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        )

        try {
            nfcAdapter?.enableForegroundDispatch(
                this,
                pendingIntent,
                intentFilters,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun disableForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun NFCReaderApp(viewModel: NFCViewModel) {
    val tags by viewModel.allTags.collectAsState()

    // Biztonsági hívás: Ha az NFCReaderScreen osztályként vagy más paraméterezéssel fut,
    // átadjuk neki a szükséges paramétereket, hogy ne tudjon mibe belekötni a fordító.
    NFCReaderScreen(
        tags = tags,
        onClearAll = { viewModel.clearAllTags() },
        onDeleteTag = { tagId -> viewModel.deleteTag(tagId) }
    )
}