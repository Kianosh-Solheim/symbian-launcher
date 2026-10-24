package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap
import java.util.EnumSet
import java.util.UUID

object BarcodeUtils {

    fun fromZxing(f: BarcodeFormat): BarcodeFormatType = when (f) {
        BarcodeFormat.QR_CODE -> BarcodeFormatType.QR_CODE
        BarcodeFormat.AZTEC -> BarcodeFormatType.AZTEC
        BarcodeFormat.PDF_417 -> BarcodeFormatType.PDF_417
        BarcodeFormat.CODE_128 -> BarcodeFormatType.CODE_128
        BarcodeFormat.CODE_39 -> BarcodeFormatType.CODE_39
        BarcodeFormat.EAN_13 -> BarcodeFormatType.EAN_13
        BarcodeFormat.EAN_8 -> BarcodeFormatType.EAN_8
        BarcodeFormat.UPC_A -> BarcodeFormatType.UPC_A
        BarcodeFormat.UPC_E -> BarcodeFormatType.UPC_E
        BarcodeFormat.ITF -> BarcodeFormatType.ITF
        BarcodeFormat.CODABAR -> BarcodeFormatType.CODABAR
        BarcodeFormat.CODE_93 -> BarcodeFormatType.CODE_93
        BarcodeFormat.DATA_MATRIX -> BarcodeFormatType.DATA_MATRIX
        else -> BarcodeFormatType.UNKNOWN
    }

    /**
     * Generates a high-contrast Bitmap for a barcode message & format
     */
    fun generateBarcodeBitmap(
        message: String,
        format: BarcodeFormatType,
        width: Int = 600,
        height: Int = 300
    ): Bitmap? {
        if (message.isBlank()) return null
        return try {
            val zxingFormat = when (format) {
                BarcodeFormatType.QR_CODE -> BarcodeFormat.QR_CODE
                BarcodeFormatType.AZTEC -> BarcodeFormat.AZTEC
                BarcodeFormatType.PDF_417 -> BarcodeFormat.PDF_417
                BarcodeFormatType.CODE_128 -> BarcodeFormat.CODE_128
                BarcodeFormatType.CODE_39 -> BarcodeFormat.CODE_39
                BarcodeFormatType.EAN_13 -> BarcodeFormat.EAN_13
                BarcodeFormatType.EAN_8 -> BarcodeFormat.EAN_8
                BarcodeFormatType.UPC_A -> BarcodeFormat.UPC_A
                BarcodeFormatType.UPC_E -> BarcodeFormat.UPC_E
                BarcodeFormatType.ITF -> BarcodeFormat.ITF
                BarcodeFormatType.CODABAR -> BarcodeFormat.CODABAR
                BarcodeFormatType.CODE_93 -> BarcodeFormat.CODE_93
                BarcodeFormatType.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
                else -> BarcodeFormat.QR_CODE
            }

            val targetWidth = if (zxingFormat == BarcodeFormat.QR_CODE || zxingFormat == BarcodeFormat.AZTEC || zxingFormat == BarcodeFormat.DATA_MATRIX) {
                height.coerceAtLeast(width)
            } else {
                width
            }
            val targetHeight = if (zxingFormat == BarcodeFormat.QR_CODE || zxingFormat == BarcodeFormat.AZTEC || zxingFormat == BarcodeFormat.DATA_MATRIX) {
                targetWidth
            } else {
                height
            }

            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = 1
            hints[EncodeHintType.CHARACTER_SET] = "ISO-8859-1"

            val bitMatrix = MultiFormatWriter().encode(message, zxingFormat, targetWidth, targetHeight, hints)
            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Scans and decodes a barcode from a Bitmap image (e.g. uploaded screenshot or camera capture)
     */
    fun decodeBarcodeFromBitmap(bitmap: Bitmap): ScannedBarcodeResult? {
        // Try original orientation first
        val firstAttempt = decodeSingleBitmap(bitmap)
        if (firstAttempt != null) return firstAttempt

        // Try rotating by 90, 180, and 270 degrees if not detected initially
        val matrix = android.graphics.Matrix()
        for (angle in listOf(90f, 180f, 270f)) {
            matrix.setRotate(angle)
            val rotatedBitmap = try {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } catch (e: Exception) {
                null
            }
            if (rotatedBitmap != null) {
                val rotatedResult = decodeSingleBitmap(rotatedBitmap)
                if (rotatedResult != null) return rotatedResult
            }
        }
        return null
    }

    private fun decodeSingleBitmap(bitmap: Bitmap): ScannedBarcodeResult? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
            hints[DecodeHintType.POSSIBLE_FORMATS] = EnumSet.allOf(BarcodeFormat::class.java)
            hints[DecodeHintType.TRY_HARDER] = true

            val reader = MultiFormatReader()
            reader.setHints(hints)
            val result = reader.decodeWithState(binaryBitmap)

            val formatType = when (result.barcodeFormat) {
                BarcodeFormat.QR_CODE -> BarcodeFormatType.QR_CODE
                BarcodeFormat.AZTEC -> BarcodeFormatType.AZTEC
                BarcodeFormat.PDF_417 -> BarcodeFormatType.PDF_417
                BarcodeFormat.CODE_128 -> BarcodeFormatType.CODE_128
                BarcodeFormat.CODE_39 -> BarcodeFormatType.CODE_39
                else -> fromZxing(result.barcodeFormat)
            }

            ScannedBarcodeResult(
                rawText = result.text,
                format = formatType
            )
        } catch (e: Exception) {
            null
        }
    }

    data class ScannedBarcodeResult(
        val rawText: String,
        val format: BarcodeFormatType
    )

    /**
     * Converts a scanned barcode result into a WalletPass (handles airline IATA BCBP or loyalty card)
     */
    fun createPassFromScannedBarcode(result: ScannedBarcodeResult, customName: String? = null): WalletPass {
        val raw = result.rawText.trim()
        val isAirline = raw.startsWith("M1")

        val passBarcode = PassBarcode(
            message = raw,
            format = result.format,
            altText = if (isAirline) "E-TICKET / BOARDING PASS" else raw
        )

        if (isAirline) {
            val iata = PkPassParser.parseIataBcbp(raw)
            if (iata != null) {
                return WalletPass(
                    id = UUID.randomUUID().toString(),
                    type = PassType.BOARDING_PASS,
                    organizationName = customName ?: (if (iata.carrier.isNotEmpty()) "${iata.carrier} Airlines" else "Airline"),
                    description = "Boarding Pass - ${iata.flightNumber}",
                    backgroundColor = 0xFF0D2538,
                    foregroundColor = 0xFFFFFFFF,
                    labelColor = 0xFF81D4FA,
                    transitType = TransitType.AIR,
                    passengerName = iata.passengerName,
                    flightNumber = iata.flightNumber,
                    originAirport = iata.originAirport,
                    destinationAirport = iata.destinationAirport,
                    departureDate = "Flight day ${iata.julianDate}",
                    seat = iata.seat.ifEmpty { "Free Seating" },
                    flightClass = when (iata.flightClass.uppercase()) {
                        "F" -> "First Class"
                        "J", "C" -> "Business Class"
                        "W" -> "Premium Economy"
                        else -> "Economy"
                    },
                    sequenceNumber = iata.sequenceNumber,
                    pnrCode = iata.pnr,
                    barcode = passBarcode,
                    primaryFields = listOf(
                        PassField("origin", "FROM", iata.originAirport),
                        PassField("destination", "TO", iata.destinationAirport)
                    ),
                    secondaryFields = listOf(
                        PassField("passenger", "PASSENGER", iata.passengerName),
                        PassField("flight", "FLIGHT", iata.flightNumber)
                    ),
                    auxiliaryFields = listOf(
                        PassField("seat", "SEAT", iata.seat.ifEmpty { "Auto" }),
                        PassField("class", "CLASS", iata.flightClass),
                        PassField("pnr", "BOOKING REF", iata.pnr)
                    )
                )
            }
        }

        // Generic loyalty or scanned card
        return WalletPass(
            id = UUID.randomUUID().toString(),
            type = if (!result.format.isSquare && result.format != BarcodeFormatType.PDF_417) PassType.LOYALTY_CARD else PassType.GENERIC,
            organizationName = customName ?: "Loyalty / Scanned Pass",
            description = "Imported Barcode Pass",
            backgroundColor = 0xFF2C3E50,
            foregroundColor = 0xFFFFFFFF,
            labelColor = 0xFFBDC3C7,
            barcode = passBarcode,
            primaryFields = listOf(
                PassField("card_number", "NUMBER", raw.take(24))
            ),
            secondaryFields = listOf(
                PassField("format", "TYPE", result.format.name),
                PassField("status", "STATUS", "Active")
            )
        )
    }
}
