package com.custom.ios18launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppItem(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

object AppRepository {
    fun loadInstalledApps(context: Context): List<AppItem> {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                AppItem(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                    icon = info.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
