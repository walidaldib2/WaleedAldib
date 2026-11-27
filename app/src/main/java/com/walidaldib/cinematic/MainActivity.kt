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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

private val bg = Color(0xFF09090D)
private val panel = Color(0xFF17151E)
private val gold = Color(0xFFE18BFF)
private val wine = Color(0xFF8B3DCE)
private val muted = Color(0xFFAAA7A0)

private data class SavedProject(val name: String, val image: String, val prompt: String, val movement: String, val length: Int, val aspect: String = "عمودي 9:16")
private data class SavedExport(val uri: String, val date: String, val movement: String, val aspect: String, val length: Int)

@Composable
private fun Studio(context: Context) {
    val prefs = remember { context.getSharedPreferences("studio_projects", Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf("الرئيسية") }
    var createMode by remember { mutableStateOf("فيديو") }
    var activeTool by remember { mutableStateOf("صورة إلى فيديو") }
    var image by remember { mutableStateOf(prefs.getString("last_image", null)?.let(Uri::parse)) }
    var projectName by remember { mutableStateOf("مشروعي الجديد") }
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
                listOf("الرئيسية", "إنشاء", "الأصول", "حسابي").forEach { tab ->
                    NavigationBarItem(selected = page == tab, onClick = { page = tab },
                        icon = { Text(when (tab) { "الرئيسية" -> "⌂"; "إنشاء" -> "✦"; "الأصول" -> "▤"; else -> "◎" }, color = gold) },
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
                if (page == "الرئيسية") {
                    item {
                        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF44205F), Color(0xFF17151E))), RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("استوديو وليد الديب", color = gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text("حوّل أفكارك إلى مشاهد", color = Color.White, fontSize = 12.sp)
                            }
                            Text("⚡ محلي", color = gold, fontWeight = FontWeight.Bold)
                        }
                    }
                    item { HomeSection("ابدأ الإنشاء", listOf("صورة إلى فيديو", "نص إلى فيديو", "تمديد فيديو", "قوالب مؤثرة")) { label ->
                        activeTool = label
                        page = "إنشاء"
                        if (label != "صورة إلى فيديو") notice = "الأداة دي ضمن الواجهة، ومحرك التوليد الخاص بها لم يتم ربطه بعد."
                    } }
                    item { HomeSection("الرائج", listOf("لقطة سينمائية", "حركة بطيئة", "تحول درامي", "مشهد خيالي")) { label -> prompt = "أنشئ مشهدًا سينمائيًا بأسلوب ${label}، حركة طبيعية وإضاءة احترافية"; activeTool = "صورة إلى فيديو"; page = "إنشاء" } }
                    item { HomeSection("الأروع", listOf("عالم الروبوتات", "سحر الطبيعة", "بوابة خيالية", "عالم مصغر")) { label -> prompt = "اصنع مشهدًا إبداعيًا بعنوان ${label}"; activeTool = "صورة إلى فيديو"; page = "إنشاء" } }
                    item { HomeSection("جديد", listOf("إضاءة سينمائية", "عالم مصغر", "ستايل أنمي", "بورتريه فني")) { label -> prompt = "حوّل الصورة إلى ${label} بتفاصيل عالية"; activeTool = "صورة إلى فيديو"; page = "إنشاء" } }
                    item { HomeSection("تحويل الصور", listOf("تغيير الستايل", "تغيير الخلفية", "ترميم الصورة", "تحسين الجودة")) { label -> notice = "${label} يحتاج محرك معالجة صور قبل تفعيله."; page = "إنشاء"; createMode = "صورة" } }
                    item { HomeSection("تفاعل", listOf("لقاء عاطفي", "تحريك الوجه", "رقصة بالذكاء", "شخصيات تتحدث")) { label -> notice = "${label} يحتاج محرك ذكاء اصطناعي متخصصًا قبل تفعيله."; page = "إنشاء" } }
                    item { HomeSection("Morph", listOf("تحول الشخصية", "تحول الملابس", "تحول المكان", "تحول فني")) { label -> notice = "تأثير ${label} يحتاج محرك توليد صور وفيديو قبل تفعيله."; page = "إنشاء" } }
                    item { HomeSection("الصوت والشخصيات", listOf("تحريك الشفاه", "مزامنة الصوت", "مؤثرات صوتية", "شخصية ثابتة")) { label -> notice = "${label} يحتاج محرك ذكاء اصطناعي متخصصًا قبل تفعيله."; page = "إنشاء" } }
                } else if (page == "حسابي") {
                    item { Text("حسابي", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item { Note("وليد الديب", "استوديو صناعة الفيديو والصور على هذا الهاتف.") }
                    item { Note("المكتبة", "${projects.size} مشروعات محفوظة • ${exports.size} فيديوهات مصدّرة") }
                    item { Note("وضع التشغيل", "إنشاء الفيديو الحالي يتم محليًا من صورة ثابتة. التوليد بالذكاء الاصطناعي يحتاج ربط خدمة أو نموذج مستقل.") }
                    item { Button(onClick = { page = "إنشاء" }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = wine)) { Text("فتح الاستوديو") } }
                }
                if (page == "فيديوهاتي" || page == "الأصول") {
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
                    item { Text("المشروعات المحفوظة", color = gold, fontWeight = FontWeight.Bold) }
                    if (projects.isEmpty()) item { Note("لا توجد مشروعات محفوظة", "احفظ إعدادات مشروعك من صفحة الإنشاء.") }
                    itemsIndexed(projects) { index, p ->
                        Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).clickable {
                            image = Uri.parse(p.image); projectName = p.name; prompt = p.prompt; movement = p.movement; length = p.length; aspect = p.aspect; page = "إنشاء"
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = Uri.parse(p.image), contentDescription = null, modifier = Modifier.size(60.dp).background(bg, RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(p.name, color = Color.White, fontWeight = FontWeight.Bold); Text(p.movement + " • " + p.length + " ثوانٍ", color = muted, fontSize = 11.sp) }
                            Text("حذف", color = Color(0xFFE28B8B), modifier = Modifier.clickable { deleteIndex = index }.padding(6.dp))
                        }
                    }
                } else if (page == "مشاريعي") {
                    item { Text("مشاريعي", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    if (projects.isEmpty()) item { Note("لا توجد مشروعات محفوظة", "ابدأ من صفحة إنشاء واحفظ إعدادات أول مشروع.") }
                    itemsIndexed(projects) { index, p ->
                        Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(16.dp)).clickable {
                            image = Uri.parse(p.image); projectName = p.name; prompt = p.prompt; movement = p.movement; length = p.length; aspect = p.aspect; page = "إنشاء"
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
                    item { Text("إنشاء", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("فيديو", "صورة", "قوالب", "وكيل").forEach { mode ->
                                FilterChip(selected = createMode == mode, onClick = {
                                    createMode = mode
                                    if (mode != "فيديو") notice = "قسم $mode ظاهر في الواجهة، وسيتم تفعيله عند إضافة محركه."
                                }, label = { Text(mode, fontSize = 11.sp) })
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("صورة إلى فيديو", "نص إلى فيديو", "تأثير", "انتقال", "صوت", "كلام", "إعادة تصميم", "تمديد", "شخصية").forEach { tool ->
                                FilterChip(selected = activeTool == tool, onClick = {
                                    activeTool = tool
                                    if (tool != "صورة إلى فيديو") notice = "قسم $tool موجود في واجهة الإنشاء، ويحتاج محركه المتخصص قبل تشغيل التوليد."
                                }, label = { Text(tool, fontSize = 11.sp) })
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ToolTile("صورة إلى فيديو", "تحريك صورة ثابتة", "▶", true) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            ToolTile("نص إلى فيديو", "محرك التوليد غير مربوط", "✎", false) { notice = "توليد فيديو من النص يحتاج محرك ذكاء اصطناعي خارجيًا أو نموذجًا محليًا." }
                        }
                    }
                    item {
                        Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("اسم المشروع", color = gold, fontWeight = FontWeight.Bold)
                            OutlinedTextField(value = projectName, onValueChange = { projectName = it.take(40) }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("اكتب اسمًا لمشروعك") })
                            Text("أداة الإنشاء: $activeTool", color = gold, fontWeight = FontWeight.Bold)
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
                                    val cleanName = projectName.trim().ifBlank { "مشروع " + (projects.size + 1) }
                                    val savedProject = SavedProject(cleanName, image.toString(), prompt, movement, length, aspect)
                                    val existing = projects.indexOfFirst { it.name.equals(cleanName, ignoreCase = true) }
                                    val updated = projects.toMutableList().also { if (existing >= 0) it[existing] = savedProject else it.add(savedProject) }
                                    projects = updated
                                    projectName = cleanName
                                    writeProjects(prefs, updated)
                                    prefs.edit().putString("prompt", prompt).putString("movement", movement).putInt("length", length).putString("aspect", aspect).apply()
                                    notice = if (existing >= 0) "تم تحديث المشروع المحفوظ." else "تم حفظ المشروع وإعداداته على هذا الهاتف."
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
@Composable
private fun HomeSection(title: String, labels: List<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("عرض الكل", color = gold, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                Column(Modifier.weight(1f).height(112.dp).background(Brush.linearGradient(listOf(
                    listOf(Color(0xFF533044), Color(0xFF263344), Color(0xFF394333), Color(0xFF403047))[index],
                    panel
                )), RoundedCornerShape(14.dp)).clickable { onSelect(label) }.padding(9.dp), verticalArrangement = Arrangement.Bottom) {
                    Text(listOf("✦", "◈", "✧", "❖")[index], color = gold, fontSize = 21.sp)
                    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                }
            }
        }
    }
}

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
