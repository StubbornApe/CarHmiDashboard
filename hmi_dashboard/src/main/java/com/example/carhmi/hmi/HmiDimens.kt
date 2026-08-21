package com.example.carhmi.hmi

/**
 * 车载 HMI 组件库尺寸设计 Token（dp/sp 单位）：
 * 集中管理组件默认尺寸，避免魔法数字；Day 4 引入自定义属性后可在 XML 中覆盖。
 * 参考真实车载 HMI"设计系统"的组织思路：尺寸/颜色/字号收口在统一的 Token 中。
 */
object HmiDimens {

    // ---- 速度表整体 ----
    const val SPEEDOMETER_SIZE_DP = 800f     // 组件测量尺寸
    const val OUTER_RING_MARGIN_DP = 60f     // 外环外缘到 View 边缘的留白
    const val OUTER_RING_WIDTH_DP = 40f      // 外环线宽
    const val ARC_WIDTH_DP = 12f             // 270° 量程弧线宽

    // ---- 刻度 ----
    const val MAJOR_TICK_LENGTH_DP = 30f     // 主刻度长度
    const val MAJOR_TICK_WIDTH_DP = 5f       // 主刻度线宽
    const val MINOR_TICK_LENGTH_DP = 16f     // 副刻度长度
    const val MINOR_TICK_WIDTH_DP = 3f       // 副刻度线宽

    // ---- 数字与中心点 ----
    const val NUMBER_TEXT_SIZE_SP = 26f      // 数字字号（sp）
    const val NUMBER_GAP_DP = 16f            // 数字到主刻度内侧的间距
    const val CENTER_CIRCLE_RADIUS_DP = 12f  // 轴心外圆半径
    const val CENTER_CIRCLE_INNER_DP = 5f    // 轴心内圆半径
}