package com.hex.arcamera

import ai.deepar.ar.*
import android.graphics.Bitmap
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.ImageButton
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutionException


class MainActivity : AppCompatActivity(), AREventListener, SurfaceHolder.Callback {

    private lateinit var effects: ArrayList<String>
    private lateinit var binding: ActivityMainBinding
    private lateinit var deepAR: DeepAR
    private lateinit var cameraView: CameraView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var surfaceProvider: ARSurfaceProvider

    private lateinit var buffers: Array<ByteBuffer>
    private var currentBuffer = 0
    private var buffersInitialized = false
    private val NUMBER_OF_BUFFERS = 2

    private val defaultLensFacing = CameraSelector.LENS_FACING_FRONT
    private val lensFacing = defaultLensFacing
    private val useExternalCameraTexture = true

    private val currentMask = 0
    private var currentEffect = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {

        setupCameraView()

        initializeDeepAR()
        initializeFilters()
        initializeView()

        setupCameraSetting()

        initialized()

        super.onStart()
    }

    private fun setupCameraView() {
        //Camera View instance with lifecycle
        cameraView = binding.cameraView
        cameraView.setLifecycleOwner(this)

        cameraView.addCameraListener(object : CameraListener() {

            override fun onPictureTaken(result: PictureResult){
                super.onPictureTaken(result)
            }

            override fun onVideoTaken(result: VideoResult){
                super.onVideoTaken(result)
            }
        })
    }


    private fun initializeDeepAR() {
        deepAR = DeepAR(this)
        deepAR.setLicenseKey("6555b91cc4e31e52d71618674c9caf7f8222a97ba450bf445568ecd848473d83b63f414e29b3cf27")
        deepAR.initialize(this, this)
    }

    private fun initializeFilters(){
        effects = ArrayList()
        effects.add("none")
        effects.add("viking_helmet.deepar")
    }

    private fun initializeView() {
        var nextMask: ImageButton = binding.changeMask
        var arView: SurfaceView = binding.surface.also {
            it.holder.addCallback(this)
        }

        arView.visibility = View.GONE
        arView.visibility = View.VISIBLE

        binding.changeMask.setOnClickListener {
            changeMask()
        }
    }

    private fun setupCameraSetting(){
        cameraView.open()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this.cameraView.context)
        cameraProviderFuture.addListener( {
            @Override
            fun run(){
                try{
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    bindImageAnalysis(cameraProvider)
                } catch (e: ExecutionException){
                    e.printStackTrace()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider){
        val cameraResolutionPreset = CameraResolutionPreset.P1920x1080;
        var cameraSelector = Builder().requireLensFacing(defaultLensFacing).build()

        val cameraResolution = Size(cameraResolutionPreset.width, cameraResolutionPreset.height)

        if (useExternalCameraTexture){
            var preview: Preview = Preview.Builder()
                .setTargetResolution(cameraResolution)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this,cameraSelector,preview)
            if (surfaceProvider == null) run {
                surfaceProvider = ARSurfaceProvider(this, deepAR)
            }
            preview.setSurfaceProvider(surfaceProvider)
            surfaceProvider.isMirror = lensFacing == CameraSelector.LENS_FACING_FRONT
        }else{
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(cameraResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(cameraView.context), imageAnalyzer())
            buffersInitialized = false
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        }
    }

    private fun initializeBuffers(size: Int) {
       this.buffers = arrayOf(ByteBuffer.allocate(NUMBER_OF_BUFFERS))
        for (i in 0..NUMBER_OF_BUFFERS){
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

            //U and V are swapped
            yBuffer[byteData, 0, ySize]
            vBuffer[byteData, ySize, vSize]
            uBuffer[byteData, ySize + vSize, uSize]
            buffers[currentBuffer].put(byteData)
            buffers[currentBuffer].position(0)
            if (deepAR != null) {
                deepAR.receiveFrame(
                    buffers[currentBuffer],
                    image.width, image.height,
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
        deepAR.switchEffect("effect", getFilterPath(effects[currentEffect]))
    }

    override fun screenshotTaken(p0: Bitmap?) {
    }

    override fun videoRecordingStarted() {
    }

    override fun videoRecordingFinished() {
    }

    override fun videoRecordingFailed() {
    }

    override fun videoRecordingPrepared() {
    }

    override fun shutdownFinished() {
    }

    override fun initialized() {

        // Restore effect state after deepar release
        deepAR.switchEffect("effect", getFilterPath(effects[currentEffect]))
    }

    override fun faceVisibilityChanged(p0: Boolean) {
    }

    override fun imageVisibilityChanged(p0: String?, p1: Boolean) {
    }

    override fun frameAvailable(p0: Image?) {
    }

    override fun error(p0: ARErrorType?, p1: String?) {
    }

    override fun effectSwitched(p0: String?) {
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        deepAR.setRenderSurface(holder.surface,width,height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (deepAR != null) {
            deepAR.setRenderSurface(null, 0, 0)
        }
    }
}



