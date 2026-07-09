package com.oiw.camera.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import java.io.File
import java.io.FileOutputStream

/**
 * RAW_SENSOR still -> DNG via the platform DngCreator (docs/CAMERA_PIPELINE.md #3). Only invoked
 * when CameraController.capabilities.hasRaw is true — the UI hides the RAW button otherwise, and
 * this class refuses (with a specific error) rather than writing a fake DNG if miscalled.
 */
class DngStillCaptor(private val characteristics: CameraCharacteristics) {

    /** @return the written file, or null after reporting via [onError]. */
    fun write(
        image: Image,
        result: TotalCaptureResult,
        outputDir: File,
        baseName: String,
        onError: (String) -> Unit,
    ): File? {
        val caps = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        if (caps == null || !caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
            onError("RAW capability not reported for this camera. DNG capture is unavailable.")
            return null
        }
        val outFile = File(outputDir, "$baseName.dng")
        return try {
            DngCreator(characteristics, result).use { dng ->
                FileOutputStream(outFile).use { fos ->
                    dng.writeImage(fos, image)
                    fos.fd.sync()
                }
            }
            outFile
        } catch (e: Exception) {
            onError("DNG write failed for ${outFile.name}: ${e.message}")
            outFile.delete()
            null
        }
    }
}
