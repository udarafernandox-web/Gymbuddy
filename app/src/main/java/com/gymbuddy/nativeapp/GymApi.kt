package com.gymbuddy.nativeapp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class GymApi(private val context: Context) {
 private val http = OkHttpClient()
 private val prefs = context.getSharedPreferences("gymbuddy_native_auth", Context.MODE_PRIVATE)
 val configured get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()
 var token: String
  get() = prefs.getString("token","") ?: ""
  private set(value) { prefs.edit().putString("token",value).apply() }
 var userId: String
  get() = prefs.getString("userId","") ?: ""
  private set(value) { prefs.edit().putString("userId",value).apply() }
 val signedIn get() = token.isNotBlank() && userId.isNotBlank()
 private val jsonType = "application/json; charset=utf-8".toMediaType()
 suspend fun request(path: String, method: String = "GET", data: String? = null, authenticate: Boolean = true, prefer: String? = null): String = withContext(Dispatchers.IO) {
  check(configured) { "Configure the Supabase URL and publishable key using GitHub Actions secrets." }
  val url = BuildConfig.SUPABASE_URL.trimEnd('/') + path
  val req = Request.Builder().url(url).header("apikey",BuildConfig.SUPABASE_KEY)
     .header("Accept","application/json")
  if (authenticate && token.isNotBlank()) req.header("Authorization","Bearer $token")
  if (prefer != null) req.header("Prefer",prefer)
  val body = (data ?: "").toRequestBody(jsonType)
  req.method(method, if (method == "GET" || method == "DELETE") null else body)
  http.newCall(req.build()).execute().use { response ->
   val text = response.body?.string() ?: ""
   if (!response.isSuccessful) {
    val msg = runCatching { JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error_description") } }.getOrNull().orEmpty()
    throw IllegalStateException(msg.ifBlank { "Request failed: HTTP ${response.code}" })
   }
   text
  }
 }
 suspend fun signIn(email: String, password: String) {
  val data = JSONObject().put("email",email).put("password",password)
  val result = JSONObject(request("/auth/v1/token?grant_type=password","POST",data.toString(),false))
  val id = result.getJSONObject("user").getString("id")
  token = result.getString("access_token"); userId = id
 }
 suspend fun signUp(email: String,password: String) {
  val data = JSONObject().put("email",email).put("password",password)
  request("/auth/v1/signup","POST",data.toString(),false)
 }
 fun signOut() { token = ""; userId = "" }
 private fun enc(s:String) = URLEncoder.encode(s,"UTF-8")
 suspend fun list(table:String, order:String? = null, extra:String = ""):JSONArray {
  val suffix = if (order == null) "" else "&order=${enc(order)}"
  return JSONArray(request("/rest/v1/$table?select=*$suffix$extra"))
 }
 suspend fun mine(table:String, key:String="user_id",order:String?=null):JSONArray =
  list(table,order,"&$key=eq.${enc(userId)}")
 suspend fun insert(table:String, data:JSONObject):String = request("/rest/v1/$table","POST",data.toString(),prefer="return=representation")
 suspend fun patch(table:String,id:String,data:JSONObject) = request("/rest/v1/$table?id=eq.${enc(id)}","PATCH",data.toString())
 suspend fun remove(table:String,id:String) = request("/rest/v1/$table?id=eq.${enc(id)}","DELETE")
 suspend fun upsertNutrition(date:String,sugar:Boolean,oil:Boolean,note:String) {
  val row=JSONObject().put("user_id",userId).put("date",date).put("had_sugar",sugar).put("had_oil",oil).put("note",note)
  request("/rest/v1/nutrition_days?on_conflict=user_id,date","POST",row.toString(),prefer="resolution=merge-duplicates")
 }
 suspend fun finishWorkout(workoutId:String,name:String,seconds:Int,sets:JSONArray,sessionId:String) {
  val args=JSONObject().put("p_workout_id",workoutId).put("p_workout_name",name)
    .put("p_duration_seconds",seconds).put("p_client_session_id",sessionId).put("p_sets",sets)
  // The web app has a finish_workout RPC, but its argument names must be verified before enabling native writes.
  throw UnsupportedOperationException("Native workout completion needs verification against the live finish_workout RPC signature. No data was written.")
 }
}
