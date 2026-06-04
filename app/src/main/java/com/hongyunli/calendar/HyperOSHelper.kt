package com.hongyunli.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 小米澎湃OS (HyperOS) 适配工具类
 */
object HyperOSHelper {

    /**
     * 检测当前系统是否为小米澎湃OS
     */
    fun isHyperOS(): Boolean {
        return try {
            val hyperOSVersion = getSystemProperty("ro.mi.os.version.name")
            !hyperOSVersion.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取澎湃OS版本号
     */
    fun getHyperOSVersion(): String? {
        return try {
            getSystemProperty("ro.mi.os.version.name")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检测是否为澎湃OS 3.0+
     */
    fun isHyperOS3OrAbove(): Boolean {
        val version = getHyperOSVersion() ?: return false
        return version.startsWith("3") || version.startsWith("OS3")
    }

    /**
     * 澎湃OS 悬浮窗权限检查
     * 澎湃OS对悬浮窗权限管理更严格，需要特殊处理
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 打开澎湃OS悬浮窗权限设置页面
     * 澎湃OS 3.0 使用特定的权限管理页面
     */
    fun requestOverlayPermission(context: Context) {
        val intent = if (isHyperOS3OrAbove()) {
            // 澎湃OS 3.0 使用小米特有的权限管理
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
        } else {
            // 标准 Android 悬浮窗权限设置
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果小米特定页面打不开，回退到标准页面
            val fallbackIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(fallbackIntent)
        }
    }

    /**
     * 打开澎湃OS后台弹窗权限（显示在其他应用上层）
     */
    fun requestBackgroundStartPermission(context: Context) {
        if (!isHyperOS()) return
        
        try {
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取系统属性
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }
}
