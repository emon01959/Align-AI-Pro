package com.emon.alignaipro

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.content.Context

class AlignAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("align_ai_settings", MODE_PRIVATE).edit().putBoolean("accessibility_connected", true).apply()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* Events are observed only; no automatic taps in this release. */ }
    override fun onInterrupt() {}
    override fun onDestroy() {
        getSharedPreferences("align_ai_settings", MODE_PRIVATE).edit().putBoolean("accessibility_connected", false).apply()
        super.onDestroy()
    }
}
object AccessibilityStatus {
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName && it.resolveInfo.serviceInfo.name == AlignAccessibilityService::class.java.name }
    }
}
