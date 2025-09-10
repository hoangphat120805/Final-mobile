package com.example.vaiche_driver.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.fragment.app.FragmentActivity
import kotlin.system.exitProcess

/**
 * Thoát hẳn app và mở lại launcher activity (activity có MAIN/LAUNCHER).
 */
fun FragmentActivity.forceQuitAndReopenApp() {
    val ctx: Context = this

    // Lấy intent của launcher activity (không cần biết LoginActivity/SplashActivity là gì)
    val relaunchIntent: Intent =
        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            ?: Intent() // fallback, gần như không dùng tới

    val piFlags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val pi = PendingIntent.getActivity(ctx, 98765, relaunchIntent, piFlags)

    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAt = System.currentTimeMillis() + 200 // 200ms

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
    } else {
        am.setExact(AlarmManager.RTC, triggerAt, pi)
    }

    // Đóng task hiện tại rồi kill process
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        finishAndRemoveTask()
    } else {
        finishAffinity()
    }
    Process.killProcess(Process.myPid())
    exitProcess(0)
}
