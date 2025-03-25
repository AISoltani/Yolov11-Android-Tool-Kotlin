package com.surendramaran.yolov11instancesegmentation

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.surendramaran.yolov11instancesegmentation.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), InstanceSegmentation.InstanceSegmentationListener {

    private lateinit var binding: ActivityMainBinding

    private lateinit var instanceSegmentation: InstanceSegmentation
    private lateinit var drawImages: DrawImages
    private lateinit var previewView: PreviewView

    // Variable for accessing the camera to control zoom.
    private lateinit var camera: Camera

    private var segmentedBitmap: Bitmap? = null
    private var originalBitmap: Bitmap? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        previewView = binding.previewView

        // Check permissions and later start camera.
        checkPermission()

        drawImages = DrawImages(applicationContext)

        instanceSegmentation = InstanceSegmentation(
            context = applicationContext,
            modelPath = "yolo11n-seg_float16.tflite",
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            }
        )

        // Initialize executor for camera operations.
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up the shutter button to save images.
        binding.btnShutter.setOnClickListener {
            saveCombinedImage()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Set aspect ratio to 4:3.
            val aspectRatio = AspectRatio.RATIO_4_3

            // Preview Use Case.
            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            // Image Analysis Use Case.
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build().also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // Bind the camera use cases and capture the Camera instance for zoom control.
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                // Now that the camera is bound, set up the zoom slider.
                setupZoomSlider()
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Sets up the zoom slider (SeekBar) to control the camera zoom.
     * The slider is set to start with its minimum value.
     */
    private fun setupZoomSlider() {
        val seekBar = binding.zoomSeekBar

        // Set default progress to minimum (0).
        seekBar.progress = 0

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Get the current zoom state from CameraX.
                val zoomState = camera.cameraInfo.zoomState.value ?: return
                val minZoom = zoomState.minZoomRatio    // Typically 1.0
                val maxZoom = zoomState.maxZoomRatio

                // Map progress (0 to seekBar.max) to (minZoom to maxZoom).
                val newZoomRatio = minZoom + (progress.toFloat() / seekBar.max) * (maxZoom - minZoom)
                camera.cameraControl.setZoomRatio(newZoomRatio)
                Log.d("CameraZoom", "Updated zoom ratio: $newZoomRatio")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Optional: actions when the user starts interacting.
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Optional: actions when the user stops interacting.
            }
        })

        // Immediately update the camera's zoom based on the default (minimum) slider value.
        val currentProgress = seekBar.progress
        val zoomState = camera.cameraInfo.zoomState.value
        if (zoomState != null) {
            val minZoom = zoomState.minZoomRatio
            val maxZoom = zoomState.maxZoomRatio
            val newZoomRatio = minZoom + (currentProgress.toFloat() / seekBar.max) * (maxZoom - minZoom)
            camera.cameraControl.setZoomRatio(newZoomRatio)
            Log.d("CameraZoom", "Initialized zoom ratio: $newZoomRatio")
        }
    }

    private fun saveCombinedImage() {
        val original = originalBitmap ?: run {
            Log.e("CameraX", "No original bitmap available!")
            Toast.makeText(this, "No original frame to save.", Toast.LENGTH_SHORT).show()
            return
        }

        val segmented = segmentedBitmap ?: run {
            Log.e("CameraX", "No segmented bitmap available!")
            Toast.makeText(this, "No segmentation result to save.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Combine the original and segmented bitmaps.
            val combinedBitmap =
                Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combinedBitmap)
            canvas.drawBitmap(original, 0f, 0f, null)
            canvas.drawBitmap(segmented, 0f, 0f, null)

            // Save the combined bitmap to the Download directory.
            val photoDirectory = File("/storage/emulated/0/Download").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val photoFile = File(photoDirectory, "combined_image_$timestamp.jpg")

            FileOutputStream(photoFile).use { out ->
                combinedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
            }

            addImageToGallery(photoFile)

            Toast.makeText(
                this@MainActivity,
                "Combined Image Saved: ${photoFile.absolutePath}",
                Toast.LENGTH_SHORT
            ).show()
            Log.d("CameraX", "Combined Image saved successfully.")
        } catch (e: Exception) {
            Log.e("CameraX", "Error saving combined image: ${e.message}", e)
        }
    }

    private fun addImageToGallery(file: File) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            Log.d("CameraX", "Image added to gallery: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("CameraX", "Error adding image to gallery: ${e.message}", e)
        }
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<SegmentationResult>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        if (results.isEmpty()) {
            Log.e("Segmentation", "No results detected!")
            runOnUiThread {
                segmentedBitmap = null
                Toast.makeText(this, "No objects detected for segmentation", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val image = drawImages.invoke(results)
        Log.d("Segmentation", "Segmentation successful, results applied to bitmap.")

        runOnUiThread {
            segmentedBitmap = image
            binding.tvPreprocess.text = preProcessTime.toString()
            binding.tvInference.text = interfaceTime.toString()
            binding.tvPostprocess.text = postProcessTime.toString()
            binding.ivTop.setImageBitmap(image)
        }
    }

    override fun onEmpty() {
        runOnUiThread {
            binding.ivTop.setImageResource(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceSegmentation.close()
        cameraExecutor.shutdown()
    }

    private fun checkPermission() = lifecycleScope.launch(Dispatchers.IO) {
        val isGranted = REQUIRED_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        if (map.all { it.value }) {
            startCamera()
        } else {
            Toast.makeText(baseContext, "Permission required", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )
            Log.d("ImageAnalyzer", "Captured frame analyzed. Passing to segmentation model.")
            originalBitmap = rotatedBitmap
            instanceSegmentation.invoke(rotatedBitmap)
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
        }
    }
}