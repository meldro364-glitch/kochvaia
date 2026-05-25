package com.kochvaia.app.ui.kid

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX ImageAnalysis analyzer that pipes frames through ML Kit's barcode
 * scanner. Calls onCode with each detected QR payload. The analyzer dedupes
 * trivially: we don't fire onCode again until reset() is called by the screen
 * (typically after navigating away or showing an error).
 */
class QrBarcodeAnalyzer(
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @Volatile private var consumed = false

    fun reset() { consumed = false }

    override fun close() { scanner.close() }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (consumed) {
            image.close()
            return
        }
        val media = image.image ?: run { image.close(); return }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val payload = barcodes.firstOrNull()?.rawValue
                if (!payload.isNullOrBlank() && !consumed) {
                    consumed = true
                    onCode(payload)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}
