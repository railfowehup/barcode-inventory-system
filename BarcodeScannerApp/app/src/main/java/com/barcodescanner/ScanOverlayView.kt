package com.barcodescanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * 自定义扫码框覆盖层
 * 提供：半透明遮罩 + 扫描框（四角标记） + 扫描线动画
 *
 * 自定义属性：
 * - frameColor: 扫描框四角颜色
 * - scanLineColor: 扫描线颜色
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frameColor = Color.parseColor("#FF8C42")
    private var scanLineColor = Color.parseColor("#FF8C42")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private var scanLineY = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            scanLineY = scanLineStartY + (scanLineEndY - scanLineStartY) * it.animatedFraction
            invalidate()
        }
    }

    private var scanLineStartY = 0f
    private var scanLineEndY = 0f

    // 扫描框尺寸（相对于 View 的比例）
    private val frameWidthRatio = 0.75f
    private val frameHeightRatio = 0.5f

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ScanOverlayView)
        frameColor = a.getColor(R.styleable.ScanOverlayView_frameColor, frameColor)
        scanLineColor = a.getColor(R.styleable.ScanOverlayView_scanLineColor, scanLineColor)
        a.recycle()

        cornerPaint.color = frameColor
        linePaint.color = scanLineColor
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val frameWidth = w * frameWidthRatio
        val frameHeight = h * frameHeightRatio
        val frameLeft = (w - frameWidth) / 2f
        val frameTop = (h - frameHeight) / 2f
        scanLineStartY = frameTop + 20f
        scanLineEndY = frameTop + frameHeight - 20f
        scanLineY = scanLineStartY
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val frameWidth = w * frameWidthRatio
        val frameHeight = h * frameHeightRatio
        val frameLeft = (w - frameWidth) / 2f
        val frameTop = (h - frameHeight) / 2f

        // 半透明遮罩（除扫描框区域）
        paint.color = Color.parseColor("#80000000")
        // 上
        canvas.drawRect(0f, 0f, w, frameTop, paint)
        // 下
        canvas.drawRect(0f, frameTop + frameHeight, w, h, paint)
        // 左
        canvas.drawRect(0f, frameTop, frameLeft, frameTop + frameHeight, paint)
        // 右
        canvas.drawRect(frameLeft + frameWidth, frameTop, w, frameTop + frameHeight, paint)

        // 四角标记
        val cornerLen = 40f
        // 左上角
        canvas.drawLine(frameLeft, frameTop, frameLeft + cornerLen, frameTop, cornerPaint)
        canvas.drawLine(frameLeft, frameTop, frameLeft, frameTop + cornerLen, cornerPaint)
        // 右上角
        canvas.drawLine(frameLeft + frameWidth - cornerLen, frameTop, frameLeft + frameWidth, frameTop, cornerPaint)
        canvas.drawLine(frameLeft + frameWidth, frameTop, frameLeft + frameWidth, frameTop + cornerLen, cornerPaint)
        // 左下角
        canvas.drawLine(frameLeft, frameTop + frameHeight - cornerLen, frameLeft, frameTop + frameHeight, cornerPaint)
        canvas.drawLine(frameLeft, frameTop + frameHeight, frameLeft + cornerLen, frameTop + frameHeight, cornerPaint)
        // 右下角
        canvas.drawLine(frameLeft + frameWidth - cornerLen, frameTop + frameHeight, frameLeft + frameWidth, frameTop + frameHeight, cornerPaint)
        canvas.drawLine(frameLeft + frameWidth, frameTop + frameHeight - cornerLen, frameLeft + frameWidth, frameTop + frameHeight, cornerPaint)

        // 扫描线（带渐变）
        val gradient = LinearGradient(
            frameLeft, scanLineY, frameLeft + frameWidth, scanLineY,
            intArrayOf(Color.TRANSPARENT, scanLineColor, scanLineColor, Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        linePaint.shader = gradient
        canvas.drawRect(frameLeft + 10f, scanLineY, frameLeft + frameWidth - 10f, scanLineY + 4f, linePaint)
        linePaint.shader = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
