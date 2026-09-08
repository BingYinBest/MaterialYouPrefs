package com.bingyin.materialyouprefs.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a single preference row item.
 */
data class PrefItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

/**
 * Represents a group of preference items with a header title.
 */
data class PrefGroup(
    val title: String,
    val items: List<PrefItem>,
)

/**
 * Static preference data for different screens.
 */
object PrefData {

    val homeGroups = listOf(
        PrefGroup(
            title = "常用设置",
            items = listOf(
                PrefItem("wifi", "Wi-Fi", "连接到无线网络", Icons.Outlined.Wifi),
                PrefItem("bluetooth", "蓝牙", "配对和管理设备", Icons.Outlined.Bluetooth),
                PrefItem("display", "显示", "亮度、深色模式、字体大小", Icons.Outlined.BrightnessMedium),
            ),
        ),
        PrefGroup(
            title = "个人偏好",
            items = listOf(
                PrefItem("notifications", "通知", "管理应用通知和提醒方式", Icons.Outlined.Notifications),
                PrefItem("sound", "声音和振动", "铃声音量、媒体音量", Icons.Outlined.VolumeUp),
                PrefItem("battery", "电池", "电量百分比、省电模式", Icons.Outlined.BatteryFull),
            ),
        ),
        PrefGroup(
            title = "其他",
            items = listOf(
                PrefItem("storage", "存储", "已用空间 128GB / 256GB", Icons.Outlined.Storage),
                PrefItem("apps", "应用管理", "已安装 48 个应用", Icons.Outlined.Apps),
                PrefItem("about", "关于手机", "设备信息和系统版本", Icons.Outlined.Info),
            ),
        ),
    )

    val featureGroups = listOf(
        PrefGroup(
            title = "功能",
            items = listOf(
                PrefItem("camera", "相机", "拍照和录像设置", Icons.Outlined.CameraAlt),
                PrefItem("location", "位置信息", "GPS 和定位服务", Icons.Outlined.LocationOn),
                PrefItem("security", "安全", "屏幕锁定、加密、查找设备", Icons.Outlined.Security),
            ),
        ),
        PrefGroup(
            title = "辅助功能",
            items = listOf(
                PrefItem("accessibility", "无障碍", "视觉增强、互动控制", Icons.Outlined.Accessibility),
                PrefItem("language", "语言和输入", "中文简体", Icons.Outlined.Language),
                PrefItem("accounts", "账户", "已添加 2 个账户", Icons.Outlined.AccountCircle),
            ),
        ),
    )

    val settingsGroups = listOf(
        PrefGroup(
            title = "应用设置",
            items = listOf(
                PrefItem("theme", "主题", "跟随系统动态取色", Icons.Outlined.Palette),
                PrefItem("dark_mode", "深色模式", "跟随系统", Icons.Outlined.DarkMode),
                PrefItem("font_size", "字体大小", "默认", Icons.Outlined.FormatSize),
            ),
        ),
        PrefGroup(
            title = "开发者选项",
            items = listOf(
                PrefItem("debug", "USB调试", "已关闭", Icons.Outlined.BugReport),
                PrefItem("analytics", "诊断数据", "不发送", Icons.Outlined.Analytics),
                PrefItem("version", "版本信息", "v1.0.0 (build 1)", Icons.Outlined.Build),
            ),
        ),
    )
}
