package com.emon.alignaipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import android.app.Application

private val Night = Color(0xFF070B18)
private val Panel = Color(0xFF111A2E)
private val Cyan = Color(0xFF42F5E8)
private val Violet = Color(0xFFB56BFF)
private val Muted = Color(0xFF9AAAC4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("align_ai_settings", Context.MODE_PRIVATE)
        setContent {
            var autoBuild by remember { mutableStateOf(prefs.getBoolean("auto_build", false)) }
            AlignTheme {
                Surface(Modifier.fillMaxSize(), color = Night) {
                    Column(
                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101A35), Night)))
                            .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            AppMark(58)
                            Column {
                                Text("ALIGN AI PRO", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                                Text("PRECISION ALIGNMENT SYSTEM", color = Cyan, fontSize = 10.sp, letterSpacing = 1.6.sp)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("LIVE ALIGNMENT", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("--", color = Cyan, fontSize = 52.sp, fontWeight = FontWeight.Black)
                                    Text(" %", color = Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 9.dp))
                                    Spacer(Modifier.weight(1f))
                                    AssistChip(onClick = {}, label = { Text("WAITING FOR SCREEN") }, leadingIcon = { Icon(Icons.Default.CenterFocusStrong, null) })
                                }
                                Text("Capture and detect targets to begin measurement.", color = Muted, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TargetPill("TARGET A", Cyan)
                                    TargetPill("TARGET B", Violet)
                                }
                            }
                        }
                        Text("ALIGNMENT ENGINES", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        EngineCard("01", "Horizontal Position Engine", "Measures left/right displacement", Cyan)
                        EngineCard("02", "Rotation Engine", "Measures angle and tilt difference", Violet)
                        EngineCard("03", "Predictive Landing Engine", "Estimates landing alignment and risk", Color(0xFFFFC76A))
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
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
                                Text(if (autoBuild) "ENABLED · safety validation still required" else "DISABLED · manual measurement mode", color = if (autoBuild) Cyan else Muted, fontSize = 12.sp)
                                Text("Auto-BUILD cannot bypass screen-capture/accessibility permission prompts or validation checks.", color = Muted, fontSize = 11.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("HISTORY", "LOCAL", Icons.Default.History, Modifier.weight(1f))
                            StatCard("PROFILE", "PRO", Icons.Default.Tune, Modifier.weight(1f))
                        }
                        Text("ALIGN AI PRO  •  v1.0.0", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

@Composable private fun AlignTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, secondary = Violet, background = Night, surface = Panel), content = content)
}

@Composable private fun AppMark(size: Int) {
    Box(Modifier.size(size.dp).background(Brush.linearGradient(listOf(Cyan, Violet)), RoundedCornerShape(18.dp)).padding(2.dp)
        .background(Night, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.CenterFocusStrong, null, tint = Cyan, modifier = Modifier.size((size * .62f).dp))
    }
}

@Composable private fun TargetPill(label: String, color: Color) {
    Row(Modifier.border(1.dp, color.copy(alpha=.7f), RoundedCornerShape(50)).padding(horizontal=12.dp, vertical=7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(7.dp))
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
