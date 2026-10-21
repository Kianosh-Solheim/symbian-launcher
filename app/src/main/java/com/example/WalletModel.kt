package com.example

import org.json.JSONArray
import org.json.JSONObject

enum class PassType(val displayName: String) {
    BOARDING_PASS("Boarding Pass"),
    LOYALTY_CARD("Loyalty Card"),
    EVENT_TICKET("Event Ticket"),
    COUPON("Coupon"),
    GENERIC("Card / Pass")
}

enum class TransitType(val displayName: String) {
    AIR("Flight"),
    TRAIN("Train"),
    BUS("Bus"),
    BOAT("Ferry/Boat"),
    GENERIC("Transit")
}

enum class BarcodeFormatType {
    QR_CODE,
    AZTEC,
    PDF_417,
    CODE_128,
    CODE_39,
    EAN_13,
    UNKNOWN;

    companion object {
        fun fromPkPass(formatStr: String?): BarcodeFormatType {
            if (formatStr == null) return QR_CODE
            return when (formatStr.uppercase()) {
                "PKBARCODEFORMATQR", "QR", "QR_CODE" -> QR_CODE
                "PKBARCODEFORMATPDF417", "PDF417", "PDF_417" -> PDF_417
                "PKBARCODEFORMATAZTEC", "AZTEC" -> AZTEC
                "PKBARCODEFORMATCODE128", "CODE128", "CODE_128" -> CODE_128
                "PKBARCODEFORMATCODE39", "CODE39", "CODE_39" -> CODE_39
                else -> QR_CODE
            }
        }
    }
}

data class PassBarcode(
    val message: String,
    val format: BarcodeFormatType,
    val altText: String? = null,
    val messageEncoding: String = "iso-8859-1"
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("message", message)
        obj.put("format", format.name)
        obj.put("altText", altText ?: "")
        obj.put("messageEncoding", messageEncoding)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): PassBarcode {
            val formatStr = obj.optString("format", "QR_CODE")
            val format = runCatching { BarcodeFormatType.valueOf(formatStr) }.getOrDefault(BarcodeFormatType.QR_CODE)
            return PassBarcode(
                message = obj.optString("message", ""),
                format = format,
                altText = obj.optString("altText").takeIf { it.isNotEmpty() },
                messageEncoding = obj.optString("messageEncoding", "iso-8859-1")
            )
        }
    }
}

data class PassField(
    val key: String,
    val label: String,
    val value: String,
    val changeMessage: String? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("key", key)
        obj.put("label", label)
        obj.put("value", value)
        if (changeMessage != null) obj.put("changeMessage", changeMessage)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): PassField {
            return PassField(
                key = obj.optString("key", ""),
                label = obj.optString("label", ""),
                value = obj.optString("value", ""),
                changeMessage = obj.optString("changeMessage").takeIf { it.isNotEmpty() }
            )
        }
    }
}

data class WalletPass(
    val id: String,
    val type: PassType,
    val organizationName: String,
    val description: String,
    val logoText: String? = null,
    val backgroundColor: Long = 0xFF1C2B39,
    val foregroundColor: Long = 0xFFFFFFFF,
    val labelColor: Long = 0xFFA0AEC0,
    val headerFields: List<PassField> = emptyList(),
    val primaryFields: List<PassField> = emptyList(),
    val secondaryFields: List<PassField> = emptyList(),
    val auxiliaryFields: List<PassField> = emptyList(),
    val backFields: List<PassField> = emptyList(),
    val barcode: PassBarcode? = null,
    val transitType: TransitType? = null,
    val logoBitmapBase64: String? = null,
    val iconBitmapBase64: String? = null,
    val stripBitmapBase64: String? = null,
    val importedAt: Long = System.currentTimeMillis(),

    // Cached / extracted fields for quick airline boarding pass rendering
    val passengerName: String? = null,
    val flightNumber: String? = null,
    val originAirport: String? = null,
    val originCity: String? = null,
    val destinationAirport: String? = null,
    val destinationCity: String? = null,
    val departureDate: String? = null,
    val boardingTime: String? = null,
    val departureTime: String? = null,
    val gate: String? = null,
    val seat: String? = null,
    val flightClass: String? = null,
    val sequenceNumber: String? = null,
    val pnrCode: String? = null,
    val membershipTier: String? = null,
    val balanceOrPoints: String? = null
) {
    fun toJsonString(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("type", type.name)
        obj.put("organizationName", organizationName)
        obj.put("description", description)
        obj.put("logoText", logoText ?: "")
        obj.put("backgroundColor", backgroundColor)
        obj.put("foregroundColor", foregroundColor)
        obj.put("labelColor", labelColor)

        fun listToJson(list: List<PassField>): JSONArray {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr
        }

        obj.put("headerFields", listToJson(headerFields))
        obj.put("primaryFields", listToJson(primaryFields))
        obj.put("secondaryFields", listToJson(secondaryFields))
        obj.put("auxiliaryFields", listToJson(auxiliaryFields))
        obj.put("backFields", listToJson(backFields))

        if (barcode != null) {
            obj.put("barcode", barcode.toJson())
        }
        if (transitType != null) {
            obj.put("transitType", transitType.name)
        }
        if (logoBitmapBase64 != null) obj.put("logoBitmapBase64", logoBitmapBase64)
        if (iconBitmapBase64 != null) obj.put("iconBitmapBase64", iconBitmapBase64)
        if (stripBitmapBase64 != null) obj.put("stripBitmapBase64", stripBitmapBase64)
        obj.put("importedAt", importedAt)

        // Specific fields
        if (passengerName != null) obj.put("passengerName", passengerName)
        if (flightNumber != null) obj.put("flightNumber", flightNumber)
        if (originAirport != null) obj.put("originAirport", originAirport)
        if (originCity != null) obj.put("originCity", originCity)
        if (destinationAirport != null) obj.put("destinationAirport", destinationAirport)
        if (destinationCity != null) obj.put("destinationCity", destinationCity)
        if (departureDate != null) obj.put("departureDate", departureDate)
        if (boardingTime != null) obj.put("boardingTime", boardingTime)
        if (departureTime != null) obj.put("departureTime", departureTime)
        if (gate != null) obj.put("gate", gate)
        if (seat != null) obj.put("seat", seat)
        if (flightClass != null) obj.put("flightClass", flightClass)
        if (sequenceNumber != null) obj.put("sequenceNumber", sequenceNumber)
        if (pnrCode != null) obj.put("pnrCode", pnrCode)
        if (membershipTier != null) obj.put("membershipTier", membershipTier)
        if (balanceOrPoints != null) obj.put("balanceOrPoints", balanceOrPoints)

        return obj.toString()
    }

    companion object {
        fun fromJsonString(jsonStr: String): WalletPass? {
            return try {
                val obj = JSONObject(jsonStr)
                val typeStr = obj.optString("type", PassType.GENERIC.name)
                val type = runCatching { PassType.valueOf(typeStr) }.getOrDefault(PassType.GENERIC)

                fun jsonToList(key: String): List<PassField> {
                    val arr = obj.optJSONArray(key) ?: return emptyList()
                    val list = mutableListOf<PassField>()
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i)
                        if (item != null) list.add(PassField.fromJson(item))
                    }
                    return list
                }

                val barcodeObj = obj.optJSONObject("barcode")
                val barcode = if (barcodeObj != null) PassBarcode.fromJson(barcodeObj) else null

                val transitStr = obj.optString("transitType", "")
                val transit = if (transitStr.isNotEmpty()) runCatching { TransitType.valueOf(transitStr) }.getOrNull() else null

                WalletPass(
                    id = obj.getString("id"),
                    type = type,
                    organizationName = obj.optString("organizationName", "Pass"),
                    description = obj.optString("description", ""),
                    logoText = obj.optString("logoText").takeIf { it.isNotEmpty() },
                    backgroundColor = obj.optLong("backgroundColor", 0xFF1C2B39),
                    foregroundColor = obj.optLong("foregroundColor", 0xFFFFFFFF),
                    labelColor = obj.optLong("labelColor", 0xFFA0AEC0),
                    headerFields = jsonToList("headerFields"),
                    primaryFields = jsonToList("primaryFields"),
                    secondaryFields = jsonToList("secondaryFields"),
                    auxiliaryFields = jsonToList("auxiliaryFields"),
                    backFields = jsonToList("backFields"),
                    barcode = barcode,
                    transitType = transit,
                    logoBitmapBase64 = obj.optString("logoBitmapBase64").takeIf { it.isNotEmpty() },
                    iconBitmapBase64 = obj.optString("iconBitmapBase64").takeIf { it.isNotEmpty() },
                    stripBitmapBase64 = obj.optString("stripBitmapBase64").takeIf { it.isNotEmpty() },
                    importedAt = obj.optLong("importedAt", System.currentTimeMillis()),
                    passengerName = obj.optString("passengerName").takeIf { it.isNotEmpty() },
                    flightNumber = obj.optString("flightNumber").takeIf { it.isNotEmpty() },
                    originAirport = obj.optString("originAirport").takeIf { it.isNotEmpty() },
                    originCity = obj.optString("originCity").takeIf { it.isNotEmpty() },
                    destinationAirport = obj.optString("destinationAirport").takeIf { it.isNotEmpty() },
                    destinationCity = obj.optString("destinationCity").takeIf { it.isNotEmpty() },
                    departureDate = obj.optString("departureDate").takeIf { it.isNotEmpty() },
                    boardingTime = obj.optString("boardingTime").takeIf { it.isNotEmpty() },
                    departureTime = obj.optString("departureTime").takeIf { it.isNotEmpty() },
                    gate = obj.optString("gate").takeIf { it.isNotEmpty() },
                    seat = obj.optString("seat").takeIf { it.isNotEmpty() },
                    flightClass = obj.optString("flightClass").takeIf { it.isNotEmpty() },
                    sequenceNumber = obj.optString("sequenceNumber").takeIf { it.isNotEmpty() },
                    pnrCode = obj.optString("pnrCode").takeIf { it.isNotEmpty() },
                    membershipTier = obj.optString("membershipTier").takeIf { it.isNotEmpty() },
                    balanceOrPoints = obj.optString("balanceOrPoints").takeIf { it.isNotEmpty() }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
