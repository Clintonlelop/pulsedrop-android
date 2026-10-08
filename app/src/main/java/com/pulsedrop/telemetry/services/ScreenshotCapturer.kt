package com.pulsedrop.telemetry.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

class ScreenshotCapturer(private val context: Context) {

    fun capture(resultCode: Int, data: Intent, callback: (File?) -> Unit) {
        val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        val virtualDisplay = projection.createVirtualDisplay(
            "PulseDropScreenshot",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, null
        )

        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage()
            if (image != null) {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "PulseDrop/Screenshots"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "screenshot_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()

                virtualDisplay.release()
                projection.stop()
                r.close()
                callback(file)
            }
        }, Handler(Looper.getMainLooper()))
    }
}
