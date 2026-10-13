package com.gymbuddy.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ink = Color(0xFF0F1415)
private val lime = Color(0xFFBAEF48)
private val bg = Color(0xFFF7F8F4)
private val muted = Color(0xFF53615C)

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val api = GymApi(applicationContext)
  setContent {
   MaterialTheme(colorScheme=lightColorScheme(primary=ink,secondary=Color(0xFF5C7728),background=bg,surface=Color.White)) {
    NativeApp(api)
   }
  }
 }
}
@Composable fun NativeApp(api: GymApi) {
 var authenticated by remember { mutableStateOf(api.signedIn) }
 var page by remember { mutableStateOf("Today") }
 var notice by remember { mutableStateOf("") }
 val scope = rememberCoroutineScope()
 Surface(Modifier.fillMaxSize(),color=bg) {
  Column {
   if (!authenticated) {
    AuthPage(api, { authenticated=true }, { notice=it })
   } else {
    Row(Modifier.fillMaxWidth().background(ink).padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
     Text("GYM BUDDY",fontWeight=FontWeight.Black,color=Color.White,fontSize=21.sp)
     Spacer(Modifier.weight(1f))
     Text("NATIVE",color=lime,fontSize=11.sp)
    }
    Box(Modifier.weight(1f)) {
     key(page) {
      when(page) {
       "Today" -> TodayPage(api, { notice=it })
       "Train" -> TrainPage(api, { notice=it })
       "Log" -> LogPage(api, { notice=it })
       "Me" -> MePage(api,{notice=it},{api.signOut();authenticated=false;page="Today"})
      }
     }
    }
    NavigationBar(containerColor=Color.White) {
     listOf("Today","Train","Log","Me").forEach { tab ->
      NavigationBarItem(selected=page==tab,onClick={page=tab},icon={Text(when(tab){"Today"->"⌂";"Train"->"◉";"Log"->"▤";else->"●"},fontSize=22.sp)},label={Text(tab)})
     }
    }
   }
   if(notice.isNotBlank()) {
    Snackbar(Modifier.padding(8.dp),action={TextButton(onClick={notice=""}){Text("Close")}}){Text(notice)}
   }
  }
 }
}
@Composable fun AuthPage(api:GymApi,onSuccess:()->Unit,onNotice:(String)->Unit) {
 var email by remember { mutableStateOf("") }
 var pass by remember { mutableStateOf("") }
 var loading by remember { mutableStateOf(false) }
 var creating by remember { mutableStateOf(false) }
 val scope = rememberCoroutineScope()
 Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  Spacer(Modifier.height(56.dp))
  Text("GYM BUDDY",fontSize=32.sp,fontWeight=FontWeight.Black,color=ink)
  Text("Your training. Your progress.",color=muted)
  if(!api.configured) Text("This APK has no Supabase credentials configured. Set the GitHub build secrets and rebuild.",color=MaterialTheme.colorScheme.error)
  OutlinedTextField(email,{email=it},label={Text("Email")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),singleLine=true,modifier=Modifier.fillMaxWidth())
  OutlinedTextField(pass,{pass=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
  Button(onClick={
   loading=true
   scope.launch {
    try { if(creating){api.signUp(email.trim(),pass);onNotice("Check your email to confirm registration, then sign in.");creating=false} else {api.signIn(email.trim(),pass);onSuccess()} }
    catch(e:Exception){onNotice(e.message ?: "Authentication failed")}
    finally {loading=false}
   }
  },enabled=!loading && api.configured && email.contains("@") && pass.length>=6,modifier=Modifier.fillMaxWidth()) {Text(if(creating)"Create account" else "Sign in")}
  TextButton(onClick={creating=!creating},modifier=Modifier.fillMaxWidth()) {Text(if(creating)"Already have an account? Sign in" else "Create an account")}
 }
}
@Composable fun Title(text:String,sub:String="") {
 Text(text,fontWeight=FontWeight.ExtraBold,fontSize=26.sp,color=ink)
 if(sub.isNotBlank()) Text(sub,color=muted,fontSize=13.sp)
}
@Composable fun Panel(title:String,content:@Composable ColumnScope.()->Unit) {
 Card(Modifier.fillMaxWidth().padding(vertical=6.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
  Column(Modifier.padding(17.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
   Text(title,fontWeight=FontWeight.Bold,fontSize=17.sp)
   content()
  }
 }
}
@Composable fun RemoteRows(api:GymApi,table:String,heading:String,order:String?=null,filterMine:Boolean=true,secondary:(JSONObject)->String={""}) {
 var rows by remember(table) { mutableStateOf(listOf<JSONObject>()) }
 var loading by remember(table) { mutableStateOf(true) }
 var error by remember(table) { mutableStateOf("") }
 var refresh by remember { mutableIntStateOf(0) }
 LaunchedEffect(table,refresh) {
  loading=true;error=""
  try {
   val result = if(filterMine)api.mine(table,order=order) else api.list(table,order)
   rows=(0 until result.length()).map {result.getJSONObject(it)}
  } catch(e:Exception) {error=e.message ?: "Unable to load"}
  loading=false
 }
 Panel(heading) {
  if(loading) CircularProgressIndicator(Modifier.size(24.dp))
  if(error.isNotBlank()) Text(error,color=Color.Red)
  if(!loading && rows.isEmpty() && error.isBlank()) Text("No entries yet",color=muted)
  rows.take(30).forEach { row ->
   val title = row.optString("name").ifBlank {row.optString("title").ifBlank {row.optString("date").ifBlank {row.optString("exercise_name").ifBlank {row.optString("id").take(12)}}}}
   Text(title,fontWeight=FontWeight.Medium)
   val sub=secondary(row)
   if(sub.isNotBlank()) Text(sub,color=muted,fontSize=12.sp)
   HorizontalDivider(color=bg)
  }
  TextButton(onClick={refresh++}) {Text("Refresh")}
 }
}
@Composable fun TodayPage(api:GymApi,onNotice:(String)->Unit) {
 var visits by remember { mutableStateOf(0) }
 var workouts by remember { mutableStateOf(0) }
 LaunchedEffect(Unit) {
  try {visits=api.mine("attendance").length();workouts=api.mine("workout_logs").length()}
  catch(e:Exception){onNotice(e.message ?: "Could not load dashboard")}
 }
 Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Title("Today's plan","Build consistency one session at a time.")
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
   Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=lime)){Column(Modifier.padding(16.dp)){Text("$visits",fontSize=28.sp,fontWeight=FontWeight.Black);Text("Gym visits")}}
   Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp)){Text("$workouts",fontSize=28.sp,fontWeight=FontWeight.Black);Text("Completed workouts")}}
  }
  RemoteRows(api,"workouts","My routines",secondary={"${it.optInt("day_number")} · ${it.optString("notes")}"})
  RemoteRows(api,"workout_logs","Recent workouts","performed_at.desc",secondary={"${it.optInt("total_sets")} sets · ${it.optInt("duration_seconds")/60} min"})
 }
}
@Composable fun TrainPage(api:GymApi,onNotice:(String)->Unit) {
 var name by remember { mutableStateOf("") }
 var reload by remember {mutableIntStateOf(0)}
 val scope= rememberCoroutineScope()
 Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)){
  Title("Train","Build and browse your routines")
  Panel("Create workout"){
   OutlinedTextField(name,{name=it},label={Text("Routine name")},modifier=Modifier.fillMaxWidth())
   Button(onClick={scope.launch {
    try {api.insert("workouts",JSONObject().put("user_id",api.userId).put("name",name.trim()));name="";reload++ ;onNotice("Workout created")}
    catch(e:Exception){onNotice(e.message ?: "Unable to create")}
   }},enabled=name.isNotBlank()){Text("Create")}
  }
  key(reload){RemoteRows(api,"workouts","My workouts","created_at.desc")}
  RemoteRows(api,"exercises","Exercise library","name.asc",filterMine=false,secondary={it.optString("muscle_group")})
  RemoteRows(api,"workout_templates","Starter templates","name.asc",filterMine=false,secondary={it.optString("program")})
 }
}
@Composable fun LogPage(api:GymApi,onNotice:(String)->Unit) {
 var tab by remember {mutableStateOf("Gym")}
 var refresh by remember {mutableIntStateOf(0)}
 val scope=rememberCoroutineScope()
 Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)){
  Title("Log","Gym visits, nutrition and progress")
  SingleChoiceSegmentedButtonRow {
   listOf("Gym","Food","Progress").forEachIndexed{idx,item->
    SegmentedButton(selected=tab==item,onClick={tab=item},shape=SegmentedButtonDefaults.itemShape(idx,3)){Text(item)}
   }
  }
  key(tab,refresh){
   when(tab){
    "Gym" -> {
     var todayVisit by remember{mutableStateOf<JSONObject?>(null)}
     LaunchedEffect(refresh) {
      try {val q=api.mine("attendance");todayVisit=(0 until q.length()).map{q.getJSONObject(it)}.firstOrNull{it.optString("date")==LocalDate.now().toString()}}
      catch(e:Exception){onNotice(e.message ?: "Unable to load visits")}
     }
     Panel("Today's visit"){
      Text(if(todayVisit==null)"Not checked in" else if(todayVisit!!.isNull("check_out"))"Checked in" else "Visit completed",color=muted)
      Button(onClick={scope.launch {
       try {
        if(todayVisit==null) api.insert("attendance",JSONObject().put("user_id",api.userId).put("date",LocalDate.now().toString()).put("check_in",java.time.Instant.now().toString()))
        else if(todayVisit!!.isNull("check_out"))api.patch("attendance",todayVisit!!.getString("id"),JSONObject().put("check_out",java.time.Instant.now().toString()))
        else onNotice("One visit per day. Edit your visit in the web app to change its times.")
        refresh++
       }catch(e:Exception){onNotice(e.message ?: "Could not update visit")}
      }},enabled=todayVisit==null || todayVisit!!.isNull("check_out")){Text(if(todayVisit==null)"Check in" else "Check out")}
     }
     RemoteRows(api,"attendance","Attendance history","date.desc",secondary={it.optString("check_in").take(16)})
    }
    "Food" -> FoodPage(api,onNotice,{refresh++})
    else -> {
     RemoteRows(api,"body_measurements","Body measurements","date.desc",secondary={"${it.optString("weight_kg")} kg"})
     RemoteRows(api,"personal_records","Personal records",secondary={"${it.optString("weight_kg")} kg × ${it.optString("reps")}"})
     RemoteRows(api,"goals","Goals",secondary={"${it.optString("current_value")} / ${it.optString("target_value")}"})
    }
   }
  }
 }
}
@Composable fun FoodPage(api:GymApi,onNotice:(String)->Unit,onSaved:()->Unit) {
 var sugar by remember {mutableStateOf(false)}
 var oil by remember {mutableStateOf(false)}
 var note by remember {mutableStateOf("")}
 val scope=rememberCoroutineScope()
 LaunchedEffect(Unit){
  try{
   val rows=api.mine("nutrition_days");val current=(0 until rows.length()).map{rows.getJSONObject(it)}.firstOrNull{it.optString("date")==LocalDate.now().toString()}
   sugar=current?.optBoolean("had_sugar")?:false;oil=current?.optBoolean("had_oil")?:false;note=current?.optString("note")?:""
  }catch(e:Exception){onNotice(e.message ?: "Unable to load food log")}
 }
 Panel("Today's food log"){
  Row(verticalAlignment=Alignment.CenterVertically){Checkbox(sugar,{sugar=it});Text("Had sugar")}
  Row(verticalAlignment=Alignment.CenterVertically){Checkbox(oil,{oil=it});Text("Had oil")}
  OutlinedTextField(note,{note=it},label={Text("Food notes")},modifier=Modifier.fillMaxWidth())
  Button(onClick={scope.launch {
   try{api.upsertNutrition(LocalDate.now().toString(),sugar,oil,note);onNotice("Food log saved");onSaved()}
   catch(e:Exception){onNotice(e.message ?: "Could not save")}
  }}){Text("Save food log")}
 }
 RemoteRows(api,"nutrition_days","Previous food logs","date.desc",secondary={"${if(it.optBoolean("had_sugar"))"Sugar " else ""}${if(it.optBoolean("had_oil"))"Oil " else ""}${it.optString("note")}"})
}
@Composable fun MePage(api:GymApi,onNotice:(String)->Unit,logout:()->Unit) {
 var section by remember{mutableStateOf("Profile")}
 var profile by remember {mutableStateOf<JSONObject?>(null)}
 var name by remember {mutableStateOf("")}
 val scope=rememberCoroutineScope()
 LaunchedEffect(Unit){
  try {val rows=api.mine("profiles",key="id");profile=rows.optJSONObject(0);name=profile?.optString("name")?:""}
  catch(e:Exception){onNotice(e.message ?: "Could not load profile")}
 }
 Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp)){
  Title("Me","Profile, friends and achievements")
  Panel("My profile"){
   OutlinedTextField(name,{name=it},label={Text("Display name")},modifier=Modifier.fillMaxWidth())
   Button(onClick={scope.launch {
    try{api.patch("profiles",api.userId,JSONObject().put("name",name));onNotice("Profile saved")}
    catch(e:Exception){onNotice(e.message ?: "Could not save")}
   }}){Text("Save name")}
  }
  RemoteRows(api,"achievements","Achievements")
  RemoteRows(api,"friendships","Friend requests")
  RemoteRows(api,"profiles","Account",filterMine=true,secondary={it.optString("friend_code")})
  OutlinedButton(onClick=logout,modifier=Modifier.fillMaxWidth()){Text("Sign out")}
 }
}
