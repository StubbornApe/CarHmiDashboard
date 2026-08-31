package com.example.carhmi.hmi

/**
 * 插值器（Interpolator）纯逻辑复现（Day 6 新增）。
 * 返回「时间占比 t∈[0,1] → 进度 f(t)」的纯函数，公式与 Android 内置 Interpolator 等价，
 * 用于理解「加速/过冲/蓄力/贝塞尔」的曲线形状，并可 JVM 单测。
 * 项目实际动画仍用 android.animation 的 Interpolator 实例，本类只做纯数学参考（不直接喂给 ValueAnimator）。
 */
object InterpolatorCurves {

    /** 线性匀速：f(t) = t */
    fun linear(t: Float): Float = t.coerceIn(0f, 1f)

    /** 平滑：先慢后快再慢（等价 AccelerateDecelerateInterpolator 的标准三次函数） */
    fun easeInOut(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 3f * x * x - 2f * x * x * x
    }

    /** 过冲：冲过目标再回落（等价 OvershootInterpolator，tension 控制过冲幅度） */
    fun overshoot(t: Float, tension: Float = 2f): Float {
        val x = t.coerceIn(0f, 1f)
        val s = tension
        val t2 = x - 1f
        return t2 * t2 * ((s + 1f) * t2 + s) + 1f
    }

    /** 蓄力：先反向再冲高回落（等价 AnticipateInterpolator，tension 控制蓄力幅度） */
    fun anticipate(t: Float, tension: Float = 2f): Float {
        val x = t.coerceIn(0f, 1f)
        val s = tension
        return x * x * ((s + 1f) * x - s)
    }

    /**
     * 一维贝塞尔采样：用控制点 (x1,y1)(x2,y2) 定义一条 0→1 的曲线，
     * 输入 t 沿曲线取到对应 y 作为进度（等价 PathInterpolator 的采样思想）。
     * t 范围越界会被夹取到 [0,1]。
     */
    fun cubicBezier(t: Float, x1: Float = 0.42f, y1: Float = 0f, x2: Float = 0.58f, y2: Float = 1f): Float {
        val x = t.coerceIn(0f, 1f)
        // 二分法反解：给定 x（时间），在参数 u∈[0,1] 上找到贝塞尔曲线 x(u)=x 的 u，再求 y(u)
        var lo = 0f; var hi = 1f
        repeat(20) {
            val u = (lo + hi) / 2f
            val xu = bezierX(u, x1, x2)
            if (xu < x) lo = u else hi = u
        }
        return bezierY((lo + hi) / 2f, y1, y2)
    }

    /**
     * 动画进度计算（Day 8 新增）：start → end 按 interpolator 取 t 时刻的插值。
     * 这是 ValueAnimator「setCurrentFraction → 当前值」的纯数学内核：
     * interpolator 把时间占比 t∈[0,1] 映射为进度 f(t)，再线性映射回 [start, end]。
     * 过冲/蓄力曲线中途 f 会暂时 >1 或 <0，返回值相应越界——属预期，不是 bug。
     */
    fun progress(start: Float, end: Float, interpolator: (Float) -> Float, t: Float): Float {
        val f = interpolator(t.coerceIn(0f, 1f))
        return start + (end - start) * f
    }

    // 二次形式贝塞尔 x(u) = 3(1-u)^2*u*x1 + 3(1-u)*u^2*x2 + u^3
    private fun bezierX(u: Float, x1: Float, x2: Float): Float {
        val v = 1f - u
        return 3f * v * v * u * x1 + 3f * v * u * u * x2 + u * u * u
    }

    // 二次形式贝塞尔 y(u) = 3(1-u)^2*u*y1 + 3(1-u)*u^2*y2 + u^3
    private fun bezierY(u: Float, y1: Float, y2: Float): Float {
        val v = 1f - u
        return 3f * v * v * u * y1 + 3f * v * u * u * y2 + u * u * u
    }
}
