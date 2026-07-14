package com.mihad.review

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val Forest = Color(0xFF1D6B55)
private val Mint = Color(0xFFE0F1E8)
private val Ink = Color(0xFF182724)
private val Soft = Color(0xFFF8FAF7)
private val Line = Color(0xFFE4EAE5)
private val intervals = listOf(1L, 3, 7, 14, 30, 60, 120)

data class Lesson(val id:String=UUID.randomUUID().toString(), val title:String, val subject:String, val created:LocalDate=LocalDate.now(), val next:LocalDate=LocalDate.now().plusDays(1), val reviews:Int=0, val reviewDays:List<LocalDate> = emptyList())

class MihadViewModel(app: Application): AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("mihad_lessons", Application.MODE_PRIVATE)
    var lessons by mutableStateOf(load()); private set
    fun add(title:String, subject:String) { lessons = lessons + Lesson(title=title, subject=subject); persist(); schedule(lessons.last()) }
    fun review(id:String) { lessons = lessons.map { l -> if(l.id == id) { val count=l.reviews+1; l.copy(reviews=count, next=LocalDate.now().plusDays(intervals[minOf(count, intervals.lastIndex)]), reviewDays=l.reviewDays+LocalDate.now()) } else l }; persist(); lessons.find { it.id==id }?.let(::schedule) }
    fun postpone(id:String) { lessons = lessons.map { if(it.id==id) it.copy(next=LocalDate.now().plusDays(1)) else it }; persist(); lessons.find { it.id==id }?.let(::schedule) }
    fun due() = lessons.filter { !it.next.isAfter(LocalDate.now()) }.sortedBy { it.next }
    fun mastery(l:Lesson) = (l.reviews * 100 / intervals.size).coerceAtMost(100)
    private fun persist() { prefs.edit().putString("items", lessons.joinToString("~") { l -> listOf(l.id,l.title,l.subject,l.created,l.next,l.reviews,l.reviewDays.joinToString(",")).joinToString("|") { Base64.encodeToString(it.toString().toByteArray(),Base64.NO_WRAP) } }).apply() }
    private fun load(): List<Lesson> = prefs.getString("items", "")!!.takeIf { it.isNotBlank() }?.split("~")?.mapNotNull { row -> runCatching { val v=row.split("|").map { String(Base64.decode(it,Base64.NO_WRAP)) }; Lesson(v[0],v[1],v[2],LocalDate.parse(v[3]),LocalDate.parse(v[4]),v[5].toInt(),v[6].takeIf{it.isNotBlank()}?.split(",")?.map(LocalDate::parse) ?: emptyList()) }.getOrNull() } ?: emptyList()
    private fun schedule(l: Lesson) { val delay = maxOf(1, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),l.next)); val work=OneTimeWorkRequestBuilder<ReviewReminderWorker>().setInputData(Data.Builder().putString("title",l.title).build()).setInitialDelay(delay,TimeUnit.DAYS).build(); WorkManager.getInstance(getApplication()).enqueueUniqueWork("review_${l.id}",ExistingWorkPolicy.REPLACE,work) }
}

class MainActivity: ComponentActivity() { override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); if(android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100); setContent { MihadApp() } } }

@Composable fun MihadApp(vm:MihadViewModel = viewModel()) {
    var screen by remember { mutableStateOf(0) }; var adding by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme=lightColorScheme(primary=Forest, background=Soft, surface=Color.White, onBackground=Ink)) {
        Scaffold(containerColor=Soft, bottomBar={ NavigationBar(containerColor=Color.White) { listOf("اليوم" to Icons.Default.Home, "الدروس" to Icons.Default.MenuBook, "التقدم" to Icons.Default.Insights).forEachIndexed { i,(text,icon) -> NavigationBarItem(selected=screen==i,onClick={screen=i},icon={Icon(icon,null)},label={Text(text)}) } } }, floatingActionButton={ if(screen!=2) ExtendedFloatingActionButton(onClick={adding=true}, containerColor=Forest, contentColor=Color.White, icon={Icon(Icons.Default.Add,null)}, text={Text("تمت دراسة درس")}) }) { padding ->
            Box(Modifier.padding(padding)) { when(screen) { 0 -> TodayScreen(vm); 1 -> LessonsScreen(vm); else -> StatsScreen(vm) }; if(adding) AddLessonSheet(onDismiss={adding=false}) { title,subject -> vm.add(title,subject);adding=false } }
        }
    }
}

@Composable private fun TodayScreen(vm:MihadViewModel) { val due=vm.due(); LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    item { Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE، d MMMM")),color=Forest,fontSize=13.sp,fontWeight=FontWeight.SemiBold); Text("صباح الخير، عبدالله",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp)); Text("خطوات قصيرة اليوم، وذاكرة أقوى غدًا.",color=Color(0xFF71807B),modifier=Modifier.padding(top=4.dp)) }
    item { FocusCard(due.size) }
    item { SectionTitle("تحتاج انتباهك اليوم", if(due.isEmpty()) "يوم هادئ — لا شيء مستحق الآن" else "${due.size} ${if(due.size==1) "درس ينتظر مراجعتك" else "دروس تنتظر مراجعتك"}") }
    if(due.isEmpty()) item { EmptyState() } else items(due,key={it.id}) { LessonCard(it,vm.mastery(it),true,{vm.review(it.id)},{vm.postpone(it.id)}) }
    item { SectionTitle("قادم قريبًا","استعد بلا ضغط") }
    items(vm.lessons.filter { it.next > LocalDate.now() }.sortedBy { it.next }.take(3),key={"up"+it.id}) { LessonCard(it,vm.mastery(it),false,{},{}) }
} }

@Composable private fun FocusCard(count:Int) { Card(colors=CardDefaults.cardColors(containerColor=Forest),shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(.15f)),contentAlignment=Alignment.Center){Icon(Icons.Default.AutoAwesome,null,tint=Color(0xFFFFD58C))}; Column(Modifier.padding(horizontal=16.dp)) { Text("مراجعات اليوم",color=Color.White.copy(.75f),fontSize=13.sp); Text("$count",color=Color.White,fontSize=38.sp,fontWeight=FontWeight.Bold); Text(if(count==0)"خذ نفسًا عميقًا، أنت على المسار." else "15 دقيقة كافية لصناعة فرق.",color=Color.White.copy(.85f),fontSize=12.sp) } } } }
@Composable private fun SectionTitle(title:String, sub:String) { Column(Modifier.padding(top=12.dp)) { Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp); Text(sub,color=Color(0xFF74817D),fontSize=12.sp,modifier=Modifier.padding(top=2.dp)) } }
@Composable private fun LessonCard(l:Lesson, mastery:Int, due:Boolean, done:()->Unit, later:()->Unit) { Card(shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) { SubjectBadge(l.subject); Column(Modifier.weight(1f).padding(horizontal=12.dp)) { Text(l.title,fontWeight=FontWeight.Bold,fontSize=15.sp); Text("${l.subject} · المراجعة ${l.reviews+1}",color=Color(0xFF74817D),fontSize=12.sp); Spacer(Modifier.height(8.dp)); Row(verticalAlignment=Alignment.CenterVertically) { LinearProgressIndicator(progress={mastery/100f},modifier=Modifier.width(85.dp).height(5.dp).clip(CircleShape),color=Forest,trackColor=Mint); Text("  إتقان $mastery%",fontSize=11.sp,color=Color(0xFF74817D)) } }; if(due) Column(horizontalAlignment=Alignment.End) { Button(onClick=done,contentPadding=PaddingValues(horizontal=10.dp),shape=RoundedCornerShape(9.dp),colors=ButtonDefaults.buttonColors(containerColor=Mint,contentColor=Forest)){Text("راجعت",fontSize=12.sp)}; TextButton(onClick=later,contentPadding=PaddingValues(0.dp)){Text("لاحقًا",fontSize=11.sp,color=Color(0xFF74817D))} } else Surface(color=Soft,shape=RoundedCornerShape(9.dp)){Text("${when { l.next==LocalDate.now().plusDays(1)->"غدًا"; else->"بعد ${LocalDate.now().until(l.next).days} أيام" }}",fontSize=11.sp,color=Color(0xFF61706A),modifier=Modifier.padding(9.dp))} } } }
@Composable private fun SubjectBadge(subject:String) { val icon=when(subject){"رياضيات"->"∑";"فيزياء"->"⚛";"كيمياء"->"⌬";"أحياء"->"✿";else->"◈"}; Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Mint),contentAlignment=Alignment.Center){Text(icon,color=Forest,fontSize=20.sp,fontWeight=FontWeight.Bold)} }
@Composable private fun EmptyState(){ Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(30.dp).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(50.dp).clip(CircleShape).background(Mint),contentAlignment=Alignment.Center){Icon(Icons.Default.Done,null,tint=Forest)};Text("يوم هادئ، أحسنت!",fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp));Text("عندما تضيف درسًا، سيصنع مِهاد خطة مراجعته تلقائيًا.",textAlign=TextAlign.Center,color=Color(0xFF74817D),fontSize=12.sp,modifier=Modifier.padding(top=4.dp))}} }
@Composable private fun LessonsScreen(vm:MihadViewModel){ var selected by remember { mutableStateOf("الكل") }; val subjects=listOf("الكل")+vm.lessons.map{it.subject}.distinct(); LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("مكتبة الدراسة",color=Forest,fontWeight=FontWeight.SemiBold,fontSize=13.sp);Text("كل الدروس",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp)); Row(Modifier.padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){subjects.forEach{s->FilterChip(selected=s==selected,onClick={selected=s},label={Text(s)})}}};items(vm.lessons.filter{selected=="الكل"||it.subject==selected}.sortedByDescending{it.created},key={it.id}){LessonCard(it,vm.mastery(it),false,{},{})};if(vm.lessons.isEmpty())item{EmptyState()} } }
@Composable private fun StatsScreen(vm:MihadViewModel){val total=vm.lessons.sumOf{it.reviews}; val expected=total+vm.due().size; LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("لوحة التقدم",color=Forest,fontWeight=FontWeight.SemiBold,fontSize=13.sp);Text("إحصاءاتك",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=5.dp))};item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){StatBox("الدروس",vm.lessons.size.toString(),Modifier.weight(1f));StatBox("المراجعات",total.toString(),Modifier.weight(1f));StatBox("الالتزام",if(expected==0)"0%" else "${total*100/expected}%",Modifier.weight(1f))}};item{Card(shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(20.dp)){Text("التوزيع حسب المادة",fontWeight=FontWeight.Bold); vm.lessons.groupingBy{it.subject}.eachCount().forEach{(s,n)->Row(Modifier.padding(top=14.dp),verticalAlignment=Alignment.CenterVertically){Text(s,Modifier.width(78.dp),fontSize=13.sp);LinearProgressIndicator(progress={n.toFloat()/maxOf(vm.lessons.size,1)},modifier=Modifier.weight(1f).height(7.dp).clip(CircleShape),color=Forest,trackColor=Mint);Text("  $n",fontWeight=FontWeight.Bold,fontSize=13.sp)}}}}} } }
@Composable private fun StatBox(label:String,value:String,modifier:Modifier){Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=Color.White),modifier=modifier){Column(Modifier.padding(14.dp)){Text(label,fontSize=11.sp,color=Color(0xFF71807B));Text(value,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Forest,modifier=Modifier.padding(top=5.dp))}}}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AddLessonSheet(onDismiss:()->Unit,onSave:(String,String)->Unit){var title by remember{mutableStateOf("")};var subject by remember{mutableStateOf("رياضيات")};val subjects=listOf("رياضيات","فيزياء","كيمياء","أحياء","لغة عربية","أخرى");AlertDialog(onDismissRequest=onDismiss,shape=RoundedCornerShape(25.dp),title={Column{Text("ماذا درست اليوم؟",fontWeight=FontWeight.Bold);Text("ننشئ خطة المراجعة المناسبة لك تلقائيًا.",fontSize=12.sp,color=Color(0xFF71807B),modifier=Modifier.padding(top=5.dp))}},text={Column{OutlinedTextField(value=title,onValueChange={title=it},label={Text("اسم الدرس")},placeholder={Text("مثال: الحركة في خط مستقيم")},singleLine=true,modifier=Modifier.fillMaxWidth());Text("المادة",fontWeight=FontWeight.SemiBold,fontSize=13.sp,modifier=Modifier.padding(top=17.dp,bottom=8.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){subjects.forEach{s->FilterChip(selected=subject==s,onClick={subject=s},label={Text(s,fontSize=12.sp)})}}},confirmButton={Button(onClick={if(title.isNotBlank())onSave(title,subject)},enabled=title.isNotBlank()){Icon(Icons.Default.Done,null);Spacer(Modifier.width(5.dp));Text("حفظ وجدولة المراجعات")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})}
