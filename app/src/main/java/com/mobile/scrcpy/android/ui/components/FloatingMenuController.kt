package com.mobile.scrcpy.android.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.mobile.scrcpy.android.R
import com.mobile.scrcpy.android.common.ApiCompatHelper
import com.mobile.scrcpy.android.common.LogTags
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.hypot

// ==================== 双球体系统配置 ====================

/** 大球 A 直径（dp） */
private const val BALL_A_SIZE_DP = 50

/** 小球 B 直径（dp） */
private const val BALL_B_SIZE_DP = 45

// ==================== 手势识别配置 ====================

/** 点击最大时长（毫秒），超过此时间不算点击 */
private const val CLICK_TIME_MS = 300L

/** 长按触发时长（毫秒），按住超过此时间触发长按模式 */
private const val LONG_PRESS_TIME_MS = 300L

/** 预留功能触发时长（毫秒），长按超过此时间触发预留功能 */
private const val RESERVED_FUNCTION_TIME_MS = 800L

/** 移动阈值（dp），手指移动超过此距离才算拖动 */
private const val MOVE_SLOP_DP = 12f

/** 长按取消阈值（dp），检测移动的最小阈值（用于取消长按延迟） */
private const val LONG_PRESS_CANCEL_SLOP_DP = 3f

/** 长按拖动时，小球距离大球的最大距离（dp） */
private const val MAX_DISTANCE_FROM_B_DP = 40f

/** 方向识别阈值（dp），拖动超过此距离才识别方向 */
private const val DIRECTION_THRESHOLD_DP = 15f

/** 方向触感延迟（毫秒），进入新扇形区域后延迟触发触感 */
private const val DIRECTION_HAPTIC_DELAY_MS = 300L

/** 归位动画时长（毫秒） */
private const val RESET_ANIMATION_DURATION_MS = 200L

// ==================== 贴边配置 ====================

/** 贴边触发距离（dp），小球边缘距离屏幕边缘小于此值时触发触感并开始贴边 */
private const val EDGE_SNAP_THRESHOLD_DP = 40f

/** 贴边后露出的宽度（dp），隐藏2/3，露出1/3 */
private const val EDGE_VISIBLE_WIDTH_DP = 15f  // BALL_A_SIZE_DP / 3 = 15dp

/** 拖出距离阈值（dp），拖动超过此距离时取消贴边 */
private const val EDGE_DRAG_OUT_THRESHOLD_DP = 30f

// ==================== 触感反馈配置 ====================

/** 边缘触感重置距离（dp），离开边缘超过此距离后重置触感状态，允许再次触发 */
private const val EDGE_HAPTIC_RESET_DISTANCE_DP = 40f

// =======================================================

/**
 * 触感反馈工具类
 * 使用 Vibrator 服务和 VibrationEffect 新 API 确保触感稳定触发
 */
private object HapticHelper {
    private var vibrator: Vibrator? = null

    /**
     * 初始化触感反馈（仅在开关开启时调用）
     */
    fun init(context: Context) {
        vibrator = ApiCompatHelper.getVibratorCompat(context)

        if (vibrator?.hasVibrator() == true) {
            Log.d(LogTags.FLOATING_CONTROLLER, "✅ Vibrator 初始化成功")
        } else {
            Log.w(LogTags.FLOATING_CONTROLLER, "⚠️ 设备不支持触感")
        }
    }

    /**
     * 触发触感反馈
     * @param type 触感类型：tick(轻点), click(点击), heavy(重击)
     */
    fun vibrate(type: String = "tick") {
        ApiCompatHelper.vibrateCompat(vibrator, type)
    }
}

/**
 * 触感反馈辅助函数
 * 使用 Vibrator 服务替代 View.performHapticFeedback，确保触感稳定触发
 */
private fun performHapticFeedbackCompat(feedbackConstant: Int) {
    // 映射 HapticFeedbackConstants 到触感类型
    val rejectConstant = ApiCompatHelper.getHapticFeedbackConstant("reject")
    val type = when (feedbackConstant) {
        HapticFeedbackConstants.CLOCK_TICK,
        HapticFeedbackConstants.KEYBOARD_TAP,
        HapticFeedbackConstants.VIRTUAL_KEY -> "tick"
        HapticFeedbackConstants.CONTEXT_CLICK -> "click"
        HapticFeedbackConstants.LONG_PRESS,
        rejectConstant -> "heavy"
        else -> "tick"
    }
    HapticHelper.vibrate(type)
}

/**
 * 悬浮菜单控制器组件（自动显示版本）
 * 在 RemoteDisplayScreen 中自动显示悬浮球
 *
 * 注意：此组件在连接设备后创建，ScrcpyForegroundService 已在运行
 * 触感反馈使用 Vibrator 服务，独立工作
 *
 * @param viewModel MainViewModel 实例，用于发送控制信号
 */
@Composable
fun AutoFloatingMenu(viewModel: com.mobile.scrcpy.android.feature.session.MainViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var ballSystemReference by remember { mutableStateOf<Tuple4<View, View, WindowManager, GestureHandler>?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    // 在 Activity 中创建悬浮球
    LaunchedEffect(Unit) {
        Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 创建悬浮球")
        ballSystemReference = showDualBallSystem(context, viewModel, scope)
        // 延迟启用旋转监听，避免初始化时的配置抖动
        kotlinx.coroutines.delay(300)
        isInitialized = true
    }

    // 监听屏幕旋转，重新定位小球
    LaunchedEffect(configuration.orientation) {
        if (isInitialized && ballSystemReference != null) {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🔄 屏幕旋转，检查小球位置 (方向=${configuration.orientation})")
            ballSystemReference?.let { reference ->
                repositionBallsOnRotation(context, reference)
            }
        }
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "❌ 隐藏悬浮球")
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
        }
    }
}

/**
 * 悬浮菜单控制器组件（自动显示版本 - 直接模式，不使用 Service）
 * 在 RemoteDisplayScreen 中自动显示悬浮球
 * 注意：此模式在后台容易被系统杀掉，推荐使用 AutoFloatingMenu（Service 模式）
 * @param viewModel MainViewModel 实例，用于发送控制信号
 */
@Composable
fun AutoFloatingMenuDirect(viewModel: com.mobile.scrcpy.android.feature.session.MainViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var ballSystemReference by remember { mutableStateOf<Tuple4<View, View, WindowManager, GestureHandler>?>(null) }
    var isInitialized by remember { mutableStateOf(false) }

    // 自动显示悬浮球
    LaunchedEffect(Unit) {
        Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 自动显示双球体系统（直接模式）")
        ballSystemReference = showDualBallSystem(context, viewModel, scope)
        // 延迟启用旋转监听，避免初始化时的配置抖动
        kotlinx.coroutines.delay(300)
        isInitialized = true
    }

    // 监听屏幕旋转，重新定位小球
    LaunchedEffect(configuration.orientation) {
        if (isInitialized && ballSystemReference != null) {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🔄 屏幕旋转，检查小球位置 (方向=${configuration.orientation})")
            // 平滑移动到默认位置，而不是重建
            ballSystemReference?.let { reference ->
                repositionBallsOnRotation(context, reference)
            }
        }
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "❌ 隐藏双球体系统")
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
        }
    }
}

/**
 * TODO App 首页悬浮菜单控制器组件 测试用途 请勿删除
 * 提供双球体系统的悬浮窗交互功能
 */
@Composable
fun FloatingMenuController(viewModel: com.mobile.scrcpy.android.feature.session.MainViewModel) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var isFloatingShown by remember { mutableStateOf(false) }
    var ballSystemReference by remember { mutableStateOf<Tuple4<View, View, WindowManager, GestureHandler>?>(null) }
    var lastOrientation by remember { mutableIntStateOf(configuration.orientation) }

    // 监听屏幕旋转，重新定位小球
    LaunchedEffect(configuration.orientation) {
        if (isFloatingShown && ballSystemReference != null && lastOrientation != configuration.orientation) {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🔄 屏幕旋转，检查小球位置 (${lastOrientation} → ${configuration.orientation})")
            configuration.orientation
            // 平滑移动到默认位置，而不是重建
            ballSystemReference?.let { reference ->
                repositionBallsOnRotation(context, reference)
            }
        } else if (isFloatingShown && ballSystemReference != null) {
            // 首次显示时记录方向
            configuration.orientation
        }
    }

    IconButton(onClick = {
        if (!isFloatingShown) {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 显示双球体系统")
            ballSystemReference = showDualBallSystem(context, viewModel, scope)
            isFloatingShown = true
        } else {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "❌ 隐藏双球体系统")
            hideDualBallSystem(ballSystemReference)
            ballSystemReference = null
            isFloatingShown = false
        }
    }) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "测试悬浮窗",
            tint = if (isFloatingShown) Color(0xFFFF3B30) else Color(0xFF007AFF)
        )
    }
}

/**
 * 显示双球体系统：A（小球）+ B（大球），都用 WindowManager 实现
 * @param viewModel MainViewModel 实例，用于发送控制信号
 * @param scope CoroutineScope 用于异步操作
 * @return 返回 (ballA, ballB, windowManager, gestureHandler) 的引用，用于后续移除
 */
fun showDualBallSystem(
    context: Context,
    viewModel: com.mobile.scrcpy.android.feature.session.MainViewModel,
    scope: kotlinx.coroutines.CoroutineScope
): Tuple4<View, View, WindowManager, GestureHandler> {
    // 读取触感反馈开关状态（只读取一次）
    val hapticEnabled = viewModel.settings.value.enableFloatingHapticFeedback

    // 仅在开关开启时初始化触感反馈
    if (hapticEnabled) {
        HapticHelper.init(context)
        Log.d(LogTags.FLOATING_CONTROLLER_MSG, "✅ 触感反馈已启用")
    } else {
        Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🔕 触感反馈已禁用")
    }

    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val density = context.resources.displayMetrics.density
    val displayMetrics = context.resources.displayMetrics

    // 判断屏幕方向
    val isLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels

    // 计算初始位置
    val ballBX: Float
    val ballBY: Float

    if (isLandscape) {
        // 横屏：右侧上下居中，距离右边缘 20dp
        ballBX = displayMetrics.widthPixels - 20 * density - BALL_B_SIZE_DP * density
        ballBY = (displayMetrics.heightPixels - BALL_B_SIZE_DP * density) / 2f
    } else {
        // 竖屏：底部左右居中，距离底部 85dp
        ballBX = (displayMetrics.widthPixels - BALL_B_SIZE_DP * density) / 2f
        ballBY = displayMetrics.heightPixels - 85 * density - BALL_B_SIZE_DP * density
    }

    // 小球A的位置（中心对齐大球B）
    val ballACenterOffsetX = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f
    val ballACenterOffsetY = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f
    val ballAX = ballBX + ballACenterOffsetX
    val ballAY = ballBY + ballACenterOffsetY

    // 创建大球 B（底层）
    val ballB = createBall(context, sizeDp = BALL_B_SIZE_DP)
    val paramsB = createWindowParams(context, sizeDp = BALL_B_SIZE_DP, isFocusable = false)
    paramsB.x = ballBX.toInt()
    paramsB.y = ballBY.toInt()
    windowManager.addView(ballB, paramsB)

    // 创建小球 A（顶层，可触摸）
    val ballA = createBall(context, sizeDp = BALL_A_SIZE_DP)
    val paramsA = createWindowParams(context, sizeDp = BALL_A_SIZE_DP, isFocusable = true)
    paramsA.x = ballAX.toInt()
    paramsA.y = ballAY.toInt()
    windowManager.addView(ballA, paramsA)

    // 设置触摸事件
    val gestureHandler = GestureHandler(
        context = context,
        ballA = ballA,
        ballB = ballB,
        windowManager = windowManager,
        paramsA = paramsA,
        paramsB = paramsB,
        viewModel = viewModel,
        scope = scope,
        hapticEnabled = hapticEnabled  // 传递触感开关状态
    )
    ballA.setOnTouchListener(gestureHandler)

    Log.d(LogTags.FLOATING_CONTROLLER_MSG, "✅ 双球体系统已创建（${if (isLandscape) "横屏" else "竖屏"}）")
    return Tuple4(ballA, ballB, windowManager, gestureHandler)
}

// 辅助数据类
data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * 隐藏双球体系统
 */
fun hideDualBallSystem(reference: Tuple4<View, View, WindowManager, GestureHandler>?) {
    reference?.let { (ballA, ballB, windowManager, gestureHandler) ->
        try {
            // 先清理菜单
            gestureHandler.cleanup()

            // 移除所有球体（检查是否已附加到窗口）
            if (ballA.isAttachedToWindow) {
                windowManager.removeView(ballA)
            }
            if (ballB.isAttachedToWindow) {
                windowManager.removeView(ballB)
            }
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "✅ 双球体系统已移除")
        } catch (e: Exception) {
            Log.e(LogTags.FLOATING_CONTROLLER, "移除球体失败: ${e.message}")
        }
    }
}

/**
 * 创建球体 View
 */
private fun createBall(context: Context, sizeDp: Int): View {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()
    val radius = sizePx / 2f

    // 球颜色（使用iOS经典灰色）
    val ballColorsNormal = arrayOf(
        android.graphics.Color.argb(153, 58, 58, 60),  // 外层 60%
        android.graphics.Color.argb(102, 44, 44, 46),  // 第二层 40%
        android.graphics.Color.argb(64, 28, 28, 30),   // 第三层 25%
        android.graphics.Color.argb(100, 255, 255, 255) // 25% 白色
    )

    val layerFactors = floatArrayOf(1.0f, 0.75f, 0.60f, 0.40f) // 让每层更小，创造更明显的立体效果

    // 预分配 Paint 对象以避免在 onDraw 中重复创建
    val paints = ballColorsNormal.map { color ->
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
    }

    return object : View(context) {
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            for (i in ballColorsNormal.indices) {
                val paint = paints[i]
                for (j in 0..3) { canvas.drawCircle(centerX, centerY, radius * layerFactors[j], paint) }
            }
        }
    }.apply {
        layoutParams = android.view.ViewGroup.LayoutParams(sizePx, sizePx)
        // ✅ 关键：启用触觉反馈
        isHapticFeedbackEnabled = true
    }
}

/**
 * 创建 WindowManager 参数
 */
private fun createWindowParams(context: Context, sizeDp: Int, isFocusable: Boolean): WindowManager.LayoutParams {
    val density = context.resources.displayMetrics.density
    val sizePx = (sizeDp * density).toInt()

    return WindowManager.LayoutParams().apply {
        // 应用内悬浮窗使用 TYPE_APPLICATION
        type = WindowManager.LayoutParams.TYPE_APPLICATION
        format = PixelFormat.TRANSLUCENT
        flags = if (isFocusable) { WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else { WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE }
        width = sizePx
        height = sizePx
        gravity = Gravity.TOP or Gravity.START
    }
}

/**
 * 手势识别处理器（纯 WindowManager 实现）
 *
 * 手势类型：
 * 1. 点击：按下 -> 松开（时间 < 300ms，移动 < 阈值）
 * 2. 拖动：按下 -> 移动（未长按）-> 松开（B 跟随 A）
 * 3. 长按：按下 -> 等待（> 500ms）-> 移动（A 围绕 B 转圈）
 */
@SuppressLint("ClickableViewAccessibility")
class GestureHandler(
    private val context: Context,
    private val ballA: View,
    private val ballB: View,
    private val windowManager: WindowManager,
    private val paramsA: WindowManager.LayoutParams,
    private val paramsB: WindowManager.LayoutParams,
    private val viewModel: com.mobile.scrcpy.android.feature.session.MainViewModel,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val hapticEnabled: Boolean  // 触感反馈开关状态
) : View.OnTouchListener {

    // ==================== 内部状态 ====================

    private val MOVE_SLOP_PX = MOVE_SLOP_DP * context.resources.displayMetrics.density
    private val LONG_PRESS_CANCEL_SLOP_PX = LONG_PRESS_CANCEL_SLOP_DP * context.resources.displayMetrics.density

    // 手势状态
    private var downTime = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var hasMoved = false
    private var isLongPress = false
    private var canEnterLongPress = false  // 是否可以进入长按模式（300ms内没有移动）
    private var longPressHandler: android.os.Handler? = null
    private var longPressRunnable: Runnable? = null
    private var reservedFunctionHandler: android.os.Handler? = null
    private var reservedFunctionRunnable: Runnable? = null

    // B 球中心位置（用于长按时 A 围绕 B 转圈）
    private var ballBCenterX = 0f
    private var ballBCenterY = 0f

    // 上一次的角度（用于平滑过渡，转圈功能使用）
    private var lastAngle: Double? = null

    // 按下时手指相对于A球中心的偏移量（用于长按拖动时保持相对位置）
    private var downOffsetX = 0f
    private var downOffsetY = 0f

    // 贴边状态
    private var isSnappedToEdge = false
    private var snappedEdge: Edge? = null  // 贴在哪个边

    // 边缘触感反馈状态（防止重复触发）
    private var hasTriggeredEdgeHaptic = false  // 是否已触发边缘触感

    enum class Edge {
        LEFT, RIGHT, TOP, BOTTOM
    }

    // 长按拖动方向识别
    private var detectedDirection: Direction? = null  // 已识别的方向（仅用于日志显示）
    private var directionLocked = false  // 方向是否已锁定（已废弃，保留兼容）

    // 扇形区域触感反馈状态
    private var lastHapticDirection: Direction? = null  // 上次触发触感的扇形区域
    private var directionEnterTime = 0L  // 进入当前扇形区域的时间
    private var hasTriggeredHapticInCurrentDirection = false  // 当前扇形区域是否已触发触感

    enum class Direction(val actionName: String) {
        UP("桌面"),
        DOWN("通知栏"),
        LEFT("返回"),
        RIGHT("后台任务")
    }

    // 归位动画
    private var resetAnimator: android.animation.ValueAnimator? = null

    // 菜单状态
    private var isMenuShown = false
    private var menuView: View? = null
    private var menuParams: WindowManager.LayoutParams? = null

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 检查触摸点是否在圆形区域内
                val touchX = event.x
                val touchY = event.y
                val centerX = v.width / 2f
                val centerY = v.height / 2f
                val radius = v.width / 2f
                val distance = hypot((touchX - centerX).toDouble(), (touchY - centerY).toDouble())
                if (distance > radius) {
                    // 触摸点在圆形外部，不处理
                    Log.d(LogTags.FLOATING_CONTROLLER,
                        "❌ 触摸点在圆外: 距离=${distance.toInt()}px, 半径=${radius.toInt()}px")
                    return false
                }

                handleDown(event)
            }
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp()
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    /**
     * 按下：记录初始状态
     */
    private fun handleDown(event: MotionEvent) {
        // 取消正在进行的归位动画
        resetAnimator?.cancel()
        resetAnimator = null

        // 取消之前的长按延迟任务
        longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
        reservedFunctionRunnable?.let { reservedFunctionHandler?.removeCallbacks(it) }
        longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        reservedFunctionHandler = android.os.Handler(android.os.Looper.getMainLooper())

        downTime = System.currentTimeMillis()
        downRawX = event.rawX
        downRawY = event.rawY
        lastRawX = event.rawX
        lastRawY = event.rawY
        hasMoved = false
        isLongPress = false
        canEnterLongPress = false

        // 按下时不触发触感反馈，避免连续触发

        // 启动300ms延迟任务：如果300ms内没有移动，就允许进入长按模式
        longPressRunnable = Runnable {
            if (!hasMoved) {
                canEnterLongPress = true
                // 长按触发时使用更强的触感反馈（仅在开关开启时）
                if (hapticEnabled) {
                    performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                }
                Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 按住300ms未移动，可以进入长按模式")
            }
        }

        // 启动800ms延迟任务：预留功能
        reservedFunctionRunnable = Runnable {
            if (!hasMoved && canEnterLongPress) {
                // 预留功能触发时使用更强的触感反馈（仅在开关开启时）
                if (hapticEnabled) {
                    performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                }
                Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 按住800ms未移动，预留功能触发")
            }
        }

        longPressHandler?.postDelayed(longPressRunnable!!, LONG_PRESS_TIME_MS)
        reservedFunctionHandler?.postDelayed(reservedFunctionRunnable!!, RESERVED_FUNCTION_TIME_MS)

        // 记录 B 球中心位置
        ballBCenterX = paramsB.x + ballB.width / 2f
        ballBCenterY = paramsB.y + ballB.height / 2f

        // 计算 A 球中心位置
        val ballACenterX = paramsA.x + ballA.width / 2f
        val ballACenterY = paramsA.y + ballA.height / 2f

        // 记录按下时手指相对于A球中心的偏移量（用于长按拖动时保持相对位置）
        downOffsetX = event.rawX - ballACenterX
        downOffsetY = event.rawY - ballACenterY

        Log.d(LogTags.FLOATING_CONTROLLER,
            "⬇️ 按下 at (${event.rawX}, ${event.rawY}), " +
                    "B中心=($ballBCenterX, $ballBCenterY), " +
                    "A中心=($ballACenterX, $ballACenterY), " +
                    "A左上角=(${paramsA.x}, ${paramsA.y}), " +
                    "偏移=($downOffsetX, $downOffsetY)")
    }

    /**
     * 移动：判断手势类型并执行相应动作
     */
    private fun handleMove(event: MotionEvent) {
        val dx = event.rawX - downRawX
        val dy = event.rawY - downRawY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val duration = System.currentTimeMillis() - downTime

        // 一旦检测到移动超过小阈值（3dp），立即取消长按延迟任务
        // 这样可以确保：如果用户按住后开始移动（即使移动很慢），也不会进入长按模式
        if (distance > LONG_PRESS_CANCEL_SLOP_PX && !hasMoved) {
            // 取消长按延迟任务（因为已经开始移动了）
            longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
            reservedFunctionRunnable?.let { reservedFunctionHandler?.removeCallbacks(it) }
            // 如果已经设置了 canEnterLongPress，说明300ms内没有移动，现在开始移动 → 长按模式
            // 如果没有设置 canEnterLongPress，说明300ms内已经移动了 → 普通拖动
            if (canEnterLongPress) {
                isLongPress = true
                // 进入长按拖动模式时不需要额外触感（已经在300ms时触感过了）
                Log.d(LogTags.FLOATING_CONTROLLER, "⏱️ 长按触发！按住300ms后开始移动，切换到转圈模式")
            } else {
                isLongPress = false  // 明确设置为普通拖动模式
                Log.d(LogTags.FLOATING_CONTROLLER, "📱 普通拖动模式（按住${duration}ms后开始移动，距离=${distance.toInt()}px）")
            }
        }

        // 判断是否超过移动阈值
        if (distance > MOVE_SLOP_PX) {
            hasMoved = true
        }

        if (hasMoved) {
            if (isLongPress) {
                // 长按 + 移动：A 球跟随手指移动，B 球不动
                moveAAroundB(event)
            } else {
                // 普通拖动：A 和 B 一起移动
                moveAAndBTogether(event)
            }
        }
    }

    /**
     * 松开：判断是否为点击，长按后归位，普通拖动后贴边
     */
    private fun handleUp() {
        val duration = System.currentTimeMillis() - downTime

        // 如果是长按拖动，根据松手时的位置判断方向
        val finalDirection = if (isLongPress && hasMoved) {
            // 计算松手时相对于B球中心的偏移
            val ballACenterX = paramsA.x + ballA.width / 2f
            val ballACenterY = paramsA.y + ballA.height / 2f
            val dx = ballACenterX - ballBCenterX
            val dy = ballACenterY - ballBCenterY
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

            // 只有移动距离超过阈值才识别方向
            val density = context.resources.displayMetrics.density
            val directionThreshold = DIRECTION_THRESHOLD_DP * density
            if (distance > directionThreshold) {
                detectDirection(dx, dy)
            } else {
                null
            }
        } else {
            null
        }

        // 构建日志信息
        val directionInfo = if (finalDirection != null) {
            "$finalDirection (${finalDirection.actionName})"
        } else if (canEnterLongPress && !hasMoved) {
            // 长按超过300ms但没有移动 → 预留功能
            "未移动 (预留功能)"
        } else {
            "null"
        }

        Log.d(LogTags.FLOATING_CONTROLLER,
            "⬆️ 松开 - 时长: ${duration}ms, 移动: $hasMoved, 长按: $isLongPress, 可长按: $canEnterLongPress, 方向: $directionInfo")

        if (!hasMoved && duration < CLICK_TIME_MS) {
            // 点击事件 - 清脆的点击反馈（仅在开关开启时）
            if (hapticEnabled) {
                performHapticFeedbackCompat(HapticFeedbackConstants.CLOCK_TICK)
            }
            handleClick()
        } else if (canEnterLongPress && !hasMoved) {
            // 长按超过300ms但没有移动 → 预留功能
            Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                "⚠️ 长按超过${LONG_PRESS_TIME_MS}ms但未移动 → 预留功能")
        } else if (isLongPress && hasMoved) {
            // 长按拖动后，发送控制信号并归位到 B 球中心
            if (finalDirection != null) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                    "✅ 手势完成: ${finalDirection.actionName} ($finalDirection)")

                // 发送控制信号
                scope.launch {
                    when (finalDirection) {
                        Direction.LEFT -> {
                            // 返回键 (KEYCODE_BACK = 4)
                            viewModel.sendKeyEvent(4)
                        }
                        Direction.RIGHT -> {
                            // 最近任务 (KEYCODE_APP_SWITCH = 187)
                            viewModel.sendKeyEvent(187)
                        }
                        Direction.UP -> {
                            // 桌面 (KEYCODE_HOME = 3)
                            viewModel.sendKeyEvent(3)
                        }
                        Direction.DOWN -> {
                            // 通知栏 - 使用 ADB 命令展开通知栏
                            viewModel.executeShellCommand("cmd statusbar expand-notifications")
                            Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                                "📱 下拉通知栏: 执行命令 'cmd statusbar expand-notifications'")
                        }
                    }
                }
            } else {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                    "⚠️ 长按拖动但未识别方向 → 预留功能")
            }
            resetAPosition()
        } else if (hasMoved && !isLongPress) {
        // } else if (hasMoved) {
            // 普通拖动后，先对齐A和B的中心，然后贴边隐藏
            alignBalls()
            snapToEdge()
        }

        // 取消长按延迟任务
        longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }
        reservedFunctionRunnable?.let { reservedFunctionHandler?.removeCallbacks(it) }

        // 重置状态
        hasMoved = false
        isLongPress = false
        canEnterLongPress = false
        lastAngle = null  // 重置角度（转圈功能使用）
        downOffsetX = 0f
        downOffsetY = 0f
        detectedDirection = null  // 重置方向
        directionLocked = false  // 重置方向锁定
        hasTriggeredEdgeHaptic = false  // 重置边缘触感状态
        lastHapticDirection = null  // 重置扇形区域触感状态
        directionEnterTime = 0L
        hasTriggeredHapticInCurrentDirection = false
    }

    /**
     * 取消：重置状态
     */
    private fun handleCancel() {
        Log.d(LogTags.FLOATING_CONTROLLER, "❌ 手势取消")

        // 取消长按延迟任务
        longPressRunnable?.let { longPressHandler?.removeCallbacks(it) }

        hasMoved = false
        isLongPress = false
        canEnterLongPress = false
        downOffsetX = 0f
        downOffsetY = 0f
    }

    /**
     * 点击事件：切换菜单显示/隐藏
     */
    private fun handleClick() {
        if (isMenuShown) {
            // 隐藏菜单
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 点击！隐藏菜单")
            hideMenu()
            isMenuShown = false
        } else {
            // 显示菜单 - 菜单弹出时的触感反馈（仅在开关开启时）
            if (hapticEnabled) {
                performHapticFeedbackCompat(HapticFeedbackConstants.CONTEXT_CLICK)
            }
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🎯 点击！显示菜单")
            showMenu()
            isMenuShown = true
        }
    }

    /**
     * 显示菜单（使用 WindowManager）
     */
    private fun showMenu() {
        // 创建临时父容器以正确解析布局参数
        val parent = android.widget.FrameLayout(context)

        // 创建菜单 View
        val menu = android.view.LayoutInflater.from(context)
            .inflate(R.layout.floating_menu, parent, false)

        // 强制测量菜单尺寸
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val density = context.resources.displayMetrics.density
        val displayMetrics = context.resources.displayMetrics

        // 获取菜单实际宽度和高度
        val menuWidth = if (menu.measuredWidth > 0) menu.measuredWidth else (240 * density).toInt()
        val menuHeight = if (menu.measuredHeight > 0) menu.measuredHeight else (48 * density).toInt()

        // 创建 WindowManager 参数
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START

            // 垂直位置：菜单永远在小球上方，距离小球顶部 35dp
            y = (paramsA.y - menuHeight - 35 * density).toInt()

            // 水平位置：初次显示时，菜单水平居中对齐屏幕
            x = (displayMetrics.widthPixels - menuWidth) / 2

            // 限制菜单不超出屏幕顶部
            if (y < 0) {
                y = 0
            }
        }

        // 添加到窗口
        windowManager.addView(menu, params)
        menuView = menu
        menuParams = params

        // 设置按钮点击事件
        setupMenuButtons(menu)
    }

    /**
     * 隐藏菜单
     */
    private fun hideMenu() {
        menuView?.let { menu ->
            try {
                windowManager.removeView(menu)
            } catch (e: Exception) {
                Log.e(LogTags.FLOATING_CONTROLLER, "移除菜单失败: ${e.message}")
            }
        }
        menuView = null
        menuParams = null
    }

    /**
     * 设置菜单按钮
     */
    private fun setupMenuButtons(menu: View) {
        // 为每个按钮添加触感反馈（仅在开关开启时）
        val hapticClickListener: (View, () -> Unit) -> Unit = { view, action ->
            view.setOnClickListener {
                if (hapticEnabled) {
                    performHapticFeedbackCompat(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                action()
            }
        }

        // 返回键
        menu.findViewById<android.widget.ImageButton>(R.id.btn_back)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "⬅️ 返回按钮")
                scope.launch {
                    viewModel.sendKeyEvent(4) // KEYCODE_BACK
                }
                hideMenu()
                isMenuShown = false
            }
        }

        // 主页键
        menu.findViewById<android.widget.ImageButton>(R.id.btn_home)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🏠 主页按钮")
                scope.launch {
                    viewModel.sendKeyEvent(3) // KEYCODE_HOME
                }
                hideMenu()
                isMenuShown = false
            }
        }

        // 最近任务（多任务切换）
        menu.findViewById<android.widget.ImageButton>(R.id.btn_recent)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "📋 最近任务按钮")
                scope.launch {
                    viewModel.sendKeyEvent(187) // KEYCODE_APP_SWITCH
                }
                hideMenu()
                isMenuShown = false
            }
        }

        // 键盘按钮
        menu.findViewById<android.widget.ImageButton>(R.id.btn_keyboard)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "⌨️ 键盘按钮")
                // TODO: 实现键盘功能
                hideMenu()
                isMenuShown = false
            }
        }

        // 更多菜单按钮
        menu.findViewById<android.widget.ImageButton>(R.id.btn_menu)?.let { btn ->
            hapticClickListener(btn) {
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "📱 更多菜单按钮")
                // TODO: 实现更多菜单功能
                hideMenu()
                isMenuShown = false
            }
        }

        // 断开连接按钮
        menu.findViewById<android.widget.ImageButton>(R.id.btn_close)?.let { btn ->
            btn.setOnClickListener {
                // 断开连接使用更强的反馈（仅在开关开启时）
                if (hapticEnabled) {
                    performHapticFeedbackCompat(ApiCompatHelper.getHapticFeedbackConstant("reject"))
                }
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "❌ 断开连接")
                // 断开会话，返回主界面
                scope.launch {
                    // 先隐藏菜单和球体
                    hideMenu()
                    try {
                        if (ballA.isAttachedToWindow) {
                            windowManager.removeView(ballA)
                        }
                        if (ballB.isAttachedToWindow) {
                            windowManager.removeView(ballB)
                        }
                    } catch (e: Exception) {
                        Log.e(LogTags.FLOATING_CONTROLLER, "移除球体失败: ${e.message}")
                    }

                    // 断开设备连接
                    viewModel.clearConnectStatus()
                    viewModel.disconnectFromDevice()
                }
            }
        }
    }

    /**
     * 普通拖动：A 和 B 一起移动，菜单跟随，检测拖出贴边和到达边缘
     * 确保A和B始终保持中心对齐
     */
    private fun moveAAndBTogether(event: MotionEvent) {
        val deltaX = event.rawX - lastRawX
        val deltaY = event.rawY - lastRawY
        val density = context.resources.displayMetrics.density
        val displayMetrics = context.resources.displayMetrics

        // 如果已经贴边，检测是否拖出
        if (isSnappedToEdge && snappedEdge != null) {
            val dragOutThreshold = EDGE_DRAG_OUT_THRESHOLD_DP * density
            val shouldDragOut = when (snappedEdge!!) {
                Edge.LEFT -> deltaX > dragOutThreshold
                Edge.RIGHT -> deltaX < -dragOutThreshold
                Edge.TOP -> deltaY > dragOutThreshold
                Edge.BOTTOM -> deltaY < -dragOutThreshold
            }

            if (shouldDragOut) {
                // 拖出贴边状态
                isSnappedToEdge = false
                snappedEdge = null
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🔓 拖出贴边")

                // 如果菜单显示，拖出贴边时菜单居中对齐
                if (isMenuShown && menuView != null && menuParams != null) {
                    val menuWidth = if (menuView!!.measuredWidth > 0) menuView!!.measuredWidth else (240 * density).toInt()
                    menuParams!!.x = (displayMetrics.widthPixels - menuWidth) / 2
                    try {
                        windowManager.updateViewLayout(menuView, menuParams)
                        Log.d(LogTags.FLOATING_CONTROLLER_MSG, "📍 菜单居中对齐")
                    } catch (e: Exception) {
                        Log.e(LogTags.FLOATING_CONTROLLER, "菜单居中失败: ${e.message}")
                    }
                }
            }
        }

        // 计算A球的新中心位置（基于触摸点移动）
        val currentACenterX = paramsA.x + ballA.width / 2f
        val currentACenterY = paramsA.y + ballA.height / 2f
        val newACenterX = currentACenterX + deltaX
        val newACenterY = currentACenterY + deltaY

        // 计算边界限制（考虑球的半径）
        val ballARadius = ballA.width / 2f
        val ballBRadius = ballB.width / 2f
        val minX = ballARadius.coerceAtLeast(ballBRadius)
        val maxX = displayMetrics.widthPixels - ballARadius.coerceAtLeast(ballBRadius)
        val minY = ballARadius.coerceAtLeast(ballBRadius)
        val maxY = displayMetrics.heightPixels - ballARadius.coerceAtLeast(ballBRadius)

        // 检测是否到达边缘（在限制之前）
        var reachedEdge = false
        var currentEdge: Edge? = null
        var distanceToNearestEdge = Float.MAX_VALUE

        if (!isSnappedToEdge) {
            val snapThreshold = EDGE_SNAP_THRESHOLD_DP * density

            // 检测到达哪个边缘，并记录距离
            val distToLeft = newACenterX - ballARadius
            val distToRight = displayMetrics.widthPixels - (newACenterX + ballARadius)
            val distToTop = newACenterY - ballARadius
            val distToBottom = displayMetrics.heightPixels - (newACenterY + ballARadius)

            if (distToLeft < snapThreshold) {
                reachedEdge = true
                currentEdge = Edge.LEFT
                distanceToNearestEdge = distToLeft
            } else if (distToRight < snapThreshold) {
                reachedEdge = true
                currentEdge = Edge.RIGHT
                distanceToNearestEdge = distToRight
            } else if (distToTop < snapThreshold) {
                reachedEdge = true
                currentEdge = Edge.TOP
                distanceToNearestEdge = distToTop
            } else if (distToBottom < snapThreshold) {
                reachedEdge = true
                currentEdge = Edge.BOTTOM
                distanceToNearestEdge = distToBottom
            } else {
                // 计算最近边缘的距离
                distanceToNearestEdge = minOf(distToLeft, distToRight, distToTop, distToBottom)
            }
        }

        // 限制A球中心位置
        val clampedACenterX = newACenterX.coerceIn(minX, maxX)
        val clampedACenterY = newACenterY.coerceIn(minY, maxY)

        // 如果进入边缘区域（40dp内），立即触发触感（只触发一次）
        // 顶部和底部不触发触感反馈
        if (reachedEdge && !hasTriggeredEdgeHaptic && hapticEnabled) {
            // 只有左右边缘触发触感，顶部和底部不触发
            if (currentEdge == Edge.LEFT || currentEdge == Edge.RIGHT) {
                performHapticFeedbackCompat(HapticFeedbackConstants.VIRTUAL_KEY)
                hasTriggeredEdgeHaptic = true  // 标记已触发，防止重复
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🧲 进入边缘区域: ${currentEdge.name}, 距离=${distanceToNearestEdge.toInt()}px")
            }
        }

        // 如果离开边缘区域足够远，重置触感状态
        val hapticResetThreshold = EDGE_HAPTIC_RESET_DISTANCE_DP * density
        if (hasTriggeredEdgeHaptic && distanceToNearestEdge > hapticResetThreshold) {
            hasTriggeredEdgeHaptic = false
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "↩️ 离开边缘${distanceToNearestEdge.toInt()}px（阈值${hapticResetThreshold.toInt()}px），重置触感状态")
        }

        // 计算A球的左上角位置
        val newAX = (clampedACenterX - ballARadius).toInt()
        val newAY = (clampedACenterY - ballARadius).toInt()

        // B球中心应该与A球中心对齐（考虑大小差异）
        // A和B的中心应该完全对齐
        val newBCenterX = clampedACenterX

        // 计算B球的左上角位置
        val newBX = (newBCenterX - ballBRadius).toInt()
        val newBY = (clampedACenterY - ballBRadius).toInt()

        // 计算小球的实际移动距离（考虑边界限制）
        val finalDeltaX = newAX - paramsA.x
        var finalDeltaY = newAY - paramsA.y

        // 如果菜单显示，需要同时检查小球边界和菜单边界
        // 关键：如果小球已经到达边界（无法移动），菜单也不应该移动
        if (isMenuShown && menuView != null && menuParams != null) {
            val menuHeight = if (menuView!!.measuredHeight > 0) menuView!!.measuredHeight else (48 * density).toInt()

            // 检查菜单是否到达顶部
            val menuAtTop = menuParams!!.y <= 0

            // 检查小球是否到达底部
            val ballAtBottomEdge = paramsA.y + ballA.height >= displayMetrics.heightPixels

            // 检查菜单底部是否到达屏幕底部（菜单高度 + 菜单Y位置）
            val menuBottom = menuParams!!.y + menuHeight
            val menuAtBottom = menuBottom >= displayMetrics.heightPixels

            // 标记是否已经限制了Y方向移动（用于保持小球和菜单的同步）
            var yMovementLocked = false

            // 如果菜单到达顶部且向上移动，不允许移动
            if (menuAtTop && finalDeltaY < 0) {
                finalDeltaY = 0
                yMovementLocked = true
            }

            // 如果小球到达底部或菜单底部到达屏幕底部，且向下移动，不允许移动
            if ((ballAtBottomEdge || menuAtBottom) && finalDeltaY > 0) {
                finalDeltaY = 0
                yMovementLocked = true
            }

            // 检查菜单Y方向边界（菜单X方向保持在屏幕中央，不限制小球X方向移动）
            val newMenuY = menuParams!!.y + finalDeltaY

            // 限制菜单Y方向（但如果已经锁定了Y方向移动，不再修改finalDeltaY）
            if (!yMovementLocked) {
                if (newMenuY < 0) {
                    finalDeltaY = -menuParams!!.y
                } else if (newMenuY + menuHeight > displayMetrics.heightPixels) {
                    finalDeltaY = displayMetrics.heightPixels - menuHeight - menuParams!!.y
                }
            }

            // 重新计算A和B的位置（只考虑Y方向限制，X方向不受菜单影响，允许贴边）
            val adjustedACenterX = (paramsA.x + ballARadius) + finalDeltaX
            val adjustedACenterY = (paramsA.y + ballARadius) + finalDeltaY
            val adjustedAX = (adjustedACenterX - ballARadius).toInt()
            val adjustedAY = (adjustedACenterY - ballARadius).toInt()
            val adjustedBX = (adjustedACenterX - ballBRadius).toInt()
            val adjustedBY = (adjustedACenterY - ballBRadius).toInt()

            paramsA.x = adjustedAX
            paramsA.y = adjustedAY
            paramsB.x = adjustedBX
            paramsB.y = adjustedBY
        } else {
            // 应用位置
            paramsA.x = newAX
            paramsA.y = newAY
            paramsB.x = newBX
            paramsB.y = newBY
        }

        windowManager.updateViewLayout(ballA, paramsA)
        windowManager.updateViewLayout(ballB, paramsB)

        // 更新 B 球中心位置（重要！）
        ballBCenterX = paramsB.x + ballB.width / 2f
        ballBCenterY = paramsB.y + ballB.height / 2f

        // 如果菜单显示，菜单Y方向跟随小球移动，X方向保持在屏幕中央（允许小球左右移动用于贴边）
        if (isMenuShown && menuView != null && menuParams != null) {
            menuParams?.let { params ->
                // 菜单Y方向跟随小球移动
                params.y += finalDeltaY

                // 菜单X方向保持在屏幕中央（不跟随小球左右移动，允许小球贴边）
                val menuWidth = if (menuView!!.measuredWidth > 0) menuView!!.measuredWidth else (240 * context.resources.displayMetrics.density).toInt()
                params.x = (displayMetrics.widthPixels - menuWidth) / 2

                try {
                    windowManager.updateViewLayout(menuView, params)
                } catch (e: Exception) {
                    Log.e(LogTags.FLOATING_CONTROLLER, "更新菜单位置失败: ${e.message}")
                }
            }
        }

        lastRawX = event.rawX
        lastRawY = event.rawY

        Log.d(LogTags.FLOATING_CONTROLLER, "🔄 拖动 A+B: Δ($finalDeltaX, $finalDeltaY), A中心=(${paramsA.x + ballARadius}, ${paramsA.y + ballARadius}), B中心=($ballBCenterX, $ballBCenterY)")
    }

    /**
     * 长按拖动：A 球跟随手指移动，B 球不动，识别方向（上下左右，90°划分）
     * 保持按下时手指相对于A球中心的偏移量，确保跟手
     */
    private fun moveAAroundB(event: MotionEvent) {
        val displayMetrics = context.resources.displayMetrics

        // 手指位置
        val fingerX = event.rawX
        val fingerY = event.rawY

        // 计算小球中心应该在的位置（手指位置 - 按下时的偏移量）
        // 这样可以保持按下时的相对位置关系
        val ballARadius = ballA.width / 2f
        val density = context.resources.displayMetrics.density
        val newACenterX = fingerX - downOffsetX
        val newACenterY = fingerY - downOffsetY

        // 计算边界限制（考虑小球半径）
        val minX = ballARadius
        val maxX = displayMetrics.widthPixels - ballARadius
        val maxY = displayMetrics.heightPixels - ballARadius

        // 限制小球中心位置（屏幕边界）
        var clampedACenterX = newACenterX.coerceIn(minX, maxX)
        var clampedACenterY = newACenterY.coerceIn(ballARadius, maxY)

        // 限制小球距离B球中心的最大距离（40dp）
        val maxDistancePx = MAX_DISTANCE_FROM_B_DP * density
        val dx = clampedACenterX - ballBCenterX
        val dy = clampedACenterY - ballBCenterY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        // 方向识别和扇形区域触感反馈（仅在长按拖动模式下）
        // 只有距离足够远时才触发触感，避免在圆心附近频繁触发
        val directionThreshold = DIRECTION_THRESHOLD_DP * density
        if (distance > directionThreshold && hapticEnabled) {
            val direction = detectDirection(dx, dy)
            if (direction != null) {
                // 只有切换到不同的扇形区域时才重置状态
                if (direction != lastHapticDirection) {
                    // 进入新的扇形区域，记录进入时间，重置触感标记
                    directionEnterTime = System.currentTimeMillis()
                    lastHapticDirection = direction
                    hasTriggeredHapticInCurrentDirection = false  // 重置触感标记

                    val angleRad = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
                    val angleDeg = Math.toDegrees(angleRad).toInt()
                    Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                        "🎯 进入扇形区域: $direction → ${direction.actionName} (dx=${dx.toInt()}, dy=${dy.toInt()}, 角度=${angleDeg}°, 距离=${distance.toInt()}px)")
                } else if (!hasTriggeredHapticInCurrentDirection) {
                    // 在同一扇形区域内，检查是否需要触发延迟触感
                    val timeInDirection = System.currentTimeMillis() - directionEnterTime
                    if (timeInDirection >= DIRECTION_HAPTIC_DELAY_MS) {
                        // 在扇形区域内停留超过延迟时间，触发触感（只触发一次）
                        performHapticFeedbackCompat(HapticFeedbackConstants.LONG_PRESS)
                        hasTriggeredHapticInCurrentDirection = true
                        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                            "🔔 扇形区域触感触发: $direction → ${direction.actionName} (停留${timeInDirection}ms)")
                    }
                }

                // 更新检测到的方向（用于日志）
                if (direction != detectedDirection) {
                    detectedDirection = direction
                }
            }
        } else {
            // 距离太近，重置扇形触感状态（避免在圆心附近频繁触发）
            if (lastHapticDirection != null) {
                lastHapticDirection = null
                directionEnterTime = 0L
                hasTriggeredHapticInCurrentDirection = false
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "↩️ 回到圆心附近（距离=${distance.toInt()}px < ${directionThreshold.toInt()}px），重置扇形触感状态")
            }
        }

        if (distance > maxDistancePx) {
            // 如果超过最大距离，限制在最大距离范围内
            val scale = maxDistancePx / distance
            clampedACenterX = ballBCenterX + dx * scale
            clampedACenterY = ballBCenterY + dy * scale

            // 再次检查屏幕边界（因为限制距离后可能超出屏幕）
            clampedACenterX = clampedACenterX.coerceIn(minX, maxX)
            clampedACenterY = clampedACenterY.coerceIn(ballARadius, maxY)
        }

        // 计算小球左上角位置
        val newAX = (clampedACenterX - ballARadius).toInt()
        val newAY = (clampedACenterY - ballARadius).toInt()

        // 应用位置
        paramsA.x = newAX
        paramsA.y = newAY
        windowManager.updateViewLayout(ballA, paramsA)

        // 计算最终距离（用于日志）
        val finalDx = clampedACenterX - ballBCenterX
        val finalDy = clampedACenterY - ballBCenterY
        val finalDistance = hypot(finalDx.toDouble(), finalDy.toDouble()).toFloat()

        // 更新最后位置
        lastRawX = event.rawX
        lastRawY = event.rawY

        Log.d(LogTags.FLOATING_CONTROLLER,
            "🔁 长按拖动: 手指=(${fingerX.toInt()}, ${fingerY.toInt()}), " +
                    "偏移=(${downOffsetX.toInt()}, ${downOffsetY.toInt()}), " +
                    "A中心=(${clampedACenterX.toInt()}, ${clampedACenterY.toInt()}), " +
                    "A左上角=(${paramsA.x}, ${paramsA.y}), " +
                    "距离B=${finalDistance.toInt()}px/${maxDistancePx.toInt()}px, " +
                    "方向=$detectedDirection")
    }

    /**
     * 检测方向：基于 X 形划分（45°线作为分界）
     *
     * 区域划分（以45°线为边界的扇形区域）：
     * - 上（UP）：左上到中心到右上，即 -135° ~ -45°
     * - 右（RIGHT）：右上到中心到右下，即 -45° ~ 45°
     * - 下（DOWN）：右下到中心到左下，即 45° ~ 135°
     * - 左（LEFT）：左下到中心到左上，即 135° ~ -135°（跨越±180°）
     *
     * 视觉示意：
     *        -135°  -90°  -45°
     *           \   |   /
     *            \ UP  /
     *             \ | /
     *   LEFT ------+------ RIGHT
     *             / | \
     *            / DOWN \
     *           /   |   \
     *        135°  90°  45°
     *
     * @param dx X方向偏移（相对于B球中心，向右为正）
     * @param dy Y方向偏移（相对于B球中心，向下为正）
     * @return 识别的方向
     */
    private fun detectDirection(dx: Float, dy: Float): Direction? {
        if (dx == 0f && dy == 0f) return null

        // 计算角度（-180° ~ 180°）
        // atan2(dy, dx): 右=0°, 右下=45°, 下=90°, 左下=135°, 左=±180°, 左上=-135°, 上=-90°, 右上=-45°
        val angleRad = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
        val angleDeg = Math.toDegrees(angleRad)

        // 以45°线为边界划分四个扇形区域
        return when {
            angleDeg >= -45 && angleDeg < 45 -> Direction.RIGHT    // -45° ~ 45°（右上到右下）
            angleDeg in 45.0..<135.0 -> Direction.DOWN     // 45° ~ 135°（右下到左下）
            angleDeg >= 135 || angleDeg < -135 -> Direction.LEFT   // 135° ~ -135°（左下到左上，跨越±180°）
            else -> Direction.UP                                    // -135° ~ -45°（左上到右上）
        }
    }

    /**
     * 归位：A 球回到 B 球中心
     */
    private fun resetAPosition() {
        // 重新计算 B 球中心（确保准确）
        ballBCenterX = paramsB.x + ballB.width / 2f
        ballBCenterY = paramsB.y + ballB.height / 2f

        // 计算 A 球应该在的位置（左上角坐标）
        val targetX = (ballBCenterX - ballA.width / 2f).toInt()
        val targetY = (ballBCenterY - ballA.height / 2f).toInt()

        // 计算归位后 A 球的中心位置（用于验证）
        val targetACenterX = targetX + ballA.width / 2f
        val targetACenterY = targetY + ballA.height / 2f

        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
            "🎯 开始归位: A从(${paramsA.x}, ${paramsA.y}) → ($targetX, $targetY), " +
                    "B中心=($ballBCenterX, $ballBCenterY), 归位后A中心=($targetACenterX, $targetACenterY)")

        // 使用 ValueAnimator 实现平滑归位
        val startX = paramsA.x
        val startY = paramsA.y

        resetAnimator?.cancel() // 取消之前的动画
        resetAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = RESET_ANIMATION_DURATION_MS
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                paramsA.x = (startX + (targetX - startX) * fraction).toInt()
                paramsA.y = (startY + (targetY - startY) * fraction).toInt()
                try {
                    windowManager.updateViewLayout(ballA, paramsA)
                } catch (e: Exception) {
                    Log.e(LogTags.FLOATING_CONTROLLER, "归位动画更新失败: ${e.message}")
                    cancel()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 动画结束后，验证位置
                    val finalACenterX = paramsA.x + ballA.width / 2f
                    val finalACenterY = paramsA.y + ballA.height / 2f
                    Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                        "✅ 归位完成: A左上角=(${paramsA.x}, ${paramsA.y}), " +
                                "A中心=($finalACenterX, $finalACenterY), B中心=($ballBCenterX, $ballBCenterY)")
                }
            })
            start()
        }
    }

    /**
     * 对齐球体：确保A球和B球中心对齐
     */
    private fun alignBalls() {
        val displayMetrics = context.resources.displayMetrics

        // 计算A球中心位置
        val ballARadius = ballA.width / 2f
        val ballBRadius = ballB.width / 2f
        val ballACenterX = paramsA.x + ballARadius
        val ballACenterY = paramsA.y + ballARadius

        // B球中心应该与A球中心对齐
        val ballBCenterX = ballACenterX

        // 计算B球的左上角位置
        val newBX = (ballBCenterX - ballBRadius).toInt()
        val newBY = (ballACenterY - ballBRadius).toInt()

        // 检查边界限制
        val minX = ballBRadius
        val maxX = displayMetrics.widthPixels - ballBRadius
        val maxY = displayMetrics.heightPixels - ballBRadius

        // 如果B球超出边界，调整A球位置（让A球跟随B球）
        val clampedBCenterX = ballBCenterX.coerceIn(minX, maxX)
        val clampedBCenterY = ballACenterY.coerceIn(ballBRadius, maxY)

        if (clampedBCenterX != ballBCenterX || clampedBCenterY != ballACenterY) {
            // B球被边界限制，调整A球位置以保持对齐
            val adjustedACenterX = clampedBCenterX
            val adjustedAX = (adjustedACenterX - ballARadius).toInt()
            val adjustedAY = (clampedBCenterY - ballARadius).toInt()

            paramsA.x = adjustedAX
            paramsA.y = adjustedAY
            paramsB.x = (clampedBCenterX - ballBRadius).toInt()
            paramsB.y = (clampedBCenterY - ballBRadius).toInt()
        } else {
            // 直接对齐
            paramsB.x = newBX
            paramsB.y = newBY
        }

        // 更新视图
        windowManager.updateViewLayout(ballA, paramsA)
        windowManager.updateViewLayout(ballB, paramsB)

        // 更新B球中心位置
        this.ballBCenterX = paramsB.x + ballB.width / 2f
        this.ballBCenterY = paramsB.y + ballB.height / 2f

        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
            "🔧 对齐完成: A中心=(${paramsA.x + ballARadius}, ${paramsA.y + ballARadius}), " +
                    "B中心=($ballBCenterX, $ballACenterY)")
    }

    /**
     * 贴边隐藏：手动拖到屏幕边缘时才贴边，隐藏2/3，露出1/3
     */
    private fun snapToEdge() {
        val displayMetrics = context.resources.displayMetrics
        val density = context.resources.displayMetrics.density

        // 计算小球边缘到屏幕边缘的距离（不是中心距离）
        val ballLeftEdge = paramsA.x.toFloat()
        val ballRightEdge = paramsA.x + ballA.width
        val ballTopEdge = paramsA.y.toFloat()
        val ballBottomEdge = paramsA.y + ballA.height

        // 计算小球边缘到各屏幕边缘的距离（统一为Float类型）
        val distanceToLeft = ballLeftEdge
        val distanceToRight = (displayMetrics.widthPixels - ballRightEdge).toFloat()
        val distanceToBottom = (displayMetrics.heightPixels - ballBottomEdge).toFloat()

        // 找到最近的边和距离
        val distances = listOf(
            distanceToLeft to Edge.LEFT,
            distanceToRight to Edge.RIGHT,
            ballTopEdge to Edge.TOP,
            distanceToBottom to Edge.BOTTOM
        )
        val minPair = distances.minByOrNull { (distance, _) -> distance } ?: return
        val minDistance: Float = minPair.first
        val edge: Edge = minPair.second

        // 贴边阈值：只有当小球边缘真正接近屏幕边缘时才贴边（阈值很小）
        val snapThreshold = EDGE_SNAP_THRESHOLD_DP * density

        // 只有距离边缘小于阈值时才贴边
        if (minDistance > snapThreshold) {
            Log.d(LogTags.FLOATING_CONTROLLER_MSG, "🚫 距离边缘${minDistance.toInt()}px，不贴边（阈值${snapThreshold.toInt()}px）")
            return
        }

        // 贴边时露出的宽度：隐藏2/3，露出1/3
        // 小球直径45dp，露出15dp，隐藏30dp
        val visibleWidth = EDGE_VISIBLE_WIDTH_DP * density
        // 计算目标位置（大部分隐藏在屏幕外）
        // 小球右下角应该在 visibleWidth 的位置，这样左上角在 visibleWidth - ballA.width
        val targetX: Int
        val targetY: Int
        val targetBX: Int
        val targetBY: Int

        when (edge) {
            Edge.LEFT -> {
                // 贴左边：小球右下角在 visibleWidth，左上角在 visibleWidth - ballA.width（可能是负数）
                // 小球中心应该在 visibleWidth - ballARadius
                targetX = (visibleWidth - ballA.width).toInt()
                targetY = paramsA.y
                targetBX = (visibleWidth - ballB.width).toInt()
                targetBY = paramsB.y
            }
            Edge.RIGHT -> {
                // 贴右边：小球左上角在 width - visibleWidth，右下角在 width - visibleWidth + ballA.width
                // 小球中心应该在 width - visibleWidth + ballARadius
                targetX = (displayMetrics.widthPixels - visibleWidth).toInt()
                targetY = paramsA.y
                targetBX = (displayMetrics.widthPixels - visibleWidth).toInt()
                targetBY = paramsB.y
            }
            Edge.TOP -> {
                // 贴顶部：小球右下角在 visibleWidth，左上角在 visibleWidth - ballA.height
                targetX = paramsA.x
                targetY = (visibleWidth - ballA.height).toInt()
                targetBX = paramsB.x
                targetBY = (visibleWidth - ballB.height).toInt()
            }
            Edge.BOTTOM -> {
                // 贴底部：小球左上角在 height - visibleWidth，右下角在 height - visibleWidth + ballA.height
                targetX = paramsA.x
                targetY = (displayMetrics.heightPixels - visibleWidth).toInt()
                targetBX = paramsB.x
                targetBY = (displayMetrics.heightPixels - visibleWidth).toInt()
            }
        }

        // 标记为已贴边
        isSnappedToEdge = true
        snappedEdge = edge

        // 计算实际露出的宽度（用于验证）
        val actualVisibleWidth = when (edge) {
            Edge.LEFT -> targetX + ballA.width
            Edge.RIGHT -> displayMetrics.widthPixels - targetX
            Edge.TOP -> targetY + ballA.height
            Edge.BOTTOM -> displayMetrics.heightPixels - targetY
        }

        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
            "🧲 贴边${edge.name}: 从(${paramsA.x}, ${paramsA.y}) → ($targetX, $targetY), " +
                    "目标露出=${EDGE_VISIBLE_WIDTH_DP}dp(${(EDGE_VISIBLE_WIDTH_DP * density).toInt()}px), " +
                    "实际露出=${actualVisibleWidth}px, " +
                    "小球大小=${ballA.width}px")

        // 使用动画平滑移动到边缘
        val startAX = paramsA.x
        val startAY = paramsA.y
        val startBX = paramsB.x
        val startBY = paramsB.y

        // 记录菜单初始位置（如果菜单显示）
        val startMenuX = if (isMenuShown && menuParams != null) menuParams!!.x else 0
        val startMenuY = if (isMenuShown && menuParams != null) menuParams!!.y else 0

        resetAnimator?.cancel()
        resetAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float

                // 移动小球A
                paramsA.x = (startAX + (targetX - startAX) * fraction).toInt()
                paramsA.y = (startAY + (targetY - startAY) * fraction).toInt()

                // 移动大球B
                paramsB.x = (startBX + (targetBX - startBX) * fraction).toInt()
                paramsB.y = (startBY + (targetBY - startBY) * fraction).toInt()

                // 更新 B 球中心位置
                ballBCenterX = paramsB.x + ballB.width / 2f
                ballBCenterY = paramsB.y + ballB.height / 2f

                try {
                    windowManager.updateViewLayout(ballA, paramsA)
                    windowManager.updateViewLayout(ballB, paramsB)

                    // 如果菜单显示，菜单跟随小球贴边
                    if (isMenuShown && menuView != null && menuParams != null) {
                        menuParams?.let { params ->
                            // 菜单跟随小球移动（使用绝对位置，避免累积误差）
                            params.x = (startMenuX + (targetX - startAX) * fraction).toInt()
                            params.y = (startMenuY + (targetY - startAY) * fraction).toInt()
                            windowManager.updateViewLayout(menuView, params)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(LogTags.FLOATING_CONTROLLER, "贴边动画更新失败: ${e.message}")
                    cancel()
                }
            }
            start()
        }
    }

    /**
     * 清理资源：移除菜单、取消动画
     */
    fun cleanup() {
        // 1. 停止归位 / 吸附动画
        resetAnimator?.cancel()
        resetAnimator = null

        // 2. 取消长按检测
        longPressRunnable?.let { runnable -> longPressHandler?.removeCallbacks(runnable) }
        longPressRunnable = null
        longPressHandler = null

        // 3. 移除菜单（安全 + 可观测）
        if (isMenuShown && menuView != null) {
            try {
                windowManager.removeView(menuView)
                Log.d(LogTags.FLOATING_CONTROLLER_MSG, "✅ 菜单已移除")
            } catch (e: Exception) {
                Log.e(
                    LogTags.FLOATING_CONTROLLER,
                    "❌ 移除菜单失败: ${e.message}"
                )
            }
        }
        menuView = null
        menuParams = null
        isMenuShown = false

        // 4. 移除悬浮球本体（兜底，避免生命周期炸）
        try {
            windowManager.removeViewImmediate(ballA)
        } catch (_: Exception) {}

        try {
            windowManager.removeViewImmediate(ballB)
        } catch (_: Exception) {}
    }
}


/**
 * 屏幕旋转时重新定位小球
 * 旋转后屏幕宽高互换，原坐标可能超出范围导致小球不可见
 * 策略：等待 1 秒后再移动到右边/底部，避免旋转动画期间轨迹不好看
 * - 横屏：右侧居中
 * - 竖屏：底部居中
 */
private fun repositionBallsOnRotation(
    context: Context,
    reference: Tuple4<View, View, WindowManager, GestureHandler>
) {
    val (ballA, ballB, _, _) = reference
    val displayMetrics = context.resources.displayMetrics

    // 判断当前屏幕方向
    val isLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels

    // 获取当前小球位置
    val paramsA = ballA.layoutParams as WindowManager.LayoutParams
    val paramsB = ballB.layoutParams as WindowManager.LayoutParams

    Log.d(LogTags.FLOATING_CONTROLLER_MSG,
        "🔄 屏幕旋转检测 (${if (isLandscape) "横屏" else "竖屏"})，当前小球位置: A=(${paramsA.x}, ${paramsA.y}), B=(${paramsB.x}, ${paramsB.y})")

    // TODO: 屏幕旋转，增强用户体验，让小球移动到底部/右侧
    /*
    val ballACenterOffsetX = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f
    val ballACenterOffsetY = (BALL_B_SIZE_DP - BALL_A_SIZE_DP) * density / 2f

    Log.d(LogTags.FLOATING_CONTROLLER_MSG,
        "🔄 屏幕旋转检测 (${if (isLandscape) "横屏" else "竖屏"})，等待 1 秒后重新定位小球")

    // 等待 1 秒后再移动小球
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        // 计算目标位置（屏幕内）
        val targetBX: Float
        val targetBY: Float

        if (isLandscape) {
            // 横屏：右侧居中，距离右边缘 20dp
            targetBX = displayMetrics.widthPixels - 20 * density - BALL_B_SIZE_DP * density
            targetBY = (displayMetrics.heightPixels - BALL_B_SIZE_DP * density) / 2f
        } else {
            // 竖屏：底部居中，距离底部 85dp
            targetBX = (displayMetrics.widthPixels - BALL_B_SIZE_DP * density) / 2f
            targetBY = displayMetrics.heightPixels - 85 * density - BALL_B_SIZE_DP * density
        }

        val targetAX = targetBX + ballACenterOffsetX
        val targetAY = targetBY + ballACenterOffsetY

        Log.d(LogTags.FLOATING_CONTROLLER_MSG,
            "🔄 开始重定位: 从(${paramsB.x}, ${paramsB.y}) → (${targetBX.toInt()}, ${targetBY.toInt()}) (${if (isLandscape) "横屏右侧" else "竖屏底部"})")

        // 平滑移动到目标位置
        val startAX = paramsA.x
        val startAY = paramsA.y
        val startBX = paramsB.x
        val startBY = paramsB.y

        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350L
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float

                paramsA.x = (startAX + (targetAX - startAX) * fraction).toInt()
                paramsA.y = (startAY + (targetAY - startAY) * fraction).toInt()
                paramsB.x = (startBX + (targetBX - startBX) * fraction).toInt()
                paramsB.y = (startBY + (targetBY - startBY) * fraction).toInt()

                try {
                    windowManager.updateViewLayout(ballA, paramsA)
                    windowManager.updateViewLayout(ballB, paramsB)
                } catch (e: Exception) {
                    Log.e(LogTags.FLOATING_CONTROLLER, "重定位动画失败: ${e.message}")
                    cancel()
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    Log.d(LogTags.FLOATING_CONTROLLER_MSG,
                        "✅ 重定位完成: A=(${paramsA.x}, ${paramsA.y}), B=(${paramsB.x}, ${paramsB.y})")
                }
            })
            start()
        }
    }, 1000L) // 延迟 1 秒
    */
}
