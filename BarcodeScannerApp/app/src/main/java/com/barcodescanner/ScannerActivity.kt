package com.barcodescanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.barcodescanner.scanner.ScanActions
import com.barcodescanner.sync.SyncManager
import com.barcodescanner.network.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 扫码器 - 负责相机、条码识别、闪光灯、缩放
 * 扫码后通过 ScanActions 处理操作对话框
 */
class ScannerActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val DEDUP_INTERVAL_MS = 800L
        private const val AUTO_FLASH_THRESHOLD = 45
        private const val AUTO_FLASH_DEBOUNCE_MS = 2000L
    }

    // 用户信息
    private var userId = 0
    private var themeColor = "#2196F3"
    private var userName = ""
    private var isOffline = false
    private var deviceId = ""

    // 相机
    private lateinit var previewView: PreviewView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null

    // ML Kit
    private lateinit var barcodeScanner: BarcodeScanner
    private val isScanning = AtomicBoolean(true)

    // 闪光灯
    private var isFlashOn = false
    private var lastFlashToggleTime = 0L

    // 扫码防抖
    private var lastScannedCode = ""
    private var lastScanTime = 0L

    // UI
    private lateinit var scanOverlay: View
    private lateinit var resultCard: CardView
    private lateinit var resultFormat: TextView
    private lateinit var resultValue: TextView
    private lateinit var btnContinue: View
    private lateinit var btnFlashlight: ImageView
    private lateinit var hintText: TextView
    private lateinit var userNameDisplay: TextView
    private lateinit var recordsBtn: View
    private lateinit var galleryBtn: View
    private lateinit var exitBtn: View
    private lateinit var zoomHint: TextView

    // 缩放
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomRatio = 1.0f

    // 声音
    private var mediaPlayer: MediaPlayer? = null

    // 心跳
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatInterval = 15000L
    private var isHeartbeatRunning = false

    // 操作对话框
    private lateinit var scanActions: ScanActions

    // 同步管理器
    private lateinit var syncManager: SyncManager

    // 相册选择
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { scanImageFromGallery(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        userId = intent.getIntExtra("user_id", 0)
        themeColor = intent.getStringExtra("theme_color") ?: "#2196F3"
        userName = intent.getStringExtra("user_name") ?: ""
        isOffline = intent.getBooleanExtra("is_offline", false)
        deviceId = intent.getStringExtra("device_id") ?: ""

        // 初始化同步管理器
        syncManager = SyncManager(this)

        // 初始化操作对话框
        scanActions = ScanActions(this, object : ScanActions.ScanActionCallback {
            override fun resumeScanning() = this@ScannerActivity.resumeScanning()
            override fun showHint(text: String, color: Int) {
                hintText.text = text; hintText.setTextColor(color)
            }
            override fun isOffline() = this@ScannerActivity.isOffline
            override fun userId() = this@ScannerActivity.userId
            override fun deviceId() = this@ScannerActivity.deviceId
            override fun userName() = this@ScannerActivity.userName
            override fun saveOfflineRecord(barcode: String, address: String, weight: Double) =
                syncManager.saveOfflineRecord(barcode, address, weight)
        })

        if (!isOffline && userId > 0) {
            syncManager.syncAll(userId) { success, fail ->
                if (success > 0) {
                    Toast.makeText(this, "已同步 $success 条离线记录", Toast.LENGTH_SHORT).show()
                }
            }
        }

        initViews()
        initBarcodeScanner()
        initSound()
        initZoom()

        if (!isOffline && userId > 0) {
            startHeartbeat()
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    // ==================== 初始化 ====================

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        scanOverlay = findViewById(R.id.scanOverlay)
        resultCard = findViewById(R.id.resultCard)
        resultFormat = findViewById(R.id.resultFormat)
        resultValue = findViewById(R.id.resultValue)
        btnContinue = findViewById(R.id.btnContinue)
        btnFlashlight = findViewById(R.id.btnFlashlight)
        hintText = findViewById(R.id.hintText)
        userNameDisplay = findViewById(R.id.userNameDisplay)
        recordsBtn = findViewById(R.id.recordsBtn)
        galleryBtn = findViewById(R.id.galleryBtn)
        exitBtn = findViewById(R.id.exitBtn)
        zoomHint = findViewById(R.id.zoomHint)

        val color = Color.parseColor(themeColor)
        userNameDisplay.text = userName
        userNameDisplay.setTextColor(color)
        btnContinue.setBackgroundTintList(ColorStateList.valueOf(color))
        recordsBtn.setBackgroundTintList(ColorStateList.valueOf(color))
        galleryBtn.setBackgroundTintList(ColorStateList.valueOf(color))

        btnContinue.setOnClickListener { resumeScanning() }
        btnFlashlight.setOnClickListener { toggleFlashlight() }
        recordsBtn.setOnClickListener {
            startActivity(Intent(this, RecordsActivity::class.java).apply {
                putExtra("user_id", userId)
                putExtra("user_name", userName)
                putExtra("theme_color", themeColor)
            })
        }
        galleryBtn.setOnClickListener { pickImageLauncher.launch("image/*") }
        exitBtn.setOnClickListener { finish() }
    }

    private fun initBarcodeScanner() {
        val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODE_128, Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun initSound() {
        try {
            mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI)
            mediaPlayer?.setVolume(1.0f, 1.0f)
        } catch (_: Exception) {}
    }

    private fun initZoom() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentZoomRatio *= detector.scaleFactor
                currentZoomRatio = currentZoomRatio.coerceIn(1.0f, 5.0f)
                camera?.cameraControl?.setZoomRatio(currentZoomRatio)
                zoomHint.text = "%.1fx".format(currentZoomRatio)
                zoomHint.visibility = View.VISIBLE
                zoomHint.postDelayed({ zoomHint.visibility = View.GONE }, 1000)
                return true
            }
        })
    }

    // ==================== 相机 ====================

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        analysisExecutor = Executors.newSingleThreadExecutor()
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera()
            } catch (e: Exception) {
                Toast.makeText(this, "相机启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val previewUseCase = Preview.Builder().build()
        previewUseCase.setSurfaceProvider(previewView.surfaceProvider)

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val analysisUseCase = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(640, 480))
            .build()

        analysisUseCase.setAnalyzer(analysisExecutor!!) { imageProxy: ImageProxy ->
            analyzeImage(imageProxy)
        }
        imageAnalysis = analysisUseCase

        provider.unbindAll()
        camera = provider.bindToLifecycle(
            this as LifecycleOwner, cameraSelector, previewUseCase, analysisUseCase
        )
    }

    // ==================== 图像分析 ====================

    private fun analyzeImage(imageProxy: ImageProxy) {
        if (!isScanning.get()) {
            imageProxy.close()
            return
        }

        analyzeBrightness(imageProxy)

        @Suppress("UnsafeOptInUsageError")
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes: List<Barcode> ->
                    if (barcodes.isNotEmpty() && isScanning.get()) {
                        handleBarcodes(barcodes)
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    private fun analyzeBrightness(imageProxy: ImageProxy) {
        try {
            val buffer: ByteBuffer = imageProxy.planes[0].buffer
            val bufferSize = buffer.remaining()
            val bytes = ByteArray(bufferSize)
            buffer.get(bytes)

            var sum = 0L
            val step = 4
            var count = 0
            for (i in 0 until bytes.size step step) {
                sum += bytes[i].toInt() and 0xFF
                count++
            }

            val avgBrightness = sum / count
            val now = System.currentTimeMillis()

            if (avgBrightness < AUTO_FLASH_THRESHOLD && !isFlashOn &&
                now - lastFlashToggleTime > AUTO_FLASH_DEBOUNCE_MS) {
                camera?.cameraControl?.enableTorch(true)
                isFlashOn = true
                lastFlashToggleTime = now
                runOnUiThread { updateFlashlightUI(true) }
            } else if (avgBrightness > AUTO_FLASH_THRESHOLD + 20 && isFlashOn &&
                now - lastFlashToggleTime > AUTO_FLASH_DEBOUNCE_MS) {
                camera?.cameraControl?.enableTorch(false)
                isFlashOn = false
                lastFlashToggleTime = now
                runOnUiThread { updateFlashlightUI(false) }
            }
        } catch (_: Exception) {}
    }

    private fun handleBarcodes(barcodes: List<Barcode>) {
        val now = System.currentTimeMillis()
        val barcode = barcodes.firstOrNull() ?: return
        val rawValue = barcode.rawValue ?: return
        val formatName = getFormatName(barcode.format)

        if (rawValue == lastScannedCode && now - lastScanTime < DEDUP_INTERVAL_MS) {
            return
        }

        lastScannedCode = rawValue
        lastScanTime = now

        vibrate()
        playBeep()

        isScanning.set(false)

        runOnUiThread {
            scanActions.showOperationDialog(rawValue, formatName)
        }
    }

    // ==================== 闪光灯 ====================

    private fun toggleFlashlight() {
        camera?.let { cam ->
            try {
                val newState = !isFlashOn
                cam.cameraControl.enableTorch(newState)
                isFlashOn = newState
                updateFlashlightUI(newState)
            } catch (e: Exception) {
                Toast.makeText(this, "闪光灯控制失败", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "相机未就绪", Toast.LENGTH_SHORT).show()
    }

    private fun updateFlashlightUI(on: Boolean) {
        btnFlashlight.setColorFilter(if (on) Color.parseColor("#FFD600") else Color.WHITE)
    }

    // ==================== 恢复扫码 ====================

    private fun resumeScanning() {
        resultCard.visibility = View.GONE
        hintText.text = "将条形码对准框内"
        hintText.setTextColor(Color.WHITE)
        isScanning.set(true)
    }

    // ==================== 相册识别 ====================

    private fun scanImageFromGallery(uri: Uri) {
        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            hintText.text = "⏳ 正在识别图片..."
            hintText.setTextColor(Color.parseColor("#FFD600"))

            analysisExecutor?.execute {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes: List<Barcode> ->
                        if (barcodes.isNotEmpty()) {
                            handleBarcodes(barcodes)
                        } else {
                            runOnUiThread {
                                hintText.text = "未识别到条码"
                                hintText.setTextColor(Color.parseColor("#FF5252"))
                            }
                        }
                    }
                    .addOnFailureListener { e: Exception ->
                        runOnUiThread {
                            hintText.text = "识别失败: ${e.message}"
                            hintText.setTextColor(Color.parseColor("#FF5252"))
                        }
                    }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "图片读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 反馈 ====================

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    }

    private fun playBeep() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) seekTo(0)
                start()
            }
        } catch (_: Exception) {}
    }

    // ==================== 格式名称 ====================

    private fun getFormatName(format: Int?): String {
        return when (format) {
            Barcode.FORMAT_EAN_13 -> "EAN-13"
            Barcode.FORMAT_EAN_8 -> "EAN-8"
            Barcode.FORMAT_UPC_A -> "UPC-A"
            Barcode.FORMAT_UPC_E -> "UPC-E"
            Barcode.FORMAT_CODE_39 -> "Code 39"
            Barcode.FORMAT_CODE_93 -> "Code 93"
            Barcode.FORMAT_CODE_128 -> "Code 128"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_CODABAR -> "Codabar"
            Barcode.FORMAT_QR_CODE -> "QR Code"
            Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
            Barcode.FORMAT_PDF417 -> "PDF417"
            Barcode.FORMAT_AZTEC -> "Aztec"
            else -> "条码"
        }
    }

    // ==================== 心跳 ====================

    private fun startHeartbeat() {
        isHeartbeatRunning = true
        doHeartbeat()
    }

    private fun doHeartbeat() {
        if (!isHeartbeatRunning) return

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val ipAddress = getLocalIpAddress()

        ApiClient.sendHeartbeat(deviceId, deviceName, ipAddress, userName, object : ApiClient.ApiCallback {
            override fun onSuccess(data: JSONObject?) {}
            override fun onError(error: String?) {}
        })

        heartbeatHandler.postDelayed({ doHeartbeat() }, heartbeatInterval)
    }

    private fun stopHeartbeat() {
        isHeartbeatRunning = false
        heartbeatHandler.removeCallbacksAndMessages(null)
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    // ==================== 生命周期 ====================

    override fun onDestroy() {
        super.onDestroy()
        stopHeartbeat()
        barcodeScanner.close()
        analysisExecutor?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }
}
