package com.barcodescanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 自定义扫描框遮罩 - 半透明遮罩 + 扫描框 + 扫描线动画
 * 类似微信扫一扫的视觉效果
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 扫描框
    private var scanFrameRect = RectF()
    private var scanFrameWidth = 280f.dpToPx()
    private var scanFrameHeight = 120f.dpToPx()
    private var cornerLength = 30f.dpToPx()
    private var cornerWidth = 4f.dpToPx()

    // 颜色
    private var frameColor = Color.parseColor("#00FF00")
    private var maskColor = Color.parseColor("#80000000")
    private var scanLineColor = Color.parseColor("#00FF00")

    // 画笔
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = cornerWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 180
    }

    // 扫描线动画
    private var scanLineY = 0f
    private var animator: ValueAnimator? = null

    init {
        // 读取自定义属性
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ScanOverlayView)
        frameColor = typedArray.getColor(R.styleable.ScanOverlayView_frameColor, frameColor)
        scanLineColor = typedArray.getColor(R.styleable.ScanOverlayView_scanLineColor, scanLineColor)
        typedArray.recycle()

        cornerPaint.color = frameColor
        scanLinePaint.color = scanLineColor

        startScanLineAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        scanFrameRect = RectF(
            cx - scanFrameWidth / 2,
            cy - scanFrameHeight / 2,
            cx + scanFrameWidth / 2,
            cy + scanFrameHeight / 2
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 绘制半透明遮罩（四个矩形区域）
        drawMask(canvas)

        // 2. 绘制四角
        drawCorners(canvas)

        // 3. 绘制扫描线
        drawScanLine(canvas)
    }

    private fun drawMask(canvas: Canvas) {
        maskPaint.color = maskColor
        val w = width.toFloat()
        val h = height.toFloat()

        // 上
        canvas.drawRect(0f, 0f, w, scanFrameRect.top, maskPaint)
        // 下
        canvas.drawRect(0f, scanFrameRect.bottom, w, h, maskPaint)
        // 左
        canvas.drawRect(0f, scanFrameRect.top, scanFrameRect.left, scanFrameRect.bottom, maskPaint)
        // 右
        canvas.drawRect(scanFrameRect.right, scanFrameRect.top, w, scanFrameRect.bottom, maskPaint)
    }

    private fun drawCorners(canvas: Canvas) {
        val l = scanFrameRect.left
        val t = scanFrameRect.top
        val r = scanFrameRect.right
        val b = scanFrameRect.bottom
        val cl = cornerLength

        cornerPaint.color = frameColor
        cornerPaint.strokeWidth = cornerWidth

        // 左上角
        canvas.drawLine(l, t + cl, l, t, cornerPaint)
        canvas.drawLine(l, t, l + cl, t, cornerPaint)

        // 右上角
        canvas.drawLine(r - cl, t, r, t, cornerPaint)
        canvas.drawLine(r, t, r, t + cl, cornerPaint)

        // 左下角
        canvas.drawLine(l, b - cl, l, b, cornerPaint)
        canvas.drawLine(l, b, l + cl, b, cornerPaint)

        // 右下角
        canvas.drawLine(r - cl, b, r, b, cornerPaint)
        canvas.drawLine(r, b, r, b - cl, cornerPaint)
    }

    private fun drawScanLine(canvas: Canvas) {
        val lineY = scanFrameRect.top + scanLineY
        scanLinePaint.color = scanLineColor

        // 渐变效果
        val gradient = LinearGradient(
            scanFrameRect.left, lineY - 10f.dpToPx(),
            scanFrameRect.left, lineY + 10f.dpToPx(),
            Color.TRANSPARENT,
            scanLineColor,
            Shader.TileMode.CLAMP
        )
        scanLinePaint.shader = gradient

        canvas.drawRect(
            scanFrameRect.left + 4f.dpToPx(),
            lineY - 2f.dpToPx(),
            scanFrameRect.right - 4f.dpToPx(),
            lineY + 2f.dpToPx(),
            scanLinePaint
        )

        scanLinePaint.shader = null
    }

    private fun startScanLineAnimation() {
        animator = ValueAnimator.ofFloat(0f, scanFrameHeight).apply {
            duration = 2500L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                scanLineY = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setFrameColor(color: Int) {
        frameColor = color
        cornerPaint.color = color
        scanLineColor = color
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    // ==================== dp 转 px 扩展 ====================

    private fun Float.dpToPx(): Float {
        return this * context.resources.displayMetrics.density
    }
}
