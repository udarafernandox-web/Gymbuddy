package com.gymbuddy.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private data class SetInput(val weight:String, val reps:String, val done:Boolean)

@Composable
fun SessionNative(
    api: GymApi,
    workoutId: String,
    onClose: () -> Unit,
    run: (suspend () -> Unit) -> Unit
) {
    var exercises by remember(workoutId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sets by remember(workoutId) { mutableStateOf<List<JSONObject>>(emptyList()) }
    val entries = remember(workoutId) { mutableStateMapOf<String,SetInput>() }
    var error by remember(workoutId) { mutableStateOf("") }
    var ready by remember(workoutId) { mutableStateOf(false) }
    var saving by remember(workoutId) { mutableStateOf(false) }
    var resting by remember(workoutId) { mutableIntStateOf(0) }
    var elapsed by remember(workoutId) { mutableIntStateOf(0) }
    var paused by remember(workoutId) { mutableStateOf(false) }
    val clientSessionId = remember(workoutId) { api.sessionUuid(workoutId) }

    LaunchedEffect(workoutId) {
        try {
            exercises = api.rows("workout_exercises","&workout_id=eq.$workoutId&order=order_index.asc")
            val allSets = api.rows("workout_sets","&user_id=eq.${api.userId}&order=set_number.asc")
            sets = allSets.filter { item -> exercises.any { it.optString("id")==item.optString("workout_exercise_id") } }
            val saved=api.draftDetails(workoutId)
            val history=api.rows("workout_logs","&order=performed_at.desc&limit=10")
            val latestByName=mutableMapOf<String,Pair<String,String>>()
            history.forEach { log ->
                val records=log.optJSONArray("entries") ?: JSONArray()
                for(i in 0 until records.length()) {
                    val entry=records.optJSONObject(i) ?: continue
                    val name=entry.optString("exercise")
                    if(name.isNotBlank()&&!latestByName.containsKey(name)) latestByName[name] =
                        Pair(entry.optString("weight","0"),entry.optString("reps","8"))
                }
            }
            sets.forEach { item ->
                val id=item.getString("id")
                val prior=saved.optJSONObject(id)
                val exercise=exercises.firstOrNull{it.optString("id")==item.optString("workout_exercise_id")}
                val fallback=latestByName[exercise?.optString("name") ?: ""]
                entries[id] = SetInput(
                    prior?.optString("weight") ?: fallback?.first ?: item.optString("weight_kg","0"),
                    prior?.optString("reps") ?: fallback?.second ?: item.optString("reps","8"),
                    prior?.optBoolean("done") ?: false
                )
            }
            api.draftSave(workoutId)
        } catch(e: Exception) { error=e.message ?: "Failed to load session" }
        finally {ready=true}
    }
    LaunchedEffect(ready, entries.toMap()) {
        if (ready && error.isBlank()) {
            val json=JSONObject()
            entries.forEach { (id,v) ->
                json.put(id,JSONObject().put("weight",v.weight).put("reps",v.reps).put("done",v.done))
            }
            api.saveDraftDetails(workoutId,json)
        }
    }
    LaunchedEffect(workoutId) {
        while(true) {
            delay(1000)
            if(!paused) elapsed += 1
            if(resting>0) resting -= 1
        }
    }
    fun mark(id:String,done:Boolean) {
        val prev=entries[id] ?: return
        entries[id]=prev.copy(done=done)
        if(done) resting=90
    }
    Card(Modifier.fillMaxWidth().padding(top=12.dp)){
        Column(Modifier.padding(14.dp)) {
            Text("Live workout",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Elapsed ${elapsed/60}:${(elapsed%60).toString().padStart(2,'0')}")
                TextButton(onClick={paused=!paused}) { Text(if(paused)"Resume" else "Pause") }
            }
            if (resting>0) {
                Text("Rest: ${resting}s",color=MaterialTheme.colorScheme.primary)
                Row {
                    TextButton(onClick={resting=0}){Text("Skip")}
                    TextButton(onClick={resting+=30}){Text("+30s")}
                }
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            if(!ready) CircularProgressIndicator()
            exercises.forEach { ex ->
                Text(ex.optString("name"),fontWeight=FontWeight.Bold,
                    color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=12.dp))
                sets.filter{it.optString("workout_exercise_id")==ex.optString("id")}.forEach { item ->
                    val id=item.optString("id")
                    val input=entries[id] ?: SetInput("0","8",false)
                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(checked=input.done,onCheckedChange={mark(id,it)})
                                Text("Set ${item.optInt("set_number")}",fontWeight=FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(input.weight,{value->entries[id]=input.copy(weight=value)},
                                    label={Text("kg")},modifier=Modifier.weight(1f),
                                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                                OutlinedTextField(input.reps,{value->entries[id]=input.copy(reps=value)},
                                    label={Text("Reps")},modifier=Modifier.weight(1f),
                                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                            }
                            Row {
                                TextButton(onClick={
                                    val v=(input.weight.toDoubleOrNull()?:0.0)
                                    entries[id]=input.copy(weight=(v-2.5).coerceAtLeast(0.0).toString())
                                }){Text("−2.5 kg")}
                                TextButton(onClick={
                                    val v=(input.weight.toDoubleOrNull()?:0.0)
                                    entries[id]=input.copy(weight=(v+2.5).toString())
                                }){Text("+2.5 kg")}
                            }
                        }
                    }
                }
            }
            val completed=entries.values.count{it.done}
            Text("$completed / ${sets.size} sets completed",modifier=Modifier.padding(vertical=8.dp))
            Button(onClick={
                if(saving)return@Button
                saving=true
                run {
                    try {
                        val result=JSONArray()
                        sets.forEach{item->
                            val entry=entries[item.optString("id")] ?: return@forEach
                            if(entry.done) {
                                val weight=entry.weight.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid weight")
                                val reps=entry.reps.toIntOrNull() ?: throw IllegalArgumentException("Invalid reps")
                                require(weight>=0 && reps in 1..100){"Enter valid weight and reps"}
                                val exercise=exercises.firstOrNull{it.optString("id")==item.optString("workout_exercise_id")}
                                result.put(JSONObject().put("exercise",exercise?.optString("name") ?: "Exercise")
                                    .put("weight",weight).put("reps",reps))
                            }
                        }
                        require(result.length()>0){"Complete at least one set"}
                        api.finish(workoutId,result,elapsed,clientSessionId)
                        api.clearDraftDetails(workoutId)
                        onClose()
                    } finally {saving=false}
                }
            },enabled=completed>0 && !saving && ready){Text(if(saving)"Saving…" else "Finish and save workout")}
            TextButton(onClick=onClose){Text("Continue later")}
        }
    }
}
