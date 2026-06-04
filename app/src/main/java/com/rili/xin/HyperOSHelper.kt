package com.rili.xin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 小米澎湃OS (HyperOS) 适配工具类
 */
object HyperOSHelper {

    private var cachedIsHyperOS: Boolean? = null
    private var cachedVersion: String? = null

    fun isHyperOS(): Boolean {
        cachedIsHyperOS?.let { return it }
        
        val result = try {
            val propVersion = getSystemProperty("ro.mi.os.version.name")
            if (!propVersion.isNullOrEmpty()) {
                true
            } else {
                isXiaomiDevice()
            }
        } catch (e: Exception) {
            isXiaomiDevice()
        }
        
        cachedIsHyperOS = result
        return result
    }

    private fun isXiaomiDevice(): Boolean {
        return (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Redmi", ignoreCase = true) ||
                Build.BRAND.equals("POCO", ignoreCase = true)) &&
                Build.VERSION.RELEASE.isNotEmpty()
    }

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

    fun isHyperOS3OrAbove(): Boolean {
        val version = getHyperOSVersion() ?: return false
        return version.startsWith("3") || 
               version.startsWith("OS3") ||
               version.startsWith("V3") ||
               version.contains("3.0")
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestOverlayPermission(context: Context) {
        val intents = mutableListOf<Intent>()
        
        if (isHyperOS3OrAbove()) {
            intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            })
        }
        
        intents.add(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        })
        
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
        
        intents.add(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ))
        
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
