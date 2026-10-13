package com.gymbuddy.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/** Direct native workout editor: workouts → exercises → sets. */
@Composable
fun TrainNative(api: GymApi, run: (suspend () -> Unit) -> Unit) {
    var workouts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var templates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var exercises by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var sets by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedWorkout by remember { mutableStateOf("") }
    var selectedExercise by remember { mutableStateOf("") }
    var workoutName by remember { mutableStateOf("") }
    var exerciseName by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("0") }
    var reps by remember { mutableStateOf("8") }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    suspend fun reloadWorkouts() { workouts = api.rows("workouts","&order=created_at.desc") }
    suspend fun reloadExercises() { if(selectedWorkout.isNotBlank()) exercises = api.rows("workout_exercises","&workout_id=eq.$selectedWorkout&order=order_index.asc") }
    suspend fun reloadSets() { if(selectedExercise.isNotBlank()) sets = api.rows("workout_sets","&workout_exercise_id=eq.$selectedExercise&order=set_number.asc") }
    LaunchedEffect(Unit) {
        runCatching { reloadWorkouts(); templates = api.rows("workout_templates","&order=created_at.asc") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Train", style=MaterialTheme.typography.headlineMedium, fontWeight=FontWeight.Bold)
        if (selectedWorkout.isBlank()) {
            OutlinedTextField(workoutName,{workoutName=it},label={Text("Name your workout")},modifier=Modifier.fillMaxWidth())
            Button(onClick={run {
                if(workoutName.isBlank()) throw IllegalArgumentException("Name required")
                api.insert("workouts",JSONObject().put("user_id",api.userId).put("name",workoutName.trim()))
                workoutName="";reloadWorkouts()
            }},enabled=workoutName.isNotBlank()){Text("Create workout")}
            Spacer(Modifier.height(16.dp))
            Text("My workouts",style=MaterialTheme.typography.titleLarge)
            workouts.forEach{w->
                Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                    Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(w.optString("name"),Modifier.weight(1f))
                        TextButton(onClick={selectedWorkout=w.getString("id");run { reloadExercises() }}){Text("Edit")}
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Starter templates",style=MaterialTheme.typography.titleLarge)
            templates.forEach { template ->
                Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(template.optString("name"),fontWeight=FontWeight.Bold)
                            Text(template.optString("description"),style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={run {
                            val t=api.insertReturning("workouts",JSONObject().put("user_id",api.userId).put("name",template.optString("name")))
                            val workoutId=t.getString("id")
                            try {
                                val templateExercises=template.getJSONArray("exercises")
                                for (i in 0 until templateExercises.length()) {
                                    val ex=templateExercises.getJSONObject(i)
                                    val created=api.insertReturning("workout_exercises",
                                        JSONObject().put("user_id",api.userId).put("workout_id",workoutId)
                                            .put("name",ex.optString("name")).put("order_index",i)
                                            .put("rest_seconds",ex.optInt("rest",90)))
                                    for (n in 1..ex.optInt("sets",3).coerceIn(1,15)) {
                                        api.insert("workout_sets",JSONObject().put("user_id",api.userId)
                                            .put("workout_exercise_id",created.getString("id")).put("set_number",n)
                                            .put("weight_kg",0).put("reps",ex.optInt("reps",8)))
                                    }
                                }
                            } catch (e: Exception) {
                                api.delete("workouts",workoutId)
                                throw e
                            }
                            reloadWorkouts()
                        }}){Text("Use")}
                    }
                }
            }
        } else {
            val current=workouts.firstOrNull{it.optString("id")==selectedWorkout}
            Row {
                TextButton(onClick={selectedWorkout="";selectedExercise=""}){Text("← All workouts")}
                Spacer(Modifier.weight(1f))
                TextButton(onClick={confirmRemove=selectedWorkout}){Text("Delete workout")}
            }
            Text(current?.optString("name") ?: "Workout",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(selectedExercise.isBlank()){
                Text("Exercises",fontWeight=FontWeight.Bold)
                exercises.forEach{ex->
                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){
                        Row(Modifier.padding(12.dp)){
                            Text(ex.optString("name"),Modifier.weight(1f))
                            TextButton(onClick={selectedExercise=ex.getString("id");run{reloadSets()}}){Text("Sets")}
                            TextButton(onClick={confirmRemove=ex.getString("id")}){Text("Remove")}
                        }
                    }
                }
                OutlinedTextField(exerciseName,{exerciseName=it},label={Text("Exercise name")},modifier=Modifier.fillMaxWidth())
                Button(onClick={run {
                    if(exerciseName.isNotBlank()) {
                        api.insert("workout_exercises",JSONObject().put("user_id",api.userId)
                            .put("workout_id",selectedWorkout).put("name",exerciseName.trim())
                            .put("order_index",exercises.size).put("rest_seconds",90))
                        exerciseName="";reloadExercises()
                    }
                }},enabled=exerciseName.isNotBlank()){Text("Add exercise")}
            } else {
                TextButton(onClick={selectedExercise=""}){Text("← Exercises")}
                Text(exercises.firstOrNull{it.optString("id")==selectedExercise}?.optString("name") ?: "Exercise",style=MaterialTheme.typography.titleLarge)
                sets.forEach{s->
                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                        Row(Modifier.padding(10.dp)) {
                            Text("Set ${s.optInt("set_number")}: ${s.optString("weight_kg")} kg × ${s.optInt("reps")}",Modifier.weight(1f))
                            TextButton(onClick={confirmRemove=s.getString("id")}){Text("Delete")}
                        }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(weight,{weight=it},label={Text("kg")},modifier=Modifier.weight(1f))
                    OutlinedTextField(reps,{reps=it},label={Text("Reps")},modifier=Modifier.weight(1f))
                }
                Button(onClick={run {
                    val kg=weight.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid weight")
                    val repCount=reps.toIntOrNull() ?: throw IllegalArgumentException("Invalid reps")
                    require(kg>=0 && repCount in 1..100){"Enter valid weight and repetitions"}
                    api.insert("workout_sets",JSONObject().put("user_id",api.userId).put("workout_exercise_id",selectedExercise)
                        .put("set_number",(sets.maxOfOrNull{it.optInt("set_number")} ?: 0)+1)
                        .put("weight_kg",kg).put("reps",repCount))
                    reloadSets()
                }}){Text("Add set")}
            }
        }
    }
    val removing = confirmRemove
    if(removing != null) AlertDialog(
        onDismissRequest={confirmRemove=null},title={Text("Delete this item?")},
        text={Text("This cannot be undone. Completed workout history is stored separately.")},
        confirmButton={TextButton(onClick={
            val table=when {
                removing==selectedWorkout -> "workouts"
                removing==selectedExercise || exercises.any{it.optString("id")==removing} -> "workout_exercises"
                else -> "workout_sets"
            }
            confirmRemove=null
            run {
                api.delete(table,removing)
                when(table) {
                    "workouts" -> {selectedWorkout="";selectedExercise="";reloadWorkouts()}
                    "workout_exercises" -> {selectedExercise="";reloadExercises()}
                    else -> reloadSets()
                }
            }
        }){Text("Delete")}},
        dismissButton={TextButton(onClick={confirmRemove=null}){Text("Cancel")}}
    )
}
