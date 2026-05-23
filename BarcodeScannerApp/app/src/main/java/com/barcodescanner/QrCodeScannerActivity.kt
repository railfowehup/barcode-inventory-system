package com.barcodescanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * 扫码连接服务器 - 扫描二维码自动填入服务器地址
 */
class QrCodeScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var cancelBtn: View

    private var isProcessing = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)

        previewView = findViewById(R.id.qrPreviewView)
        statusText = findViewById(R.id.qrStatusText)
        cancelBtn = findViewById(R.id.qrCancelBtn)

        cancelBtn.setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(640, 480))
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    processImage(imageProxy)
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "相机启动失败: ${e.message}"
                    statusText.setTextColor(Color.RED)
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        @Suppress("UnsafeOptInUsageError")
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner = BarcodeScanning.getClient()

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val value = barcode.rawValue ?: continue
                        // 检查是否是 URL
                        if (value.startsWith("http://") || value.startsWith("https://")) {
                            isProcessing = true
                            val url = value.trimEnd('/')
                            runOnUiThread {
                                statusText.text = "✅ 识别到地址: $url"
                                statusText.setTextColor(Color.GREEN)
                                Toast.makeText(this, "已识别服务器地址", Toast.LENGTH_SHORT).show()

                                // 返回结果给 LoginActivity
                                val resultIntent = android.content.Intent()
                                resultIntent.putExtra("scanned_url", url)
                                setResult(RESULT_OK, resultIntent)
                                finish()
                            }
                            break
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
