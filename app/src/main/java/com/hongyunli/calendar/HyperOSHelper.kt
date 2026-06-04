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

    // 缓存检测结果，避免重复反射
    private var cachedIsHyperOS: Boolean? = null
    private var cachedVersion: String? = null

    /**
     * 修复5: 检测当前系统是否为小米澎湃OS
     * 使用多重检测：Build信息 + 系统属性反射
     */
    fun isHyperOS(): Boolean {
        cachedIsHyperOS?.let { return it }
        
        val result = try {
            // 方法1: 系统属性反射
            val propVersion = getSystemProperty("ro.mi.os.version.name")
            if (!propVersion.isNullOrEmpty()) {
                true
            } else {
                // 方法2: 通过 Build 信息判断（备选）
                isXiaomiDevice()
            }
        } catch (e: Exception) {
            // 反射失败，使用 Build 信息判断
            isXiaomiDevice()
        }
        
        cachedIsHyperOS = result
        return result
    }

    /**
     * 通过 Build 信息判断是否为小米设备
     */
    private fun isXiaomiDevice(): Boolean {
        return (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Redmi", ignoreCase = true) ||
                Build.BRAND.equals("POCO", ignoreCase = true)) &&
                Build.VERSION.RELEASE.isNotEmpty()
    }

    /**
     * 获取澎湃OS版本号
     */
    fun getHyperOSVersion(): String? {
        cachedVersion?.let { return it }
        
        val version = try {
            getSystemProperty("ro.mi.os.version.name")
        } catch (e: Exception) {
            null
        }
        
        cachedVersion = version
        return version
    }

    /**
     * 检测是否为澎湃OS 3.0+
     */
    fun isHyperOS3OrAbove(): Boolean {
        val version = getHyperOSVersion() ?: return false
        return version.startsWith("3") || 
               version.startsWith("OS3") ||
               version.startsWith("V3") ||
               version.contains("3.0")
    }

    /**
     * 澎湃OS 悬浮窗权限检查
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 打开悬浮窗权限设置页面
     * 修复5: 澎湃OS 3.0 权限页面路径已变更
     */
    fun requestOverlayPermission(context: Context) {
        val intents = mutableListOf<Intent>()
        
        // 方式1: 澎湃OS 3.0 新版权限管理
        if (isHyperOS3OrAbove()) {
            intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            })
        }
        
        // 方式2: MIUI 旧版权限管理
        intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        })
        
        // 方式3: 小米应用设置
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
        
        // 方式4: 标准 Android 悬浮窗权限
        intents.add(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ))
        
        // 依次尝试，第一个成功就返回
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                continue
            }
        }
    }

    /**
     * 获取系统属性（带缓存）
     * 修复5: 增加异常处理，防止澎湃OS 3.0 反射限制
     */
    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.isAccessible = true
            method.invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }
}
