package com.zahxrun.ttslive.stream

import android.app.Presentation
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pedro.encoder.input.sources.OrientationConfig
import com.pedro.encoder.input.sources.OrientationForced
import com.pedro.encoder.input.sources.video.VideoSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sumber video RootEncoder yang menggambar sebuah Composable ke encoder.
 *
 * Caranya: buat VirtualDisplay milik aplikasi sendiri yang outputnya langsung ke Surface encoder,
 * lalu tampilkan ComposeView di dalam Presentation pada display itu. Layar fisik tidak terlibat,
 * jadi aplikasi lain bebas dipakai di depan.
 */
class CanvasVideoSource(
    private val context: Context,
    private val content: @Composable () -> Unit,
) : VideoSource() {

    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null
    private var presentation: Presentation? = null
    private var owner: ComposeOwner? = null

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        require(width % 2 == 0 && height % 2 == 0) { "width dan height harus genap" }
        return true
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        this.surfaceTexture = surfaceTexture
        if (isRunning()) return
        surfaceTexture.setDefaultBufferSize(width, height)
        val s = Surface(surfaceTexture)
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val created: VirtualDisplay? = try {
            dm.createVirtualDisplay(
                "ttslive-canvas", width, height, DPI, s,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            )
        } catch (e: Exception) {
            s.release()
            throw e
        }
        if (created == null) {
            s.release()
            throw IllegalStateException("Gagal membuat virtual display")
        }
        val vd: VirtualDisplay = created
        surface = s
        virtualDisplay = vd

        var error: Throwable? = null
        onMain {
            try {
                showPresentation(vd.display)
            } catch (t: Throwable) {
                error = t
            }
        }
        error?.let {
            stop()
            throw it
        }
    }

    private fun showPresentation(display: Display) {
        val o = ComposeOwner()
        val p = Presentation(context, display)
        // Owner harus dipasang di decorView sebelum ComposeView masuk window.
        val decor = p.window!!.decorView
        decor.setViewTreeLifecycleOwner(o)
        decor.setViewTreeSavedStateRegistryOwner(o)
        val view = ComposeView(p.context).apply { setContent(content) }
        p.setContentView(view)
        p.show()
        owner = o
        presentation = p
    }

    override fun stop() {
        onMain {
            runCatching { presentation?.dismiss() }
            presentation = null
            owner?.destroy()
            owner = null
        }
        virtualDisplay?.release()
        virtualDisplay = null
        surface?.release()
        surface = null
    }

    override fun release() {}

    override fun isRunning(): Boolean = virtualDisplay != null

    override fun getOrientationConfig(): OrientationConfig {
        val isPortrait = (rotation == 90 || rotation == 270) != (height > width)
        return OrientationConfig(
            cameraOrientation = 0,
            isPortrait = isPortrait,
            forced = OrientationForced.NONE,
        )
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                try { block() } finally { latch.countDown() }
            }
            latch.await(5, TimeUnit.SECONDS)
        }
    }

    private class ComposeOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    companion object {
        /** 720x1280 pada 280 dpi = sekitar 411 x 731 dp, mirip ukuran HP biasa. */
        const val DPI = 280
    }
}
