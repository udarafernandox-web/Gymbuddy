package com.gymbuddy.nativeapp

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

private val lime = Color(0xFFB4E542)
private val dark = Color(0xFF101318)
private val ink = Color(0xFFEFF1F3)
private val muted = Color(0xFFA3A9B3)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = GymApi(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = lime, background = dark, surface = Color(0xFF1A1E25), onBackground = ink)) {
            GymApp(api)
        } }
    }
}

class GymApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("gymbuddy_native_session", Context.MODE_PRIVATE)
    var token: String
        get() = prefs.getString("token", "") ?: ""
        private set(value) { prefs.edit().putString("token", value).apply() }
    var userId: String
        get() = prefs.getString("uid", "") ?: ""
        private set(value) { prefs.edit().putString("uid", value).apply() }
    private var refreshToken: String
        get() = prefs.getString("refresh", "") ?: ""
        set(value) { prefs.edit().putString("refresh", value).apply() }
    val loggedIn get() = token.isNotEmpty() && userId.isNotEmpty()
    fun logout() { token = ""; userId = ""; refreshToken = "" }
    fun draftGet() = prefs.getString("draft_$userId", "") ?: ""
    fun draftSave(text: String) { prefs.edit().putString("draft_$userId", text).apply() }

    private fun send(path: String, verb: String = "GET", body: String? = null, useAuth: Boolean = true, extra: Map<String,String> = emptyMap()): String {
        require(BuildConfig.SUPABASE_KEY.isNotEmpty()) { "Supabase publishable key is not configured for this build." }
        val conn = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        conn.requestMethod = verb
        conn.connectTimeout = 12000
        conn.readTimeout = 18000
        conn.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        if (useAuth && token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        extra.forEach { (k,v) -> conn.setRequestProperty(k,v) }
        if (body != null) { conn.doOutput = true; conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
        val status = conn.responseCode
        val response = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (status !in 200..299) throw IllegalStateException("HTTP $status: " + runCatching { JSONObject(response).optString("message").ifBlank { JSONObject(response).optString("error_description") } }.getOrDefault(response.take(160)))
        return response
    }
    suspend fun auth(email: String, password: String, signup: Boolean = false) = withContext(Dispatchers.IO) {
        val p = if (signup) "/auth/v1/signup" else "/auth/v1/token?grant_type=password"
        val result = JSONObject(send(p,"POST",JSONObject().put("email",email).put("password",password).toString(),false))
        if (!result.has("access_token")) {
            if (signup) return@withContext "Account created. Confirm your email, then sign in."
            throw IllegalStateException("No access token returned")
        }
        token = result.getString("access_token")
        userId = result.getJSONObject("user").getString("id")
        refreshToken = result.optString("refresh_token")
        "Signed in"
    }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) return@withContext false
        runCatching {
            val obj=JSONObject(send("/auth/v1/token?grant_type=refresh_token","POST",JSONObject().put("refresh_token",refreshToken).toString(),false))
            token=obj.getString("access_token");refreshToken=obj.getString("refresh_token");true
        }.getOrDefault(false)
    }
    suspend fun rows(table: String, query: String = ""): List<JSONObject> = withContext(Dispatchers.IO) {
        val r=JSONArray(send("/rest/v1/$table?select=*$query",extra=mapOf("Range" to "0-199")))
        (0 until r.length()).map { r.getJSONObject(it) }
    }
    suspend fun insert(table: String, obj: JSONObject): Unit = withContext(Dispatchers.IO) {
        send("/rest/v1/$table","POST",obj.toString(),extra=mapOf("Prefer" to "return=minimal"))
    }
    suspend fun patch(table: String, id: String, obj: JSONObject): Unit = withContext(Dispatchers.IO) {
        send("/rest/v1/$table?id=eq.$id","PATCH",obj.toString(),extra=mapOf("Prefer" to "return=minimal"))
    }
    suspend fun delete(table: String, id: String): Unit = withContext(Dispatchers.IO) { send("/rest/v1/$table?id=eq.$id","DELETE") }
    suspend fun upsert(table: String, conflict: String, obj: JSONObject): Unit = withContext(Dispatchers.IO) {
        send("/rest/v1/$table?on_conflict=$conflict","POST",obj.toString(),extra=mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"))
    }
    suspend fun finish(workoutId: String, entries: JSONArray, duration: Int, sessionId: String): Unit = withContext(Dispatchers.IO) {
        val workout=rows("workouts","&id=eq.$workoutId").firstOrNull()
            ?: throw IllegalStateException("Workout not found")
        val payload=JSONObject().put("_workout_id",workoutId).put("_name",workout.s("name"))
            .put("_entries",entries).put("_duration_seconds",duration).put("_calories",0)
            .put("_performed_at",Instant.now().toString()).put("_client_session_id",sessionId)
        send("/rest/v1/rpc/finish_workout","POST",payload.toString())
        draftSave("")
    }
}
private fun JSONObject.s(key: String) = optString(key).takeUnless { it=="null" } ?: ""
private fun JSONObject.n(key:String) = optDouble(key,0.0).takeIf { !it.isNaN() } ?: 0.0
private fun rowTitle(o: JSONObject, field: String = "name") = o.s(field).ifBlank { o.s("title").ifBlank { "Untitled" } }

@Composable fun GymApp(api: GymApi) {
    var logged by remember { mutableStateOf(api.loggedIn) }
    var tab by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    fun run(block: suspend () -> Unit) { if(busy)return;busy=true;scope.launch { try { block() } catch(e: Exception) { message=e.message ?: "Operation failed" } finally {busy=false} } }
    if (!logged) {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
            Text("Gym Buddy",fontSize=36.sp,fontWeight=FontWeight.ExtraBold,color=lime)
            Text("Native Android",color=muted)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(email,{email=it},label={Text("Email")},modifier=Modifier.fillMaxWidth(),singleLine=true)
            OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth(),singleLine=true)
            Spacer(Modifier.height(12.dp))
            Button(onClick={run@{run { val result=api.auth(email.trim(),password); message=result;logged=api.loggedIn }}},modifier=Modifier.fillMaxWidth(),enabled=!busy){Text("Sign in")}
            OutlinedButton(onClick={run@{run {message=api.auth(email.trim(),password,true);logged=api.loggedIn}}},modifier=Modifier.fillMaxWidth(),enabled=!busy){Text("Create account")}
            if(message.isNotBlank()) Text(message,color=muted,modifier=Modifier.padding(top=10.dp))
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(18.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("GYM BUDDY",fontWeight=FontWeight.Black,color=lime,fontSize=22.sp)
            Text("Native",color=muted)
        }
        if (message.isNotBlank()) Text(message,color=Color(0xFFFFC180),modifier=Modifier.padding(horizontal=16.dp))
        Box(Modifier.weight(1f)) {
            when(tab) {
                0 -> HomePage(api,::run)
                1 -> TrainPage(api,::run)
                2 -> LogPage(api,::run)
                3 -> MePage(api,::run,{api.logout();logged=false;message=""})
            }
        }
        NavigationBar(containerColor=Color(0xFF1A1E25)) {
            listOf("Today","Train","Log","Me").forEachIndexed { i,t ->
                NavigationBarItem(selected=tab==i,onClick={tab=i;message=""},icon={Text(listOf("⌂","◆","▣","●")[i])},label={Text(t)})
            }
        }
    }
}
@Composable fun Heading(text:String) { Text(text,fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=12.dp)) }
@Composable fun ItemCard(title:String,subtitle:String="",action:String?=null,onClick:(()->Unit)?=null) {
    Card(Modifier.fillMaxWidth().padding(vertical=5.dp),shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.SemiBold);if(subtitle.isNotEmpty())Text(subtitle,color=muted,fontSize=12.sp) }
            if(action!=null && onClick!=null) TextButton(onClick=onClick){Text(action)}
        }
    }
}
@Composable fun HomePage(api:GymApi,run:(suspend ()->Unit)->Unit) {
    var workouts by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var visits by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var selected by remember {mutableStateOf("")}
    var draft by remember {mutableStateOf(api.draftGet())}
    LaunchedEffect(api.userId) { runCatching { api.refresh();workouts=api.rows("workouts","&order=created_at.desc");visits=api.rows("attendance","&order=date.desc") } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Heading("Today")
        Text(LocalDate.now().toString(),color=muted)
        Spacer(Modifier.height(12.dp))
        Text("Your training",fontWeight=FontWeight.Bold)
        if(draft.isNotBlank()) ItemCard("Unfinished workout","Draft stored on this device","Resume"){selected=draft}
        workouts.forEach { w -> ItemCard(rowTitle(w),"Day "+w.optInt("day_number",0),"Start"){selected=w.s("id")} }
        if(selected.isNotBlank()) {
            SessionPanel(api,selected,{selected="";draft=api.draftGet()},run)
        }
        Spacer(Modifier.height(14.dp))
        Text("Recent visits",fontWeight=FontWeight.Bold)
        visits.take(5).forEach{ ItemCard(it.s("date"),"Day "+it.optInt("day_number",0)) }
    }
}
@Composable fun SessionPanel(api:GymApi,workoutId:String,onClose:()->Unit,run:(suspend ()->Unit)->Unit) {
    var sets by remember(workoutId){mutableStateOf<List<JSONObject>>(emptyList())}
    var exercises by remember(workoutId){mutableStateOf<List<JSONObject>>(emptyList())}
    val done=remember(workoutId){mutableStateMapOf<String,Boolean>()}
    val sessionId = remember(workoutId) { UUID.randomUUID().toString() }
    LaunchedEffect(workoutId) {
        runCatching {
            exercises=api.rows("workout_exercises","&workout_id=eq.$workoutId&order=order_index.asc")
            sets=api.rows("workout_sets","&user_id=eq.${api.userId}&order=set_number.asc")
        }
    }
    val mySets=sets.filter { s->exercises.any{it.s("id")==s.s("workout_exercise_id")} }
    LaunchedEffect(done.toMap(),workoutId){api.draftSave(workoutId)}
    Card(Modifier.fillMaxWidth().padding(top=12.dp)){
        Column(Modifier.padding(14.dp)){
            Text("Active workout",fontWeight=FontWeight.Bold,fontSize=19.sp)
            exercises.forEach {ex->
                Text(rowTitle(ex),color=lime,modifier=Modifier.padding(top=10.dp))
                mySets.filter{it.s("workout_exercise_id")==ex.s("id")}.forEach{s->
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Checkbox(checked=done[s.s("id")]==true,onCheckedChange={done[s.s("id")]=it})
                        Text("Set ${s.optInt("set_number")} • ${s.n("weight_kg")} kg × ${s.optInt("reps")}",fontSize=13.sp)
                    }
                }
            }
            Text("Changes stay on this device until saved.",color=muted,fontSize=12.sp)
            Button(
                onClick = {
                    run {
                        val entries = JSONArray()
                        mySets.filter { done[it.s("id")] == true }.forEach { set ->
                            val exercise = exercises.firstOrNull { it.s("id") == set.s("workout_exercise_id") }
                            entries.put(
                                JSONObject().put("exercise", exercise?.s("name") ?: "Exercise")
                                    .put("weight", set.n("weight_kg"))
                                    .put("reps", set.optInt("reps"))
                            )
                        }
                        api.finish(workoutId, entries, 0, sessionId)
                    }
                },
                enabled = done.values.any { it }
            ) { Text("Save completed workout") }
            TextButton(onClick=onClose){Text("Close")}
        }
    }
}
@Composable fun TrainPage(api:GymApi,run:(suspend ()->Unit)->Unit) {
    var workouts by remember {mutableStateOf<List<JSONObject>>(emptyList())}
    var name by remember{mutableStateOf("")}
    var exercises by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var selected by remember{mutableStateOf("")}
    var exerciseName by remember{mutableStateOf("")}
    LaunchedEffect(Unit){runCatching{workouts=api.rows("workouts","&order=created_at.desc")}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Heading("Train")
        OutlinedTextField(name,{name=it},label={Text("New workout name")},modifier=Modifier.fillMaxWidth())
        Button(onClick={run { if(name.isNotBlank()){api.insert("workouts",JSONObject().put("user_id",api.userId).put("name",name.trim()));name="";workouts=api.rows("workouts","&order=created_at.desc")} }}){Text("Create workout")}
        workouts.forEach {w->ItemCard(rowTitle(w),"Routine","Edit"){selected=w.s("id");run { exercises=api.rows("workout_exercises","&workout_id=eq.$selected&order=order_index.asc") }}}
        if(selected.isNotBlank()){
            HorizontalDivider(Modifier.padding(vertical=12.dp))
            Heading("Exercises")
            exercises.forEach {ex->ItemCard(rowTitle(ex),"Rest ${ex.optInt("rest_seconds",90)} seconds")}
            OutlinedTextField(exerciseName,{exerciseName=it},label={Text("Exercise name")},modifier=Modifier.fillMaxWidth())
            Button(onClick={run {if(exerciseName.isNotBlank()){api.insert("workout_exercises",JSONObject().put("user_id",api.userId).put("workout_id",selected).put("name",exerciseName).put("order_index",exercises.size).put("rest_seconds",90));exerciseName="";exercises=api.rows("workout_exercises","&workout_id=eq.$selected&order=order_index.asc")}}}){Text("Add exercise")}
            Text("Set editing and template import are pending.",color=muted)
        }
    }
}
@Composable fun LogPage(api:GymApi,run:(suspend ()->Unit)->Unit) {
    var page by remember{mutableStateOf(0)}
    var visits by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var food by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var logs by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var hadSugar by remember{mutableStateOf(false)}
    var hadOil by remember{mutableStateOf(false)}
    var note by remember{mutableStateOf("")}
    LaunchedEffect(page){runCatching{when(page){0->visits=api.rows("attendance","&order=date.desc");1->food=api.rows("nutrition_days","&order=date.desc");2->logs=api.rows("workout_logs","&order=performed_at.desc")}}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Heading("Log")
        Row{listOf("Gym","Food","Progress").forEachIndexed{i,v->TextButton(onClick={page=i}){Text(v,color=if(page==i)lime else muted)}}}
        when(page){
            0->{
                val today=LocalDate.now().toString()
                val record=visits.firstOrNull{it.s("date")==today}
                if(record==null) Button(onClick={run {api.insert("attendance",JSONObject().put("user_id",api.userId).put("date",today).put("check_in",Instant.now().toString()));visits=api.rows("attendance","&order=date.desc")}}){Text("Check in")}
                else if(record.isNull("check_out")) Button(onClick={run{api.patch("attendance",record.s("id"),JSONObject().put("check_out",Instant.now().toString()));visits=api.rows("attendance","&order=date.desc")}}){Text("Check out")}
                else Text("Today's visit is complete.",color=lime)
                visits.forEach{ItemCard(it.s("date"),"In: "+it.s("check_in").take(16)+" · Out: "+it.s("check_out").take(16))}
            }
            1->{
                val today=LocalDate.now().toString()
                val row=food.firstOrNull{it.s("date")==today}
                LaunchedEffect(row?.s("id")){hadSugar=row?.optBoolean("had_sugar")?:false;hadOil=row?.optBoolean("had_oil")?:false;note=row?.s("note")?:""}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(hadSugar,{hadSugar=it});Text("Had sugar today")}
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(hadOil,{hadOil=it});Text("Had oil today")}
                OutlinedTextField(note,{note=it},label={Text("Meal note")},modifier=Modifier.fillMaxWidth())
                Button(onClick={run{api.upsert("nutrition_days","user_id,date",JSONObject().put("user_id",api.userId).put("date",today).put("had_sugar",hadSugar).put("had_oil",hadOil).put("note",note));food=api.rows("nutrition_days","&order=date.desc")}}){Text("Save today's food")}
                food.take(25).forEach{ItemCard(it.s("date"),"Sugar: ${it.optBoolean("had_sugar")} · Oil: ${it.optBoolean("had_oil")} · ${it.s("note")}") }
            }
            2->logs.forEach{ItemCard(rowTitle(it),"Duration ${it.optInt("duration_seconds")/60} min • ${it.optInt("total_sets")} sets • ${it.n("total_volume")} kg")}
        }
    }
}
@Composable fun MePage(api:GymApi,run:(suspend ()->Unit)->Unit,onLogout:()->Unit) {
    var profile by remember{mutableStateOf<JSONObject?>(null)}
    var goals by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var prs by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var friends by remember{mutableStateOf<List<JSONObject>>(emptyList())}
    var name by remember{mutableStateOf("")}
    var weight by remember{mutableStateOf("")}
    LaunchedEffect(Unit){runCatching {
        profile=api.rows("profiles","&id=eq.${api.userId}").firstOrNull()
        name=profile?.s("name")?:"";weight=profile?.s("current_weight_kg")?:""
        goals=api.rows("goals");prs=api.rows("personal_records");friends=api.rows("friendships")
    }}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)){
        Heading("Me")
        OutlinedTextField(name,{name=it},label={Text("Name")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(weight,{weight=it},label={Text("Weight in kg")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
        Button(onClick={run{api.patch("profiles",api.userId,JSONObject().put("name",name).put("current_weight_kg",weight.toDoubleOrNull()));profile=api.rows("profiles","&id=eq.${api.userId}").firstOrNull()}}){Text("Save profile")}
        Text("Friend code: "+(profile?.s("friend_code")?:"—"),color=muted)
        Spacer(Modifier.height(20.dp));Heading("Goals")
        goals.forEach{ItemCard(rowTitle(it,"title"),"${it.n("current_value")} / ${it.n("target_value")} ${it.s("unit")}") }
        Heading("Personal records")
        prs.forEach{ItemCard(it.s("exercise_name"),"${it.n("weight_kg")} kg × ${it.optInt("reps")}") }
        Heading("Friends")
        friends.forEach{ItemCard("Connection",it.s("status"))}
        Text("Friend invitations, body measurements and goal editing are not yet implemented.",color=muted,fontSize=12.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick=onLogout){Text("Sign out")}
    }
}
