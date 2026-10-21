package com.example

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.InputStream
import java.util.UUID

class WalletRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("symbian_wallet_prefs", Context.MODE_PRIVATE)

    private val _passes = MutableStateFlow<List<WalletPass>>(emptyList())
    val passes: StateFlow<List<WalletPass>> = _passes.asStateFlow()

    init {
        loadPasses()
    }

    private fun loadPasses() {
        val jsonStr = prefs.getString("wallet_passes_json", null)
        if (jsonStr.isNullOrEmpty()) {
            _passes.value = emptyList()
        } else {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<WalletPass>()
                for (i in 0 until array.length()) {
                    val passJson = array.optString(i)
                    val pass = WalletPass.fromJsonString(passJson)
                    if (pass != null) list.add(pass)
                }
                _passes.value = list
            } catch (e: Exception) {
                e.printStackTrace()
                _passes.value = emptyList()
            }
        }
    }

    private fun savePasses(list: List<WalletPass>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJsonString()) }
        prefs.edit().putString("wallet_passes_json", array.toString()).apply()
        _passes.value = list
    }

    suspend fun addPass(pass: WalletPass) = withContext(Dispatchers.IO) {
        val current = _passes.value.toMutableList()
        // Remove existing with same id if any
        current.removeAll { it.id == pass.id }
        current.add(0, pass)
        savePasses(current)
    }

    suspend fun removePass(passId: String) = withContext(Dispatchers.IO) {
        val current = _passes.value.toMutableList()
        current.removeAll { it.id == passId }
        savePasses(current)
    }

    suspend fun importPkPassUri(uri: Uri): WalletPass? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val pass = PkPassParser.parsePkPass(inputStream)
                if (pass != null) {
                    addPass(pass)
                    return@withContext pass
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun decodeBarcodeFromBitmap(bitmap: Bitmap): BarcodeUtils.ScannedBarcodeResult? = withContext(Dispatchers.IO) {
        BarcodeUtils.decodeBarcodeFromBitmap(bitmap)
    }

    suspend fun importFromBitmap(bitmap: Bitmap, cardTitle: String? = null): WalletPass? = withContext(Dispatchers.IO) {
        val scanResult = BarcodeUtils.decodeBarcodeFromBitmap(bitmap)
        if (scanResult != null) {
            val pass = BarcodeUtils.createPassFromScannedBarcode(scanResult, cardTitle)
            addPass(pass)
            pass
        } else {
            null
        }
    }

    suspend fun importFromImageUri(uri: Uri, cardTitle: String? = null): WalletPass? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                val scanResult = BarcodeUtils.decodeBarcodeFromBitmap(bitmap)
                if (scanResult != null) {
                    val pass = BarcodeUtils.createPassFromScannedBarcode(scanResult, cardTitle)
                    addPass(pass)
                    return@withContext pass
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun decodeBarcodeFromUri(uri: Uri): BarcodeUtils.ScannedBarcodeResult? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                BarcodeUtils.decodeBarcodeFromBitmap(bitmap)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun addLoyaltyCard(
        organizationName: String,
        memberNumber: String,
        memberName: String = "",
        tier: String = "",
        points: String = "",
        bgColor: Long = 0xFF006241,
        barcodeFormat: BarcodeFormatType = BarcodeFormatType.QR_CODE
    ): WalletPass = withContext(Dispatchers.IO) {
        val pass = WalletPass(
            id = UUID.randomUUID().toString(),
            type = PassType.LOYALTY_CARD,
            organizationName = organizationName.ifBlank { "Loyalty Card" },
            description = if (tier.isNotBlank()) "$tier Membership" else "Member Card",
            logoText = organizationName.uppercase(),
            backgroundColor = bgColor,
            foregroundColor = 0xFFFFFFFF,
            labelColor = 0xFFFFD54F,
            membershipTier = tier.ifBlank { null },
            balanceOrPoints = points.ifBlank { null },
            headerFields = if (points.isNotBlank()) listOf(PassField("points", "POINTS / BAL", points)) else emptyList(),
            primaryFields = listOf(
                PassField("card_num", "MEMBER ID", memberNumber)
            ),
            secondaryFields = if (memberName.isNotBlank()) listOf(
                PassField("member_name", "NAME", memberName)
            ) else emptyList(),
            barcode = PassBarcode(
                message = memberNumber,
                format = barcodeFormat,
                altText = memberNumber
            )
        )
        addPass(pass)
        pass
    }

    suspend fun clearAllPasses() = withContext(Dispatchers.IO) {
        savePasses(emptyList())
    }
}
