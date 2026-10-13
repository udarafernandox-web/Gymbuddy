package com.gymbuddy.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Native management panels backed by the existing Supabase tables and RPCs. */
@Composable
fun ExtraFeatures(api: GymApi, run: (suspend () -> Unit) -> Unit) {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = 20.dp))
        Text("Manage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row {
            listOf("Friends", "Goals", "Measurements").forEachIndexed { index, title ->
                TextButton(onClick = { section = index }) {
                    Text(title, color = if (section == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        when (section) {
            0 -> FriendsPanel(api, run)
            1 -> GoalsPanel(api, run)
            else -> MeasurementsPanel(api, run)
        }
    }
}

@Composable
private fun FriendsPanel(api: GymApi, run: (suspend () -> Unit) -> Unit) {
    var code by remember { mutableStateOf("") }
    var friendList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    suspend fun reload() {
        val arr = JSONArray(api.rpc("list_friends"))
        friendList = (0 until arr.length()).map { arr.getJSONObject(it) }
    }
    LaunchedEffect(api.userId) { runCatching { reload() }.onFailure { message = it.message.orEmpty() } }
    Column {
        Text("Friend code", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = code, onValueChange = { code = it.uppercase().take(40) },
            label = { Text("Enter a friend's code") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            run {
                val matches = JSONArray(api.rpc("find_friend_by_code", JSONObject().put("_code", code.trim())))
                if (matches.length() == 0) throw IllegalStateException("No user found for that code.")
                val id = matches.getJSONObject(0).getString("id")
                if (id == api.userId) throw IllegalStateException("You cannot add yourself.")
                api.insert("friendships", JSONObject().put("requester_id", api.userId)
                    .put("addressee_id", id).put("status", "pending"))
                message = "Friend request sent."
                code = ""
                reload()
            }
        }, enabled = code.isNotBlank()) { Text("Send friend request") }
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        friendList.forEach { friend ->
            val id = friend.optString("friendship_id")
            val status = friend.optString("status")
            val incoming = friend.optBoolean("incoming")
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(friend.optString("name", "Gym Buddy member"), fontWeight = FontWeight.Bold)
                    Text(status + if (incoming && status == "pending") " · Request received" else "", style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (incoming && status == "pending") {
                            TextButton(onClick = { run { api.patch("friendships", id, JSONObject().put("status","accepted")); reload() } }) {
                                Text("Accept")
                            }
                        }
                        var confirmDelete by remember(id) { mutableStateOf(false) }
                        TextButton(onClick = { confirmDelete = true }) { Text(if (status == "pending") "Decline / Cancel" else "Remove") }
                        if (confirmDelete) AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("Remove connection?") },
                            text = { Text("This removes the friend relationship or pending request.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmDelete = false
                                    run { api.delete("friendships", id); reload() }
                                }) { Text("Remove") }
                            },
                            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalsPanel(api: GymApi, run: (suspend () -> Unit) -> Unit) {
    var goals by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    LaunchedEffect(api.userId) { runCatching { goals = api.rows("goals","&order=created_at.desc") } }
    Column {
        Text("Add goal", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(title, { title = it }, label = { Text("Goal name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(target, { target = it }, label = { Text("Target") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            run {
                val t = target.toDoubleOrNull() ?: throw IllegalArgumentException("Enter a valid target")
                require(t > 0) { "Target must be greater than zero" }
                api.insert("goals", JSONObject().put("user_id", api.userId).put("title", title.trim())
                    .put("kind", "custom").put("target_value", t).put("current_value", 0)
                    .put("unit", unit.trim()).put("completed", false))
                goals = api.rows("goals","&order=created_at.desc")
                title = ""; target = ""
            }
        }, enabled = title.isNotBlank()) { Text("Create goal") }
        goals.forEach { goal ->
            var progress by remember(goal.optString("id")) { mutableStateOf(goal.optString("current_value", "0")) }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(goal.optString("title"), fontWeight = FontWeight.Bold)
                    Text("Target: ${goal.optString("target_value")} ${goal.optString("unit")}")
                    OutlinedTextField(progress,{progress=it},label={Text("Current progress")})
                    Row {
                        TextButton(onClick = {
                            run {
                                val value = progress.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid progress")
                                api.patch("goals",goal.getString("id"),JSONObject().put("current_value",value)
                                    .put("completed",value >= goal.optDouble("target_value")))
                                goals = api.rows("goals","&order=created_at.desc")
                            }
                        }) { Text("Save progress") }
                        var confirm by remember(goal.optString("id")) { mutableStateOf(false) }
                        TextButton(onClick = { confirm = true }) { Text("Delete") }
                        if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Delete goal?")},
                            confirmButton={TextButton(onClick={confirm=false;run{api.delete("goals",goal.getString("id"));goals=api.rows("goals")}}){Text("Delete")}},
                            dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurementsPanel(api: GymApi, run: (suspend () -> Unit) -> Unit) {
    var values by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    LaunchedEffect(api.userId) { runCatching { values = api.rows("body_measurements","&order=date.desc") } }
    Column {
        Text("Body measurements", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(weight, { weight=it }, label={Text("Weight (kg)")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(waist, { waist=it }, label={Text("Waist (cm)")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(bodyFat, { bodyFat=it }, label={Text("Body fat (%)")},modifier=Modifier.fillMaxWidth())
        Button(onClick={
            run {
                val obj=JSONObject().put("user_id",api.userId).put("date",LocalDate.now().toString())
                if(weight.isNotBlank()) obj.put("weight_kg",weight.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid weight"))
                if(waist.isNotBlank()) obj.put("waist_cm",waist.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid waist"))
                if(bodyFat.isNotBlank()) obj.put("body_fat_pct",bodyFat.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid body fat"))
                api.insert("body_measurements",obj)
                values=api.rows("body_measurements","&order=date.desc")
                weight="";waist="";bodyFat=""
            }
        },enabled=weight.isNotBlank()||waist.isNotBlank()||bodyFat.isNotBlank()){Text("Log measurements")}
        values.take(30).forEach { m ->
            Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                Text("${m.optString("date")} · ${m.optString("weight_kg")} kg · Waist ${m.optString("waist_cm")} cm · Body fat ${m.optString("body_fat_pct")}%", modifier=Modifier.padding(12.dp))
            }
        }
    }
}
