package com.example

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val packageName: String, val name: String, val icon: Drawable)

class IconPackManager(private val context: Context) {
    // Cache the loaded app filters by icon pack package name
    private val appFilterCache = mutableMapOf<String, Map<String, String>>()
    private val resCache = mutableMapOf<String, android.content.res.Resources>()

    fun getAvailableIconPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val intent = Intent("com.gau.go.launcherex.theme")
        val packs = pm.queryIntentActivities(intent, 0)
        
        val intent2 = Intent("org.adw.launcher.THEMES")
        val packs2 = pm.queryIntentActivities(intent2, 0)
        
        val allPacks = (packs + packs2).distinctBy { it.activityInfo.packageName }
        
        return allPacks.mapNotNull {
            try {
                IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm))
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name.lowercase() }
    }

    fun loadIconPack(packageName: String?) {
        if (packageName == null || packageName.isEmpty()) return
        if (appFilterCache.containsKey(packageName)) return // Already loaded

        val filterMap = mutableMapOf<String, String>()
        try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(packageName)
            resCache[packageName] = res
            
            val resId = res.getIdentifier("appfilter", "xml", packageName)
            if (resId != 0) {
                val parser = res.getXml(resId)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null) {
                            filterMap[component] = drawable
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        appFilterCache[packageName] = filterMap
    }

    fun getIconList(iconPackPackageName: String): List<String> {
        val icons = mutableSetOf<String>()
        try {
            val pm = context.packageManager
            val res = pm.getResourcesForApplication(iconPackPackageName)
            
            // Try drawable.xml first (standard for icon pickers)
            val drawableResId = res.getIdentifier("drawable", "xml", iconPackPackageName)
            if (drawableResId != 0) {
                val parser = res.getXml(drawableResId)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (drawable != null) {
                            icons.add(drawable)
                        }
                    }
                    eventType = parser.next()
                }
            }
            
            // Fallback to appfilter.xml values
            if (icons.isEmpty()) {
                val filterResId = res.getIdentifier("appfilter", "xml", iconPackPackageName)
                if (filterResId != 0) {
                    val parser = res.getXml(filterResId)
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (drawable != null) {
                                icons.add(drawable)
                            }
                        }
                        eventType = parser.next()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return icons.toList().sorted()
    }

    fun getDrawableByName(iconPackPackageName: String, drawableName: String): Drawable? {
        try {
            val res = resCache[iconPackPackageName] ?: context.packageManager.getResourcesForApplication(iconPackPackageName).also { resCache[iconPackPackageName] = it }
            val resId = res.getIdentifier(drawableName, "drawable", iconPackPackageName)
            if (resId != 0) {
                return res.getDrawable(resId, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun getIcon(iconPackPackageName: String?, packageName: String, activityName: String, fallback: Drawable, individualOverrideDrawable: String? = null): Drawable {
        if (iconPackPackageName == null || iconPackPackageName.isEmpty()) return fallback
        
        if (individualOverrideDrawable != null) {
            return getDrawableByName(iconPackPackageName, individualOverrideDrawable) ?: fallback
        }
        
        // Ensure it's loaded
        if (!appFilterCache.containsKey(iconPackPackageName)) {
            loadIconPack(iconPackPackageName)
        }
        
        val filterMap = appFilterCache[iconPackPackageName] ?: return fallback
        val res = resCache[iconPackPackageName] ?: return fallback
        
        val componentNameStr = "ComponentInfo{$packageName/$activityName}"
        val drawableName = filterMap[componentNameStr] ?: return fallback
        
        return try {
            val resId = res.getIdentifier(drawableName, "drawable", iconPackPackageName)
            if (resId != 0) {
                res.getDrawable(resId, null) ?: fallback
            } else {
                fallback
            }
        } catch (e: Exception) {
            fallback
        }
    }
}
