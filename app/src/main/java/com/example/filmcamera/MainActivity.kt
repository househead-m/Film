package com.example.filmcamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import jp.co.cyberagent.android.gpuimage.GPUImageView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var previewContainer: FrameLayout
    private lateinit var gpuImageView: GPUImageView
    private lateinit var gridOverlay: GridOverlayView
    private lateinit var txtFilterName: TextView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnAspectRatio: Button
    private lateinit var btnImportPhoto: ImageButton
    private lateinit var btnEvMinus: Button
    private lateinit var btnEvPlus: Button
    private lateinit var txtEv: TextView
    private lateinit var reviewLayout: FrameLayout
    private lateinit var imgResult: ImageView
    private lateinit var txtReviewFilterName: TextView
    private lateinit var btnRetake: Button
    private lateinit var btnSave: Button

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isFrontCamera = false

    private val filters = LutPresets.getAll()
    private var currentFilterIndex = 0
    private val lutBitmapCache = mutableMapOf<Int, Bitmap>()

    private var originalCapturedBitmap: Bitmap? = null
    private var currentEvIndex = 0

    private lateinit var swipeGestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomRatio = 1f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "카메라 권한이 없으면 앱을 사용할 수 없어요", Toast.LENGTH_LONG).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) loadPickedImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewContainer = findViewById(R.id.previewContainer)
        gpuImageView = findViewById(R.id.gpuImageView)
        gridOverlay = findViewById(R.id.gridOverlay)
        txtFilterName = findViewById(R.id.txtFilterName)
        btnCapture = findViewById(R.id.btnCapture)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnSettings = findViewById(R.id.btnSettings)
        btnGallery = findViewById(R.id.btnGallery)
        btnAspectRatio = findViewById(R.id.btnAspectRatio)
        btnImportPhoto = findViewById(R.id.btnImportPhoto)
        btnEvMinus = findViewById(R.id.btnEvMinus)
        btnEvPlus = findViewById(R.id.btnEvPlus)
        txtEv = findViewById(R.id.txtEv)
        reviewLayout = findViewById(R.id.reviewLayout)
        imgResult = findViewById(R.id.imgResult)
        txtReviewFilterName = findViewById(R.id.txtReviewFilterName)
        btnRetake = findViewById(R.id.btnRetake)
        btnSave = findViewById(R.id.btnSave)

        cameraExecutor = Executors.newSingleThreadExecutor()

        applyFilterToGpuImageView(currentFilterIndex)
        updateFilterLabel()
        updateAspectRatioUi()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnCapture.setOnClickListener { takePhoto() }
        btnSwitchCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            startCamera()
        }
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        btnAspectRatio.setOnClickListener { cycleAspectRatio() }
        btnImportPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        btnEvMinus.setOnClickListener { adjustEv(-1) }
        btnEvPlus.setOnClickListener { adjustEv(1) }
        btnRetake.setOnClickListener { showCameraScreen() }
        btnSave.setOnClickListener { saveFilteredPhoto() }

        setupGestureDetectors()
    }

    override fun onResume() {
        super.onResume()
        gridOverlay.visibility = if (SettingsPrefs.isGridEnabled(this)) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    // ---------- 제스처 (스와이프로 필터 전환, 핀치로 줌) ----------

    private fun setupGestureDetectors() {
        swipeGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (abs(dx) > 120 && abs(velocityX) > 200) {
                    if (dx < 0) nextFilter() else previousFilter()
                    return true
                }
                return false
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val zoomState = cam.cameraInfo.zoomState.value ?: return false
                val newRatio = (zoomState.zoomRatio * detector.scaleFactor)
                    .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                cam.cameraControl.setZoomRatio(newRatio)
                currentZoomRatio = newRatio
                return true
            }
        })

        // 항상 true를 반환해야 이후 move/up 이벤트도 계속 전달됨 (안 그러면 스와이프가 씹힘)
        gpuImageView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (!scaleGestureDetector.isInProgress) {
                swipeGestureDetector.onTouchEvent(event)
            }
            true
        }

        imgResult.setOnTouchListener { _, event ->
            swipeGestureDetector.onTouchEvent(event)
            true
        }
    }

    // ---------- 필터 ----------

    private fun nextFilter() {
        currentFilterIndex = (currentFilterIndex + 1) % filters.size
        applyFilterToGpuImageView(currentFilterIndex)
        updateFilterLabel()
        if (reviewLayout.visibility == FrameLayout.VISIBLE) applyFilterToReviewImage()
    }

    private fun previousFilter() {
        currentFilterIndex = (currentFilterIndex - 1 + filters.size) % filters.size
        applyFilterToGpuImageView(currentFilterIndex)
        updateFilterLabel()
        if (reviewLayout.visibility == FrameLayout.VISIBLE) applyFilterToReviewImage()
    }

    private fun getLutBitmap(resId: Int): Bitmap {
        return lutBitmapCache.getOrPut(resId) { BitmapFactory.decodeResource(resources, resId) }
    }

    private fun applyFilterToGpuImageView(index: Int) {
        val preset = filters[index]
        val lutFilter = LutFilter(preset.lutSize)
        gpuImageView.filter = lutFilter
        lutFilter.setBitmap(getLutBitmap(preset.drawableResId))
        gpuImageView.requestRender()
    }

    private fun updateFilterLabel() {
        txtFilterName.text = filters[currentFilterIndex].name
    }

    // ---------- 화면비율 ----------

    private fun cycleAspectRatio() {
        val current = SettingsPrefs.getAspectRatio(this)
        val next = (current + 1) % 3
        SettingsPrefs.setAspectRatio(this, next)
        updateAspectRatioUi()
    }

    private fun updateAspectRatioUi() {
        val ratio = SettingsPrefs.getAspectRatio(this)
        val lp = previewContainer.layoutParams as ConstraintLayout.LayoutParams

        when (ratio) {
            SettingsPrefs.ASPECT_16_9 -> {
                lp.height = 0
                lp.dimensionRatio = "9:16"
                lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                btnAspectRatio.text = "16:9"
            }
            SettingsPrefs.ASPECT_1_1 -> {
                lp.height = 0
                lp.dimensionRatio = "1:1"
                lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                btnAspectRatio.text = "1:1"
            }
            else -> {
                lp.height = 0
                lp.dimensionRatio = null
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                btnAspectRatio.text = "FULL"
            }
        }
        previewContainer.layoutParams = lp
    }

    private fun cropToAspect(bitmap: Bitmap): Bitmap {
        val ratio = SettingsPrefs.getAspectRatio(this)
        if (ratio == SettingsPrefs.ASPECT_FULL) return bitmap

        val targetRatio = if (ratio == SettingsPrefs.ASPECT_16_9) 9f / 16f else 1f
        val currentRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        return if (currentRatio > targetRatio) {
            val newWidth = (bitmap.height * targetRatio).toInt()
            val x = ((bitmap.width - newWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, x, 0, newWidth.coerceAtMost(bitmap.width - x), bitmap.height)
        } else {
            val newHeight = (bitmap.width / targetRatio).toInt()
            val y = ((bitmap.height - newHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, 0, y, bitmap.width, newHeight.coerceAtMost(bitmap.height - y))
        }
    }

    // ---------- 밝기(EV) ----------

    private fun adjustEv(direction: Int) {
        val cam = camera ?: return
        val range = cam.cameraInfo.exposureState.exposureCompensationRange
        val newIndex = (currentEvIndex + direction).coerceIn(range.lower, range.upper)
        currentEvIndex = newIndex
        cam.cameraControl.setExposureCompensationIndex(newIndex)

        val step = cam.cameraInfo.exposureState.exposureCompensationStep.toFloat()
        txtEv.text = String.format("EV %.1f", newIndex * step)
    }

    // ---------- 카메라 ----------

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder().build()

            // 실시간 프리뷰는 해상도를 낮춰서 부드럽게 (저장되는 사진은 별도로 고화질 유지됨)
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(960, 540),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val bitmap = analysisFrameToBitmap(imageProxy)
                        imageProxy.close()
                        runOnUiThread {
                            gpuImageView.gpuImage.setImage(bitmap)
                        }
                    }
                }

            val cameraSelector = if (isFrontCamera)
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageCapture, imageAnalysis
                )
                currentEvIndex = 0
                currentZoomRatio = 1f
            } catch (exc: Exception) {
                Toast.makeText(this, "카메라를 여는 데 실패했어요: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // 실시간 프리뷰 프레임(RGBA)을 Bitmap으로 변환
    private fun analysisFrameToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val rawBitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        rawBitmap.copyPixelsFromBuffer(buffer)

        val cropped = if (rowPadding == 0) {
            rawBitmap
        } else {
            Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height)
        }

        val rotation = image.imageInfo.rotationDegrees
        return if (rotation != 0 || isFrontCamera) {
            val matrix = Matrix().apply {
                postRotate(rotation.toFloat())
                if (isFrontCamera) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
        } else cropped
    }

    // 실제 촬영된 사진(JPEG 압축 데이터)을 Bitmap으로 변환 - 위 함수와는 완전히 다른 형식이라 별도 처리 필요
    private fun capturedJpegToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("사진 디코딩 실패")

        val rotation = image.imageInfo.rotationDegrees
        return if (rotation != 0 || isFrontCamera) {
            val matrix = Matrix().apply {
                postRotate(rotation.toFloat())
                if (isFrontCamera) postScale(-1f, 1f)
            }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else decoded
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = capturedJpegToBitmap(image)
                        image.close()
                        val cropped = cropToAspect(bitmap)
                        runOnUiThread {
                            originalCapturedBitmap = cropped
                            showReviewScreen()
                        }
                    } catch (e: Exception) {
                        image.close()
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "사진 처리 실패: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "촬영 실패: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // ---------- 갤러리에서 사진 불러오기 ----------

    private fun loadPickedImage(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(input)
            input?.close()
            if (bitmap == null) {
                Toast.makeText(this, "이미지를 불러오지 못했어요", Toast.LENGTH_SHORT).show()
                return
            }
            originalCapturedBitmap = cropToAspect(bitmap)
            showReviewScreen()
        } catch (e: Exception) {
            Toast.makeText(this, "이미지를 불러오지 못했어요: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- 리뷰 화면 ----------

    private fun showReviewScreen() {
        reviewLayout.visibility = FrameLayout.VISIBLE
        applyFilterToReviewImage()
    }

    private fun showCameraScreen() {
        reviewLayout.visibility = FrameLayout.GONE
    }

    private fun applyFilterToReviewImage() {
        val bitmap = originalCapturedBitmap ?: return
        val preset = filters[currentFilterIndex]
        val lutBitmap = getLutBitmap(preset.drawableResId)
        val filtered = LutBaker.apply(bitmap, lutBitmap, preset.lutSize.toInt())
        imgResult.setImageBitmap(filtered)
        txtReviewFilterName.text = preset.name
    }

    private fun saveFilteredPhoto() {
        val bitmap = originalCapturedBitmap ?: return
        val preset = filters[currentFilterIndex]
        val lutBitmap = getLutBitmap(preset.drawableResId)
        val filteredBitmap = LutBaker.apply(bitmap, lutBitmap, preset.lutSize.toInt())

        val filename = "film_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FilmCamera")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                filteredBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            Toast.makeText(this, "갤러리에 저장했어요!", Toast.LENGTH_SHORT).show()
            showCameraScreen()
        } else {
            Toast.makeText(this, "저장에 실패했어요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
