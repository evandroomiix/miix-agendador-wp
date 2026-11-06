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
    val TARGET_PACKAGES = listOf(
        // WhatsApp Oficial e Mods
        Triple("com.whatsapp", "WhatsApp Oficial", "WhatsApp"),
        Triple("com.whatsapp.w4b", "WhatsApp Business", "WhatsApp"),
        Triple("com.gbwhatsapp", "GBWhatsApp", "WhatsApp Mod"),
        Triple("com.fmwhatsapp", "FMWhatsApp", "WhatsApp Mod"),
        Triple("com.yowhatsapp", "YoWhatsApp", "WhatsApp Mod"),
        Triple("com.aero", "WhatsApp Aero", "WhatsApp Mod"),
        Triple("com.whatsapp.plus", "WhatsApp Plus", "WhatsApp Mod"),
        
        // Instagram Oficial e Mods
        Triple("com.instagram.android", "Instagram Oficial", "Instagram"),
        Triple("com.instapro.android", "InstaPro Mod", "Instagram Mod"),
        Triple("com.instapro2.android", "InstaPro 2 Mod", "Instagram Mod"),
        Triple("com.instamod.android", "InstaMod", "Instagram Mod"),
        Triple("com.gbinsta", "GBInstagram", "Instagram Mod"),
        Triple("com.instander.android", "Instander Mod", "Instagram Mod"),
        Triple("com.honista.app", "Honista Mod", "Instagram Mod"),
        Triple("com.aeroinsta.android", "AeroInsta Mod", "Instagram Mod"),
        
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

    fun getInstalledInstagramPackage(context: Context): String {
        val instaPackages = listOf(
            "com.instagram.android",
            "com.instapro.android",
            "com.instapro2.android",
            "com.instamod.android",
            "com.gbinsta",
            "com.instander.android",
            "com.honista.app",
            "com.aeroinsta.android"
        )
        return instaPackages.firstOrNull { isAppInstalled(context, it) } ?: "com.instagram.android"
    }

    fun getInstalledWhatsAppModPackage(context: Context): String {
        val modPackages = listOf(
            "com.gbwhatsapp",
            "com.fmwhatsapp",
            "com.yowhatsapp",
            "com.aero",
            "com.whatsapp.plus"
        )
        return modPackages.firstOrNull { isAppInstalled(context, it) } ?: "com.whatsapp"
    }
}
