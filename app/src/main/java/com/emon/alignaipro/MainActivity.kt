package com.emon.alignaipro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Handler
import android.os.Looper

private val Night = Color(0xFF070B18)
private val Panel = Color(0xFF111A2E)
private val Cyan = Color(0xFF42F5E8)
private val Violet = Color(0xFFB56BFF)
private val Muted = Color(0xFF9AAAC4)

class MainActivity : ComponentActivity() {
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val service = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
            }
            startForegroundService(service)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("align_ai_settings", Context.MODE_PRIVATE)
        setContent {
            var autoBuild by remember { mutableStateOf(prefs.getBoolean("auto_build", false)) }
            var captureOn by remember { mutableStateOf(prefs.getBoolean("capture_running", false)) }
            var frames by remember { mutableIntStateOf(prefs.getInt("frames_seen", 0)) }
            var objects by remember { mutableIntStateOf(prefs.getInt("objects_seen", 0)) }
            var captureError by remember { mutableStateOf(prefs.getString("capture_error", "") ?: "") }
            var accessibilityOn by remember { mutableStateOf(AccessibilityStatus.isEnabled(this)) }
            val handler = remember { Handler(Looper.getMainLooper()) }
            DisposableEffect(Unit) {
                val refresh = object : Runnable {
                    override fun run() {
                        captureOn = prefs.getBoolean("capture_running", false)
                        frames = prefs.getInt("frames_seen", 0)
                        objects = prefs.getInt("objects_seen", 0)
                        captureError = prefs.getString("capture_error", "") ?: ""
                        accessibilityOn = AccessibilityStatus.isEnabled(this@MainActivity)
                        handler.postDelayed(this, 1000)
                    }
                }
                handler.post(refresh)
                onDispose { handler.removeCallbacks(refresh) }
            }
            AlignTheme {
                Surface(Modifier.fillMaxSize(), color = Night) {
                    Column(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A35), Night)))
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AppMark(58)
                            Column {
                                Text("ALIGN AI PRO", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                                Text("PRECISION ALIGNMENT SYSTEM", color = Cyan, fontSize = 10.sp, letterSpacing = 1.6.sp)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("LIVE SCREEN ANALYSIS", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(captureOn)
                                    Text(if (captureOn) "SCREEN CAPTURE ACTIVE" else "SCREEN CAPTURE STOPPED", color = if (captureOn) Cyan else Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Text("Frames processed: $frames     Objects found: $objects", color = Color.White, fontSize = 13.sp)
                                if (captureError.isNotBlank()) Text(captureError, color = Color(0xFFFF8A8A), fontSize = 11.sp)
                                Button(onClick = {
                                    val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    captureLauncher.launch(mgr.createScreenCaptureIntent())
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.CenterFocusStrong, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (captureOn) "Start / Restart Screen Capture" else "Grant Screen Capture & Start")
                                }
                                OutlinedButton(onClick = {
                                    stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
                                    prefs.edit().putBoolean("capture_running", false).apply()
                                }, modifier = Modifier.fillMaxWidth()) { Text("Stop Capture") }
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("ACCESSIBILITY SERVICE", color = Color.White, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(accessibilityOn)
                                    Text(if (accessibilityOn) "ENABLED" else "NOT ENABLED", color = if (accessibilityOn) Cyan else Muted, fontSize = 12.sp)
                                }
                                Text("Open Android Accessibility settings and enable Align AI Pro manually. Android requires your explicit approval.", color = Muted, fontSize = 12.sp)
                                Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Open Accessibility Settings")
                                }
                            }
                        }
                        Text("DETECTION ENGINE", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        EngineCard("01", "Live Object Detection", "On-device ML Kit object detection", Cyan)
                        EngineCard("02", "Target A / B Assignment", "Next stage: identify and lock target anchors", Violet)
                        EngineCard("03", "Alignment Measurement", "Next stage: corners, center, angle and landing", Color(0xFFFFC76A))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, null, tint = Violet)
                                    Text("AUTO-BUILD CONTROL", color = Color.White, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.weight(1f))
                                    Switch(checked = autoBuild, onCheckedChange = {
                                        autoBuild = it
                                        prefs.edit().putBoolean("auto_build", it).apply()
                                    })
                                }
                                Text(if (autoBuild) "ENABLED · execution still safety-locked" else "DISABLED · detection only", color = if (autoBuild) Cyan else Muted, fontSize = 12.sp)
                                Text("Build actions are not yet implemented in this version. Keep OFF until target validation is complete.", color = Muted, fontSize = 11.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("HISTORY", "LOCAL", Icons.Default.History, Modifier.weight(1f))
                            StatCard("PROFILE", "PRO", Icons.Default.Tune, Modifier.weight(1f))
                        }
                        Text("ALIGN AI PRO  •  v1.1.0", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

@Composable private fun AlignTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Night, surface = Panel), content = content)
}
@Composable private fun StatusDot(active: Boolean) {
    Box(Modifier.padding(end = 8.dp).size(9.dp).background(if (active) Cyan else Muted, RoundedCornerShape(50)))
}
@Composable private fun AppMark(size: Int) {
    Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Cyan, Violet)), RoundedCornerShape(18.dp)).padding(2.dp)
        .background(Night, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.CenterFocusStrong, null, tint = Cyan, modifier = Modifier.size((size * .62f).dp))
    }
}
@Composable private fun EngineCard(number: String, name: String, description: String, color: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number, color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(description, color = Muted, fontSize = 11.sp)
            }
        }
    }
}
@Composable private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan)
            Spacer(Modifier.width(8.dp))
            Column { Text(title, color = Muted, fontSize = 9.sp); Text(value, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}
