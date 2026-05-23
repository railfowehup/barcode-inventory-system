package com.barcodescanner.base

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
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
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.barcodescanner.R
import com.barcodescanner.network.ApiClient
import com.barcodescanner.network.ServerConfig
import com.barcodescanner.ui.theme.AppTheme
import com.barcodescanner.utils.NetworkUtils
import com.barcodescanner.utils.SoundHelper
import com.barcodescanner.utils.VibrationHelper
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 扫码基类 - 所有扫码页面继承此类
 * 提供：相机初始化、闪光灯、缩放、震动、声音、心跳上报、离线存储
 *
 * 子类需实现：
 * - getLayoutId(): 返回布局文件
 * - onBarcodeScanned(barcode: String): 扫码成功回调
 * - getScanHintText(): 扫码框提示文字
 */
abstract class BaseScannerActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val DEDUP_INTERVAL_MS = 800L
        private const val AUTO_FLASH_THRESHOLD = 45
        private const val AUTO_FLASH_DEBOUNCE_MS = 2000L
    }

    // ==================== 用户信息 ====================
    protected var userId = 0
    protected var userName = ""
    protected var isOffline = false
    protected var deviceId = ""

    // ==================== 相机 ====================
    private lateinit var previewView: PreviewView
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    protected var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: ExecutorService? = null

    // ==================== ML Kit ====================
    private lateinit var barcodeScanner: BarcodeScanner
    protected val isScanning = AtomicBoolean(true)

    // ==================== 闪光灯 ====================
    protected var isFlashOn = false
    private var lastFlashToggleTime = 0L

    // ==================== 扫码防抖 ====================
    private var lastScannedCode = ""
    private var lastScanTime = 0L

    // ==================== UI ====================
    private lateinit var scanOverlay: View
    private lateinit var btnFlashlight: ImageView
    protected lateinit var hintText: TextView
    private lateinit var zoomHint: TextView

    // ==================== 缩放 ====================
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomRatio = 1.0f

    // ==================== 心跳 ====================
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private val heartbeatInterval = 15000L
    private var isHeartbeatRunning = false

    // ==================== 抽象方法 ====================

    /** 子类提供布局文件 ID */
    protected abstract fun getLayoutId(): Int

    /** 扫码成功回调 */
    protected abstract fun onBarcodeScanned(barcode: String)

    /** 扫码框提示文字 */
    protected abstract fun getScanHintText(): String

    // ==================== 生命周期 ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(getLayoutId())

        // 读取用户信息
        userId = intent.getIntExtra("user_id", ServerConfig.getUserId())
        userName = intent.getStringExtra("user_name") ?: ServerConfig.getUserName()
        isOffline = intent.getBooleanExtra("is_offline", false)
        deviceId = intent.getStringExtra("device_id") ?: ServerConfig.getDeviceId()

        initBaseViews()
        initBarcodeScanner()
        initSound()

        // 启动心跳
        if (!isOffline && userId > 0) {
            startHeartbeat()
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHeartbeat()
        barcodeScanner.close()
        analysisExecutor?.shutdown()
        SoundHelper.release()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    // ==================== 初始化 ====================

    private fun initBaseViews() {
        previewView = findViewById(R.id.previewView)
        scanOverlay = findViewById(R.id.scanOverlay)
        btnFlashlight = findViewById(R.id.btnFlashlight)
        hintText = findViewById(R.id.hintText)
        zoomHint = findViewById(R.id.zoomHint)

        hintText.text = getScanHintText()

        btnFlashlight.setOnClickListener { toggleFlashlight() }

        // 缩放
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

    private fun initBarcodeScanner() {
        val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
    }

    private fun initSound() {
        SoundHelper.init(this)
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
            this as LifecycleOwner,
            cameraSelector,
            previewUseCase,
            analysisUseCase
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
                        handleBarcode(barcodes)
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

    private fun handleBarcode(barcodes: List<Barcode>) {
        val now = System.currentTimeMillis()
        val barcode = barcodes.firstOrNull() ?: return
        val rawValue = barcode.rawValue ?: return

        if (rawValue == lastScannedCode && now - lastScanTime < DEDUP_INTERVAL_MS) {
            return
        }

        lastScannedCode = rawValue
        lastScanTime = now

        // 震动 + 声音
        VibrationHelper.vibrate(this)
        SoundHelper.playBeep()

        // 暂停扫码
        isScanning.set(false)

        // 回调子类
        runOnUiThread { onBarcodeScanned(rawValue) }
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

    protected fun resumeScanning() {
        hintText.text = getScanHintText()
        hintText.setTextColor(Color.WHITE)
        isScanning.set(true)
    }

    // ==================== 心跳上报 ====================

    private fun startHeartbeat() {
        isHeartbeatRunning = true
        doHeartbeat()
    }

    private fun doHeartbeat() {
        if (!isHeartbeatRunning) return

        val ipAddress = NetworkUtils.getLocalIpAddress()
        val deviceName = NetworkUtils.getDeviceName()

        ApiClient.sendHeartbeat(deviceId, deviceName, ipAddress, userName, object : ApiClient.ApiCallback {
            override fun onSuccess(data: org.json.JSONObject?) {}
            override fun onError(error: String?) {}
        })

        heartbeatHandler.postDelayed({ doHeartbeat() }, heartbeatInterval)
    }

    private fun stopHeartbeat() {
        isHeartbeatRunning = false
        heartbeatHandler.removeCallbacksAndMessages(null)
    }

    // ==================== 离线存储 ====================

    protected fun saveOfflineRecord(barcode: String, address: String = "", weight: Double = 0.0) {
        try {
            val existing = ServerConfig.getOfflineRecords()
            val arr = JSONArray(existing)
            val record = JSONObject().apply {
                put("barcode", barcode)
                put("address", address)
                put("weight", weight)
                put("timestamp", System.currentTimeMillis())
            }
            arr.put(record)
            ServerConfig.saveOfflineRecords(arr.toString())
        } catch (_: Exception) {}
    }

    protected fun syncOfflineRecords() {
        try {
            val existing = ServerConfig.getOfflineRecords()
            val arr = JSONArray(existing)
            if (arr.length() == 0) return

            val records = mutableListOf<JSONObject>()
            for (i in 0 until arr.length()) {
                records.add(arr.getJSONObject(i))
            }

            ApiClient.syncOfflineRecords(records, userId, object : ApiClient.ApiCallback {
                override fun onSuccess(data: org.json.JSONObject?) {
                    ServerConfig.clearOfflineRecords()
                }
                override fun onError(error: String?) {}
            })
        } catch (_: Exception) {}
    }
}
