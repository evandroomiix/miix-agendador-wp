package com.miix.agendadorwp.service

import android.content.Context
import android.content.pm.PackageManager

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val category: String, // "WhatsApp", "Instagram", "Facebook", "Meta"
    val isInstalled: Boolean
)

object PlatformHelper {

    // Lista de pacotes conhecidos (oficiais e mods populares)
    private val TARGET_PACKAGES = listOf(
        // WhatsApp
        Triple("com.whatsapp", "WhatsApp Oficial", "WhatsApp"),
        Triple("com.whatsapp.w4b", "WhatsApp Business", "WhatsApp"),
        Triple("com.gbwhatsapp", "GBWhatsApp", "WhatsApp Mod"),
        Triple("com.fmwhatsapp", "FMWhatsApp", "WhatsApp Mod"),
        Triple("com.yowhatsapp", "YoWhatsApp", "WhatsApp Mod"),
        Triple("com.aero", "WhatsApp Aero", "WhatsApp Mod"),
        
        // Instagram
        Triple("com.instagram.android", "Instagram Oficial", "Instagram"),
        Triple("com.instander.android", "Instander Mod", "Instagram"),
        Triple("com.honista.app", "Honista Mod", "Instagram"),
        Triple("com.aeroinsta.android", "AeroInsta Mod", "Instagram"),
        
        // Facebook & Meta
        Triple("com.facebook.katana", "Facebook Oficial", "Facebook"),
        Triple("com.facebook.lite", "Facebook Lite", "Facebook"),
        Triple("com.facebook.pages.app", "Meta Business Suite", "Meta Business")
    )

    fun getInstalledApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        return TARGET_PACKAGES.map { (pkg, name, category) ->
            val isInstalled = try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
            InstalledAppInfo(pkg, name, category, isInstalled)
        }
    }

    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
