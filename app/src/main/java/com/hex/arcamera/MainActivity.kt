package com.hex.arcamera

import ai.deepar.ar.*
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraSelector.Builder
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.hex.arcamera.databinding.ActivityMainBinding
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.VideoResult
import com.otaliastudios.cameraview.preview.SurfaceCameraPreview
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutionException

class MainActivity : AppCompatActivity(), AREventListener, SurfaceHolder.Callback {

    //DeepAR
    private var deepAR: DeepAR? = null
    private lateinit var effects: ArrayList<String>
    private var currentEffect = 0

    //CameraView
    private lateinit var cameraView: CameraView
    private lateinit var surfaceCamera: SurfaceCameraPreview
    private val defaultLensFacing = CameraSelector.LENS_FACING_FRONT
    private val lensFacing = defaultLensFacing
    private var useExternalCameraTexture = true
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var surfaceProvider: ARSurfaceProvider? = null
    
    //Image Buffers
    private lateinit var buffers: Array<ByteBuffer>
    private val NUMBER_OF_BUFFERS = 2
    private var buffersInitialized = false
    private var currentBuffer = 0

    //Main
    private lateinit var binding: ActivityMainBinding

    private var width = 0
    private var height = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupCameraView()
    }

    override fun onStart() {
    
        initializeDeepAR()
        initializeFilters()
        initializeView()

        setupCameraSetting()

        super.onStart()
    }

    private fun setupCameraView() {
        // Camera View instance with lifecycle

        cameraView = binding.cameraView
        cameraView.setLifecycleOwner(this)

        cameraView.addCameraListener(
                object : CameraListener() {

                    override fun onPictureTaken(result: PictureResult) {
                        super.onPictureTaken(result)
                    }

                    override fun onVideoTaken(result: VideoResult) {
                        super.onVideoTaken(result)
                    }
                }
        )
    }

    private fun initializeDeepAR() {
        deepAR = DeepAR(this)
        deepAR?.setLicenseKey(
                "6555b91cc4e31e52d71618674c9caf7f8222a97ba450bf445568ecd848473d83b63f414e29b3cf27"
        )
        deepAR?.initialize(this, this)
    }

    private fun initializeFilters() {
        effects = ArrayList()
        effects.add("none")
        effects.add("viking_helmet.deepar")
        effects.add("MakeupLook.deepar")
        effects.add("Split_View_Look.deepar")
        effects.add("Emotions_Exaggerator.deepar")
        effects.add("Emotion_Meter.deepar")
        effects.add("Stallone.deepar")
        effects.add("flower_face.deepar")
        effects.add("galaxy_background.deepar")
        effects.add("Humanoid.deepar")
        effects.add("Neon_Devil_Horns.deepar")
        effects.add("Ping_Pong.deepar")
        effects.add("Pixel_Hearts.deepar")
        effects.add("Snail.deepar")
        effects.add("Hope.deepar")
        effects.add("Vendetta_Mask.deepar")
        effects.add("Fire_Effect.deepar")
        effects.add("burning_effect.deepar")
        effects.add("Elephant_Trunk.deepar")
    }

    private fun initializeView() {
        surfaceCamera = SurfaceCameraPreview(this, cameraView)

        val arView = surfaceCamera.view.also { it.holder.addCallback(this) }

        arView.visibility = View.GONE
        arView.visibility = View.VISIBLE

        binding.changeMask.setOnClickListener { changeMask() }
    }

    private fun setupCameraSetting() {
        cameraView.open()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this.cameraView.context)
        cameraProviderFuture?.addListener(
                {
                    try {
                        val cameraProvider = cameraProviderFuture?.get()
                        if (cameraProvider != null) {
                            bindImageAnalysis(cameraProvider)
                        }
                    } catch (e: ExecutionException) {
                        e.printStackTrace()
                    }
                },
                ContextCompat.getMainExecutor(this)
        )
    }

    private fun getScreenOrientation(): Int {
        var rotation: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rotation = this.display?.rotation!!
        } else {
            @Suppress("DEPRECATION")
            rotation = windowManager.defaultDisplay.rotation
        }
        val dm = DisplayMetrics()

        @Suppress("DEPRECATION")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R){
            val display = this.display
            display?.getRealMetrics(dm)
        } else {
            @Suppress("DEPRECATION")
            val display = this.windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            display.getMetrics(dm)
        }

        width = dm.widthPixels
        height = dm.heightPixels
        // if the device's natural orientation is portrait:
        val orientation: Int = if (
            (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
            && height > width ||
            (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
            && width > height)
        {
            when (rotation) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else {
            when (rotation) {
                Surface.ROTATION_0 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Surface.ROTATION_180 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        return orientation
    }

    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider) {
        val cameraResolutionPreset = CameraResolutionPreset.P1920x1080
        var width: Int = 0
        var height: Int = 0
        var orientation = getScreenOrientation()

        if (orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
                orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ){
            width = cameraResolutionPreset.width
            height = cameraResolutionPreset.height
        } else {
            width = cameraResolutionPreset.height
            height = cameraResolutionPreset.width
        }

        val cameraResolution = Size(width, height)
        val cameraSelector = Builder().requireLensFacing(lensFacing).build()

        if (useExternalCameraTexture) {
            val preview: Preview = Preview.Builder().setTargetResolution(cameraResolution).build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            if (surfaceProvider == null){
                surfaceProvider = ARSurfaceProvider(this.cameraView.context, deepAR)
            }
            preview.setSurfaceProvider(surfaceProvider)
            surfaceProvider?.isMirror = lensFacing == CameraSelector.LENS_FACING_FRONT
        } else {
            val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(cameraResolution)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
            imageAnalysis.setAnalyzer(
                    ContextCompat.getMainExecutor(cameraView.context),
                    imageAnalyzer()
            )

            buffersInitialized = false
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        }
    }

    private fun initializeBuffers(size: Int) {
        this.buffers = arrayOf(ByteBuffer.allocate(NUMBER_OF_BUFFERS))
        for (i in 0..NUMBER_OF_BUFFERS) {
            this.buffers[i] = ByteBuffer.allocateDirect(size)
            this.buffers[i].order(ByteOrder.nativeOrder())
            this.buffers[i].position(0)
        }
    }


    private fun imageAnalyzer() =  ImageAnalysis.Analyzer() {
        @Override
        fun analyze(image: ImageProxy) {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            if (!buffersInitialized) {
                buffersInitialized = true
                initializeBuffers(ySize + uSize + vSize)
            }
            val byteData = ByteArray(ySize + uSize + vSize)

                    // U and V are swapped
                    yBuffer[byteData, 0, ySize]
                    vBuffer[byteData, ySize, vSize]
                    uBuffer[byteData, ySize + vSize, uSize]
                    buffers[currentBuffer].put(byteData)
                    buffers[currentBuffer].position(0)
                    if (deepAR != null) {
                        deepAR?.receiveFrame(
                                buffers[currentBuffer],
                                image.width,
                                image.height,
                                image.imageInfo.rotationDegrees,
                                lensFacing == CameraSelector.LENS_FACING_FRONT,
                                DeepARImageFormat.YUV_420_888,
                                image.planes[1].pixelStride
                        )
                    }
                    currentBuffer = (currentBuffer + 1) % NUMBER_OF_BUFFERS
                    image.close()
                }
            }

    private fun getFilterPath(filterName: String): String? {
        return if (filterName == "none") {
            null
        } else "file:///android_asset/$filterName"
    }

    private fun changeMask() {
        currentEffect = (currentEffect + 1) % effects.size
        Log.i("Effects", effects[currentEffect])
        deepAR?.switchEffect("effect", getFilterPath(effects[currentEffect]))
    }

    override fun onStop() {
//        recording = false
//        currentSwitchRecording = false
        var cameraProvider: ProcessCameraProvider? = null
        try {
            cameraProvider = cameraProviderFuture!!.get()
            cameraProvider.unbindAll()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        if (surfaceProvider != null) {
            surfaceProvider!!.stop()
            surfaceProvider = null
        }
        deepAR?.release()
        deepAR = null
        super.onStop()
        cameraView.close()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (surfaceProvider != null) {
            surfaceProvider!!.stop()
        }
        if (deepAR == null) {
            return
        }
        deepAR!!.setAREventListener(null)
        deepAR!!.release()
        cameraView.destroy()
    }

    override fun screenshotTaken(p0: Bitmap?) {}

    override fun videoRecordingStarted() {}

    override fun videoRecordingFinished() {}

    override fun videoRecordingFailed() {}

    override fun videoRecordingPrepared() {}

    override fun shutdownFinished() {}

    override fun initialized() {
        // Restore effect state after deepar release
        deepAR?.switchEffect("effect", getFilterPath(effects[currentEffect]))
    }

    override fun faceVisibilityChanged(p0: Boolean) {}

    override fun imageVisibilityChanged(p0: String?, p1: Boolean) {}

    override fun frameAvailable(p0: Image?) {}

    override fun error(p0: ARErrorType?, p1: String?) {}

    override fun effectSwitched(p0: String?) {}

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        deepAR?.setRenderSurface(holder.surface, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (deepAR != null) {
            deepAR?.setRenderSurface(null, 0, 0)
        }
    }
}
