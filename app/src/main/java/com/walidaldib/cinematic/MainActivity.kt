package com.walidaldib.cinematic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WaleedAldibApp() }
    }
}

private val Ink = Color(0xFF101014)
private val Panel = Color(0xFF1B1B22)
private val Gold = Color(0xFFD6B36A)
private val Muted = Color(0xFFAAA7A0)
private val Wine = Color(0xFF7D263D)

@Composable
private fun WaleedAldibApp() {
    var selected by remember { mutableStateOf("الرئيسية") }
    var prompt by remember { mutableStateOf("") }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Gold)) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Gold) {
                listOf("الرئيسية", "إنشاء", "مشاريعي").forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = { selected = item },
                        icon = { Text(when (item) { "الرئيسية" -> "⌂"; "إنشاء" -> "✦"; else -> "▤" }, fontSize = 20.sp) },
                        label = { Text(item) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Gold, selectedTextColor = Gold, indicatorColor = Wine, unselectedIconColor = Muted, unselectedTextColor = Muted)
                    )
                }
            }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Spacer(Modifier.height(12.dp)); Header() }
                item {
                    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF38202B), Color(0xFF17171D))), RoundedCornerShape(24.dp)).border(1.dp, Gold.copy(alpha = .35f), RoundedCornerShape(24.dp)).padding(20.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("استوديو وليد الديب", color = Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("اصنع مشاهدك… من صورة أو من خيالك", color = Color.White, fontSize = 15.sp)
                            Text("مساحة عمل سينمائية للصور والأفكار والمشروعات", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                item { Text(if (selected == "إنشاء") "ابدأ مشروعًا جديدًا" else if (selected == "مشاريعي") "مساحة مشروعاتك" else "أدوات الاستوديو", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                item { FeatureCard("صورة إلى فيديو", "تحريك صورة ثابتة وبناء لقطة سينمائية", "▶", selected == "إنشاء") { selected = "إنشاء" } }
                item { FeatureCard("نص إلى فيديو", "تحويل وصفك إلى مشهد متحرك", "✎", selected == "إنشاء") { selected = "إنشاء" } }
                item { FeatureCard("تحريك المشاهد والشخصيات", "حركة كاميرا وعمق وانتقالات", "✧", selected == "إنشاء") { selected = "إنشاء" } }
                item {
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("وصف المشهد", color = Gold, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("اكتب فكرتك بالعربي أو الإنجليزي…", color = Muted) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Gold, unfocusedBorderColor = Muted.copy(alpha = .4f)))
                        Text("محرك توليد الفيديو لم يُربط بعد. لن يتم عرض زر إنشاء وهمي.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                    }
                }
                item { Text("هذا إصدار تأسيسي للواجهة، وليس مولّد فيديو مكتملًا بعد.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("وليد الديب", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            Text("CINEMATIC AI STUDIO", color = Gold, fontSize = 10.sp, letterSpacing = 2.sp)
        }
        Box(Modifier.size(44.dp).border(1.dp, Gold, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("WD", color = Gold, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, symbol: String, active: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).border(1.dp, if (active) Gold.copy(alpha = .7f) else Color.White.copy(alpha = .06f), RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).background(Wine.copy(alpha = .55f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(symbol, color = Gold, fontSize = 23.sp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Text("‹", color = Gold, fontSize = 25.sp)
    }
}
