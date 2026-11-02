package app.renotify.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object InstalledApps {
    fun load(context: Context): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val manager = context.packageManager
        val results = if (Build.VERSION.SDK_INT >= 33) {
            manager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentActivities(intent, 0)
        }

        return results.asSequence()
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                runCatching {
                    AppEntry(
                        packageName = packageName,
                        label = info.loadLabel(manager).toString(),
                        icon = info.loadIcon(manager),
                    )
                }.getOrNull()
            }
            .distinctBy(AppEntry::packageName)
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun label(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)
}
