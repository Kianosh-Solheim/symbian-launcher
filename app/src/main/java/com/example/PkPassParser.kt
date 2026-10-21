package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object PkPassParser {

    /**
     * Parses a .pkpass file (zipped archive) from an InputStream.
     * Safely reads pass.json and image assets without requiring external native dependencies.
     */
    fun parsePkPass(inputStream: InputStream): WalletPass? {
        val files = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.substringAfterLast("/")
                    if (!entry.isDirectory && name.isNotEmpty()) {
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            baos.write(buffer, 0, len)
                        }
                        files[name] = baos.toByteArray()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val passJsonBytes = files["pass.json"] ?: return null
        val passJsonStr = String(passJsonBytes, Charsets.UTF_8)
        
        return parsePassJson(passJsonStr, files)
    }

    fun parsePassJson(jsonStr: String, images: Map<String, ByteArray> = emptyMap()): WalletPass? {
        try {
            val root = JSONObject(jsonStr)
            val orgName = root.optString("organizationName", "Airline / Issuer")
            val description = root.optString("description", "Boarding Pass")
            val logoText = root.optString("logoText").takeIf { it.isNotEmpty() }
            val serialNumber = root.optString("serialNumber", UUID.randomUUID().toString())

            val bgColor = parseColor(root.optString("backgroundColor"), 0xFF1B2838)
            val fgColor = parseColor(root.optString("foregroundColor"), 0xFFFFFFFF)
            val labelColor = parseColor(root.optString("labelColor"), 0xFFA0AEC0)

            // Determine Pass Type and structure
            var passType = PassType.GENERIC
            var transitType: TransitType? = null
            var structureObj: JSONObject? = null

            if (root.has("boardingPass")) {
                passType = PassType.BOARDING_PASS
                structureObj = root.getJSONObject("boardingPass")
                val transitStr = structureObj.optString("transitType", "PKTransitTypeAir")
                transitType = when (transitStr) {
                    "PKTransitTypeAir" -> TransitType.AIR
                    "PKTransitTypeTrain" -> TransitType.TRAIN
                    "PKTransitTypeBus" -> TransitType.BUS
                    "PKTransitTypeBoat" -> TransitType.BOAT
                    else -> TransitType.GENERIC
                }
            } else if (root.has("storeCard")) {
                passType = PassType.LOYALTY_CARD
                structureObj = root.getJSONObject("storeCard")
            } else if (root.has("eventTicket")) {
                passType = PassType.EVENT_TICKET
                structureObj = root.getJSONObject("eventTicket")
            } else if (root.has("coupon")) {
                passType = PassType.COUPON
                structureObj = root.getJSONObject("coupon")
            } else if (root.has("generic")) {
                passType = PassType.GENERIC
                structureObj = root.getJSONObject("generic")
            }

            fun parseFields(key: String): List<PassField> {
                if (structureObj == null || !structureObj.has(key)) return emptyList()
                val arr = structureObj.optJSONArray(key) ?: return emptyList()
                val list = mutableListOf<PassField>()
                for (i in 0 until arr.length()) {
                    val f = arr.optJSONObject(i) ?: continue
                    val fKey = f.optString("key", "")
                    val fLabel = f.optString("label", "")
                    val fVal = f.optString("value", "")
                    val fChange = f.optString("changeMessage").takeIf { it.isNotEmpty() }
                    list.add(PassField(fKey, fLabel, fVal, fChange))
                }
                return list
            }

            val headerFields = parseFields("headerFields")
            val primaryFields = parseFields("primaryFields")
            val secondaryFields = parseFields("secondaryFields")
            val auxiliaryFields = parseFields("auxiliaryFields")
            val backFields = parseFields("backFields")

            // Parse Barcode (Apple Wallet supports 'barcodes' array or legacy 'barcode' object)
            var barcode: PassBarcode? = null
            if (root.has("barcodes")) {
                val barcodesArr = root.optJSONArray("barcodes")
                if (barcodesArr != null && barcodesArr.length() > 0) {
                    val bObj = barcodesArr.getJSONObject(0)
                    barcode = PassBarcode(
                        message = bObj.optString("message", ""),
                        format = BarcodeFormatType.fromPkPass(bObj.optString("format")),
                        altText = bObj.optString("altText").takeIf { it.isNotEmpty() },
                        messageEncoding = bObj.optString("messageEncoding", "iso-8859-1")
                    )
                }
            } else if (root.has("barcode")) {
                val bObj = root.getJSONObject("barcode")
                barcode = PassBarcode(
                    message = bObj.optString("message", ""),
                    format = BarcodeFormatType.fromPkPass(bObj.optString("format")),
                    altText = bObj.optString("altText").takeIf { it.isNotEmpty() },
                    messageEncoding = bObj.optString("messageEncoding", "iso-8859-1")
                )
            }

            // Extract images into Base64
            fun getImageBase64(vararg candidates: String): String? {
                for (c in candidates) {
                    val bytes = images[c]
                    if (bytes != null && bytes.isNotEmpty()) {
                        return Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }
                return null
            }

            val logoBase64 = getImageBase64("logo@2x.png", "logo.png", "logo@3x.png")
            val iconBase64 = getImageBase64("icon@2x.png", "icon.png", "icon@3x.png")
            val stripBase64 = getImageBase64("strip@2x.png", "strip.png", "strip@3x.png", "background@2x.png", "background.png")

            // Extract airline specifics from fields or IATA barcode
            var passengerName: String? = null
            var flightNumber: String? = null
            var originAirport: String? = null
            var originCity: String? = null
            var destinationAirport: String? = null
            var destinationCity: String? = null
            var departureDate: String? = null
            var boardingTime: String? = null
            var departureTime: String? = null
            var gate: String? = null
            var seat: String? = null
            var flightClass: String? = null
            var sequenceNumber: String? = null
            var pnrCode: String? = null
            var membershipTier: String? = null
            var balanceOrPoints: String? = null

            // Inspect primary fields
            if (primaryFields.size >= 2) {
                originAirport = primaryFields[0].value
                originCity = primaryFields[0].label
                destinationAirport = primaryFields[1].value
                destinationCity = primaryFields[1].label
            } else if (primaryFields.size == 1) {
                passengerName = primaryFields[0].value
            }

            val allFields = headerFields + primaryFields + secondaryFields + auxiliaryFields + backFields
            for (f in allFields) {
                val k = f.key.lowercase()
                val l = f.label.lowercase()
                val v = f.value
                when {
                    k.contains("passenger") || l.contains("passenger") || l.contains("name") -> if (passengerName == null) passengerName = v
                    k.contains("flight") || l.contains("flight") -> if (flightNumber == null) flightNumber = v
                    k.contains("gate") || l.contains("gate") -> if (gate == null) gate = v
                    k.contains("seat") || l.contains("seat") -> if (seat == null) seat = v
                    k.contains("class") || l.contains("class") || l.contains("cabin") -> if (flightClass == null) flightClass = v
                    k.contains("boarding") || l.contains("boarding") -> if (boardingTime == null) boardingTime = v
                    k.contains("depart") || l.contains("depart") -> if (departureTime == null) departureTime = v
                    k.contains("date") || l.contains("date") -> if (departureDate == null) departureDate = v
                    k.contains("seq") || l.contains("seq") -> if (sequenceNumber == null) sequenceNumber = v
                    k.contains("pnr") || l.contains("booking") || l.contains("record") -> if (pnrCode == null) pnrCode = v
                    k.contains("tier") || l.contains("tier") || l.contains("status") -> if (membershipTier == null) membershipTier = v
                    k.contains("points") || k.contains("balance") || l.contains("balance") || l.contains("points") -> if (balanceOrPoints == null) balanceOrPoints = v
                }
            }

            // IATA BCBP (Bar Coded Boarding Pass) Barcode format fallback parser
            if (barcode != null && barcode.message.startsWith("M1")) {
                val iataData = parseIataBcbp(barcode.message)
                if (iataData != null) {
                    if (passengerName.isNullOrEmpty()) passengerName = iataData.passengerName
                    if (flightNumber.isNullOrEmpty()) flightNumber = iataData.flightNumber
                    if (originAirport.isNullOrEmpty()) originAirport = iataData.originAirport
                    if (destinationAirport.isNullOrEmpty()) destinationAirport = iataData.destinationAirport
                    if (pnrCode.isNullOrEmpty()) pnrCode = iataData.pnr
                    if (seat.isNullOrEmpty()) seat = iataData.seat
                    if (flightClass.isNullOrEmpty()) flightClass = iataData.flightClass
                    if (sequenceNumber.isNullOrEmpty()) sequenceNumber = iataData.sequenceNumber
                }
            }

            return WalletPass(
                id = serialNumber.ifEmpty { UUID.randomUUID().toString() },
                type = passType,
                organizationName = orgName,
                description = description,
                logoText = logoText,
                backgroundColor = bgColor,
                foregroundColor = fgColor,
                labelColor = labelColor,
                headerFields = headerFields,
                primaryFields = primaryFields,
                secondaryFields = secondaryFields,
                auxiliaryFields = auxiliaryFields,
                backFields = backFields,
                barcode = barcode,
                transitType = transitType,
                logoBitmapBase64 = logoBase64,
                iconBitmapBase64 = iconBase64,
                stripBitmapBase64 = stripBase64,
                passengerName = passengerName,
                flightNumber = flightNumber,
                originAirport = originAirport,
                originCity = originCity,
                destinationAirport = destinationAirport,
                destinationCity = destinationCity,
                departureDate = departureDate,
                boardingTime = boardingTime,
                departureTime = departureTime,
                gate = gate,
                seat = seat,
                flightClass = flightClass,
                sequenceNumber = sequenceNumber,
                pnrCode = pnrCode,
                membershipTier = membershipTier,
                balanceOrPoints = balanceOrPoints
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Parses IATA Bar Coded Boarding Pass (BCBP) standard format (e.g. M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 226F001A0025 100)
     */
    data class IataBoardingPassData(
        val passengerName: String,
        val pnr: String,
        val originAirport: String,
        val destinationAirport: String,
        val carrier: String,
        val flightNumber: String,
        val julianDate: String,
        val flightClass: String,
        val seat: String,
        val sequenceNumber: String
    )

    fun parseIataBcbp(raw: String): IataBoardingPassData? {
        try {
            if (!raw.startsWith("M1") || raw.length < 58) return null
            // Standard IATA BCBP Field offsets:
            // 0..1: "M1"
            // 2..21: Passenger Name (20 chars, e.g. "SURNAME/FIRSTNAME")
            // 22..22: Electronic ticket indicator "E"
            // 23..29: PNR / Booking Code (7 chars)
            // 30..32: From Airport (3 chars)
            // 33..35: To Airport (3 chars)
            // 36..38: Operating Carrier (3 chars)
            // 39..43: Flight Number (5 chars)
            // 44..46: Julian Date of Flight (3 chars)
            // 47..47: Compartment / Class (1 char)
            // 48..51: Seat (4 chars, e.g. "001A", "022C")
            // 52..56: Check-in Sequence Number (5 chars)
            val nameRaw = raw.substring(2, 22.coerceAtMost(raw.length)).trim()
            val formattedName = if (nameRaw.contains("/")) {
                val parts = nameRaw.split("/")
                val last = parts.getOrNull(0)?.trim() ?: ""
                val first = parts.getOrNull(1)?.trim() ?: ""
                "$first $last".trim()
            } else {
                nameRaw
            }

            val pnr = raw.substring(23, 30.coerceAtMost(raw.length)).trim()
            val from = raw.substring(30, 33.coerceAtMost(raw.length)).trim()
            val to = raw.substring(33, 36.coerceAtMost(raw.length)).trim()
            val carrier = raw.substring(36, 39.coerceAtMost(raw.length)).trim()
            val flightNum = raw.substring(39, 44.coerceAtMost(raw.length)).trim()
            val flightFull = if (carrier.isNotEmpty() && flightNum.isNotEmpty()) "$carrier $flightNum" else carrier + flightNum
            val julian = raw.substring(44, 47.coerceAtMost(raw.length)).trim()
            val cabinClass = raw.substring(47, 48.coerceAtMost(raw.length)).trim()
            val seatRaw = raw.substring(48, 52.coerceAtMost(raw.length)).trim().trimStart('0')
            val seq = if (raw.length >= 57) raw.substring(52, 57.coerceAtMost(raw.length)).trim().trimStart('0') else ""

            return IataBoardingPassData(
                passengerName = formattedName,
                pnr = pnr,
                originAirport = from,
                destinationAirport = to,
                carrier = carrier,
                flightNumber = flightFull,
                julianDate = julian,
                flightClass = cabinClass,
                seat = seatRaw,
                sequenceNumber = seq
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseColor(colorStr: String?, defaultColor: Long): Long {
        if (colorStr.isNullOrBlank()) return defaultColor
        val s = colorStr.trim()
        return try {
            if (s.startsWith("rgb", ignoreCase = true)) {
                val nums = s.substringAfter("(").substringBefore(")").split(",")
                val r = nums.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                val g = nums.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val b = nums.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                (0xFFL shl 24) or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)
            } else if (s.startsWith("#")) {
                val hex = s.substring(1)
                if (hex.length == 6) {
                    0xFF000000L or hex.toLong(16)
                } else if (hex.length == 8) {
                    hex.toLong(16)
                } else {
                    defaultColor
                }
            } else {
                defaultColor
            }
        } catch (e: Exception) {
            defaultColor
        }
    }
}
