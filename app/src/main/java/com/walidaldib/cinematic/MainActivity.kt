package com.walidaldib.cinematic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Studio(this) }
    }
}

private val bg = Color(0xFF101014)
private val panel = Color(0xFF1D1B22)
private val gold = Color(0xFFD6B36A)
private val wine = Color(0xFF7D263D)
private val muted = Color(0xFFAAA7A0)

private data class SavedProject(val name: String, val image: String, val prompt: String, val movement: String, val length: Int, val aspect: String = "عمودي 9:16")
private data class SavedExport(val uri: String, val date: String, val movement: String, val aspect: String, val length: Int)

@Composable
private fun Studio(context: Context) {
    val prefs = remember { context.getSharedPreferences("studio_projects", Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf("الرئيسية") }
    var image by remember { mutableStateOf(prefs.getString("last_image", null)?.let(Uri::parse)) }
    var prompt by remember { mutableStateOf(prefs.getString("prompt", "") ?: "") }
    var movement by remember { mutableStateOf(prefs.getString("movement", "تقريب سينمائي") ?: "تقريب سينمائي") }
    var length by remember { mutableIntStateOf(prefs.getInt("length", 5)) }
    var aspect by remember { mutableStateOf(prefs.getString("aspect", "عمودي 9:16") ?: "عمودي 9:16") }
    var notice by remember { mutableStateOf("الاستوديو جاهز. اختر صورة للبدء.") }
    var projects by remember { mutableStateOf(readProjects(prefs)) }
    var exports by remember { mutableStateOf(readExports(prefs)) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            image = uri
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            prefs.edit().putString("last_image", uri.toString()).apply()
            page = "إنشاء"
            notice = "تم اختيار الصورة. احفظ إعدادات مشروعك."
        }
    }
    if (deleteIndex != null) {
        AlertDialog(
            onDismissRequest = { deleteIndex = null },
            title = { Text("حذف المشروع؟") },
            text = { Text("سيتم حذف المشروع المحفوظ من هذا الهاتف.") },
            confirmButton = { TextButton(onClick = {
                val index = deleteIndex
                if (index != null && index in projects.indices) {
                    projects = projects.toMutableList().also { it.removeAt(index) }
                    writeProjects(prefs, projects)
                    notice = "تم حذف المشروع."
                }
                deleteIndex = null
            }) { Text("حذف", color = Color(0xFFE28B8B)) } },
            dismissButton = { TextButton(onClick = { deleteIndex = null }) { Text("إلغاء") } },
            containerColor = panel,
            titleContentColor = Color.White,
            textContentColor = muted
        )
    }
    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = gold)) {
        Scaffold(containerColor = bg, bottomBar = {
            NavigationBar(containerColor = panel) {
                listOf("الرئيسية", "إنشاء", "مشاريعي", "فيديوهاتي").forEach { tab ->
                    NavigationBarItem(selected = page == tab, onClick = { page = tab },
                        icon = { Text(if (tab == "الرئيسية") "⌂" else if (tab == "إنشاء") "✦" else "▤", color = gold) },
                        label = { Text(tab) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = gold, selectedTextColor = gold, indicatorColor = wine, unselectedTextColor = muted))
                }
            }
        }) { inset ->
            LazyColumn(Modifier.fillMaxSize().padding(inset).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("وليد الديب", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Text("CINEMATIC STUDIO", color = gold, fontSize = 10.sp, letterSpacing = 2.sp) }
                        Box(Modifier.size(46.dp).border(1.dp, gold, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("WD", color = gold, fontWeight = FontWeight.Bold) }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF492536), Color(0xFF1C1A22))), RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("استوديو وليد الديب", color = gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("مساحة لصناعة اللقطات السينمائية من صورك", color = Color.White, fontSize = 14.sp)
                        Text("واجهة عربية • حفظ مشروعات محلي • أدوات حركة", color = muted, fontSize = 11.sp)
                    }
                }
                if (page == "فيديوهاتي") {
                    item { Text("الفيديوهات المحفوظة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    if (exports.isEmpty()) item { Note("لا توجد فيديوهات مصدّرة", "بعد إنشاء فيديو MP4 سيظهر هنا لتشغيله أو مشاركته.") }
                    itemsIndexed(exports) { index, video ->
                        Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("فيديو ${exports.size - index}", color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${video.movement} • ${video.length} ثوانٍ • ${video.aspect}", color = muted, fontSize = 11.sp)
                                Text(video.date, color = muted, fontSize = 10.sp)
                            }
                            Text("تشغيل", color = gold, modifier = Modifier.clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(video.uri), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                                    .onFailure { notice = "تعذر فتح الفيديو على هذا الهاتف." }
                            }.padding(6.dp))
                            Text("مشاركة", color = gold, modifier = Modifier.clickable {
                                val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, Uri.parse(video.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(Intent.createChooser(send, "مشاركة فيديو وليد الديب")) }
                                    .onFailure { notice = "تعذرت مشاركة الفيديو." }
                            }.padding(6.dp))
                        }
                    }
                } else if (page == "مشاريعي") {
                    item { Text("مشاريعي", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    if (projects.isEmpty()) item { Note("لا توجد مشروعات محفوظة", "ابدأ من صفحة إنشاء واحفظ إعدادات أول مشروع.") }
                    itemsIndexed(projects) { index, p ->
                        Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).clickable {
                            image = Uri.parse(p.image); prompt = p.prompt; movement = p.movement; length = p.length; aspect = p.aspect; page = "إنشاء"
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = Uri.parse(p.image), contentDescription = null, modifier = Modifier.size(66.dp).background(bg, RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(p.name, color = Color.White, fontWeight = FontWeight.Bold); Text(p.movement + " • " + p.length + " ثوانٍ", color = muted, fontSize = 11.sp) }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("فتح", color = gold)
                                Text("حذف", color = Color(0xFFE28B8B), modifier = Modifier.clickable { deleteIndex = index })
                            }
                        }
                    }
                } else {
                    item { Text(if (page == "إنشاء") "محرر المشروع" else "ابدأ مشروعك", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ToolTile("صورة إلى فيديو", "تحريك صورة ثابتة", "▶", true) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            ToolTile("نص إلى فيديو", "محرك التوليد غير مربوط", "✎", false) { notice = "توليد فيديو من النص يحتاج محرك ذكاء اصطناعي خارجيًا أو نموذجًا محليًا." }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("الصورة المصدر", color = gold, fontWeight = FontWeight.Bold)
                            if (image != null) AsyncImage(model = image, contentDescription = "الصورة المختارة", modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).background(bg, RoundedCornerShape(12.dp)))
                            else Box(Modifier.fillMaxWidth().height(120.dp).background(bg, RoundedCornerShape(12.dp)).clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) { Text("＋  اختر صورة من هاتفك", color = gold) }
                            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = wine)) { Text("اختيار صورة") }
                            Text("وصف اللقطة", color = gold, fontWeight = FontWeight.Bold)
                            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("اكتب وصف المشهد أو الحركة المطلوبة") })
                            Text("حركة الكاميرا", color = gold, fontWeight = FontWeight.Bold)
                            listOf("تقريب سينمائي", "ابتعاد سينمائي", "تحريك أفقي").forEach { choice ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { movement = choice }) {
                                    RadioButton(selected = movement == choice, onClick = { movement = choice }, colors = RadioButtonDefaults.colors(selectedColor = gold))
                                    Text(choice, color = Color.White)
                                }
                            }
                            Text("أبعاد الفيديو", color = gold, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("عمودي 9:16", "مربع 1:1", "أفقي 16:9").forEach { ratio ->
                                    FilterChip(selected = aspect == ratio, onClick = { aspect = ratio }, label = { Text(ratio, fontSize = 11.sp) })
                                }
                            }
                            Text("مدة اللقطة", color = gold, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(5, 8, 10).forEach { n -> FilterChip(selected = length == n, onClick = { length = n }, label = { Text("$n ثوانٍ") }) }
                            }
                            Button(onClick = {
                                if (image == null) notice = "اختَر صورة أولًا."
                                else {
                                    val updated = projects + SavedProject("مشروع " + (projects.size + 1), image.toString(), prompt, movement, length, aspect)
                                    projects = updated
                                    writeProjects(prefs, updated)
                                    prefs.edit().putString("prompt", prompt).putString("movement", movement).putInt("length", length).putString("aspect", aspect).apply()
                                    notice = "تم حفظ المشروع وإعداداته على هذا الهاتف."
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = bg)) { Text("حفظ المشروع", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = {
                                    val source = image
                                    if (source == null) notice = "اختَر صورة أولًا."
                                    else if (!exporting) {
                                        exporting = true
                                        notice = "جارٍ إنشاء الفيديو..."
                                        scope.launch {
                                            try {
                                                val saved = withContext(Dispatchers.IO) {
                                                    LocalVideoExporter.export(context, source, length, movement, aspect)
                                                }
                                                val entry = SavedExport(saved.toString(), java.text.SimpleDateFormat("yyyy/MM/dd  HH:mm", java.util.Locale.getDefault()).format(java.util.Date()), movement, aspect, length)
                                                exports = listOf(entry) + exports
                                                writeExports(prefs, exports)
                                                notice = "تم حفظ الفيديو في المعرض، وأضيف إلى فيديوهاتي."
                                            } catch (e: Exception) {
                                                notice = "فشل إنشاء الفيديو: ${e.message ?: "خطأ غير معروف"}"
                                            } finally {
                                                exporting = false
                                            }
                                        }
                                    }
                                },
                                enabled = !exporting,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = wine)
                            ) {
                                Text(if (exporting) "جارٍ تصدير الفيديو..." else "إنشاء وحفظ فيديو MP4", fontWeight = FontWeight.Bold)
                            }
                            Text(notice, color = gold, fontSize = 12.sp)
                        }
                    }
                    item { Note("أدوات الاستوديو", "تصدير MP4 محليًا يضيف حركة تقريب أو ابتعاد أو تحريك أفقي للصورة ويحفظ النتيجة في مجلد Movies/WaleedAldib. توليد فيديو ذكاء اصطناعي من النص أو تحويل الصورة إلى مشهد جديد ما زال يحتاج محرك توليد مستقل.") }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun ToolTile(title: String, subtitle: String, icon: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(0.48f).background(panel, RoundedCornerShape(16.dp)).border(1.dp, if (active) gold.copy(alpha = .4f) else muted.copy(alpha = .15f), RoundedCornerShape(16.dp)).clickable(enabled = active, onClick = onClick).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(icon, color = gold, fontSize = 24.sp)
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(subtitle, color = muted, fontSize = 10.sp)
    }
}

@Composable
private fun Note(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = gold, fontWeight = FontWeight.Bold)
        Text(body, color = muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

private fun readProjects(prefs: android.content.SharedPreferences): List<SavedProject> {
    val a = runCatching { JSONArray(prefs.getString("projects", "[]")) }.getOrNull() ?: JSONArray()
    return (0 until a.length()).mapNotNull { i -> runCatching {
        val o = a.getJSONObject(i)
        SavedProject(o.getString("name"), o.getString("image"), o.optString("prompt"), o.optString("movement"), o.optInt("length", 5), o.optString("aspect", "عمودي 9:16"))
    }.getOrNull() }
}

private fun writeProjects(prefs: android.content.SharedPreferences, projects: List<SavedProject>) {
    val a = JSONArray()
    projects.forEach { p -> a.put(JSONObject().put("name", p.name).put("image", p.image).put("prompt", p.prompt).put("movement", p.movement).put("length", p.length).put("aspect", p.aspect)) }
    prefs.edit().putString("projects", a.toString()).apply()
}


private fun readExports(prefs: android.content.SharedPreferences): List<SavedExport> {
    val a = runCatching { JSONArray(prefs.getString("exports", "[]")) }.getOrNull() ?: JSONArray()
    return (0 until a.length()).mapNotNull { i -> runCatching {
        val o = a.getJSONObject(i)
        SavedExport(o.getString("uri"), o.optString("date"), o.optString("movement"), o.optString("aspect", "عمودي 9:16"), o.optInt("length", 5))
    }.getOrNull() }
}

private fun writeExports(prefs: android.content.SharedPreferences, exports: List<SavedExport>) {
    val a = JSONArray()
    exports.take(50).forEach { v ->
        a.put(JSONObject().put("uri", v.uri).put("date", v.date).put("movement", v.movement).put("aspect", v.aspect).put("length", v.length))
    }
    prefs.edit().putString("exports", a.toString()).apply()
}
