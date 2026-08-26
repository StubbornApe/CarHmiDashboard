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

    // ---- 刻度（Day 5 视觉层级升级：主刻度拉长加粗、副刻度弱化） ----
    const val MAJOR_TICK_LENGTH_DP = 36f     // 主刻度长度（原 30 → 更长）
    const val MAJOR_TICK_WIDTH_DP = 6f       // 主刻度线宽（原 5 → 更粗）
    const val MINOR_TICK_LENGTH_DP = 16f     // 副刻度长度（不变，保持弱化）
    const val MINOR_TICK_WIDTH_DP = 2f       // 副刻度线宽（原 3 → 更细，拉开层级差）

    // ---- 数字与中心点 ----
    const val NUMBER_TEXT_SIZE_SP = 26f      // 数字字号（sp）
    const val NUMBER_GAP_DP = 16f            // 数字到主刻度内侧的间距
    const val CENTER_CIRCLE_RADIUS_DP = 12f  // 轴心外圆半径
    const val CENTER_CIRCLE_INNER_DP = 5f    // 轴心内圆半径

    // ---- 指针（Day 3 新增；Day 5 刀锋化） ----
    const val POINTER_LENGTH_DP = 240f       // 指针头端长度（自轴心向外）
    const val POINTER_TAIL_LENGTH_DP = 48f   // 指针配重尾长度（自轴心反向）
    const val POINTER_WIDTH_DP = 5f         // 指针最宽处半宽

    // ---- 轴心帽（Day 5 方案 C：机芯化） ----
    const val CENTER_RING_WIDTH_DP = 2.5f    // 轴心金属环线宽
    const val CENTER_INNER_RADIUS_DP = 8f    // 轴心内芯半径
    const val CENTER_BASE_RADIUS_DP = 7f     // 指针根部底衬半径（尾端过渡）
    const val CENTER_HIGHLIGHT_RADIUS_DP = 2f  // 内芯高光点半径
    const val CENTER_HIGHLIGHT_DX_DP = 3f    // 高光点偏移 x（左上）
    const val CENTER_HIGHLIGHT_DY_DP = 3f    // 高光点偏移 y（左上）

    // ---- 触摸有效环带（Day 3 新增，3.5 使用） ----
    const val TOUCH_RING_INNER_DP = 60f      // 有效触摸内半径（更靠里视为按在轴心，忽略）
    const val TOUCH_RING_OUTER_SLOP_DP = 40f // 允许超出外环的容差（手指略画出表盘也算有效）

    // ---- 表盘内单位 "km/h"（Day 3 新增） ----
    const val UNIT_TEXT_SIZE_SP = 30f        // 单位字号
    const val UNIT_CENTER_OFFSET_DP = 100f    // 距轴心圆心的纵向偏移（画在轴心下方）

    // ---- 当前车速大数字（Day 5：显示在 90° 正下方、弧内侧偏上） ----
    const val SPEED_VALUE_TEXT_SIZE_SP = 56f      // 当前车速大号数字字号
    const val SPEED_VALUE_ARC_GAP_DP = 28f        // 大数字中心距刻度数字环的额外内缩间距
    const val SPEED_VALUE_LIFT_DP = 16f           // 从弧内侧再向圆心"上提"的间距
}