package com.nfcreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
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
import com.nfcreader.domain.NFCReader
import com.nfcreader.domain.NFCViewModel
import com.nfcreader.presentation.NFCReaderScreen
import com.nfcreader.ui.theme.NFCReaderTheme

/**
 * Fő Activity az NFC Reader alkalmazáshoz.
 * Kezeli az NFC adapter-t és a tag beolvasást.
 */
class MainActivity : ComponentActivity() {
    
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private val viewModel: NFCViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // NFC adapter inicializálása
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Ellenőrizzük, hogy az eszköz támogatja-e az NFC-t
        if (nfcAdapter == null) {
            Toast.makeText(
                this,
                getString(R.string.error_nfc_not_supported),
                Toast.LENGTH_LONG
            ).show()
        }
        
        // PendingIntent létrehozása az NFC eseményekhez
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
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
        
        // Ha az Activity Intent-ből indult, ellenőrizzük a tag-et
        handleIntent(intent)
    }
    
    override fun onResume() {
        super.onResume()
        
        // Ellenőrizzük, hogy az NFC be van-e kapcsolva
        nfcAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                Toast.makeText(
                    this,
                    getString(R.string.error_nfc_disabled),
                    Toast.LENGTH_LONG
                ).show()
                
                // Lehetőség van az NFC beállításokhoz navigálni
                // startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            } else {
                // Engedélyezzük az NFC foreground dispatch-t
                enableForegroundDispatch()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Kikapcsoljuk az NFC foreground dispatch-t
        disableForegroundDispatch()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    /**
     * NFC intent kezelése.
     */
    private fun handleIntent(intent: Intent) {
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                readTag(it)
            }
        }
    }
    
    /**
     * NFC tag olvasása és mentése az adatbázisba.
     */
    private fun readTag(tag: Tag) {
        try {
            val tagId = NFCReader.getTagId(tag)
            val (hexData, decodedData, temperature) = NFCReader.readTag(tag)
            
            // Tag mentése az adatbázisba a ViewModelen keresztül
            viewModel.addTag(tagId, hexData, decodedData, temperature)
            
            val message = if (temperature != null) {
                getString(R.string.tag_detected_with_temp, temperature)
            } else {
                getString(R.string.tag_detected)
            }
            
            Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.error_reading_tag),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * NFC foreground dispatch engedélyezése.
     * Ez biztosítja, hogy az alkalmazás előtérben legyen, amikor NFC tag-et észlelünk.
     */
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
    
    /**
     * NFC foreground dispatch kikapcsolása.
     */
    private fun disableForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Fő Composable függvény az alkalmazáshoz.
 */
@Composable
fun NFCReaderApp(viewModel: NFCViewModel) {
    val tags by viewModel.allTags.collectAsState()
    
    NFCReaderScreen(
        tags = tags,
        onClearAll = { viewModel.clearAllTags() },
        onDeleteTag = { tagId -> viewModel.deleteTag(tagId) }
    )
}
