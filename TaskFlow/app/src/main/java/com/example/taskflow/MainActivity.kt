package com.example.taskflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.taskflow.ui.theme.TaskFlowTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaskFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SaveToFirebaseScreen()
                }
            }
        }
    }
}

@Composable
fun SaveToFirebaseScreen() {
    var isTaskFormVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.Red) } // Default color
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val taskManager = remember { TaskManagement(db, auth) }
    val currentUser = auth.currentUser
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val tasks = remember { mutableStateListOf<Task>() }
    var selectedTask by remember { mutableStateOf<Task?>(null) }
    var isDialogOpen by remember { mutableStateOf(false) }

    // Fetch tasks associated with the user's email
    LaunchedEffect(currentUser) {
        currentUser?.email?.let { email ->
            db.collection("userInputs")
                .whereEqualTo("userEmail", email)
                .get()
                .addOnSuccessListener { documents ->
                    tasks.clear()
                    for (document in documents) {
                        val taskText = document.getString("taskText") ?: ""
                        val taskDescription = document.getString("description") ?: ""

                        val subtasksData = document.get("subtasks") as? List<Map<String, Any>> ?: emptyList()
                        val formattedSubtasks = subtasksData.map {
                            Pair(it["name"] as? String ?: "", it["isCompleted"] as? Boolean ?: false)
                        }

                        tasks.add(Task(taskText, taskDescription, document.id, formattedSubtasks))
                    }
                }
                .addOnFailureListener { e ->
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to fetch tasks: ${e.message}")
                    }
                }
        }
    }

    val filteredTasks = tasks.filter { task ->
        task.text.contains(searchQuery, ignoreCase = true)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Black top half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            )

            // White bottom half
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            SignOutButton(
                auth = auth,
                snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope,
                onSignOut = {
                    val intent = Intent(context, LoginActivity::class.java)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Tasks") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Color Picker
            ColorPicker(selectedColor) { color ->
                selectedColor = color // Update the selected color
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { isTaskFormVisible = !isTaskFormVisible },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isTaskFormVisible) "Hide Task Form" else "Add Task")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isTaskFormVisible) {
                TaskForm(
                    onSave = { taskText, description, subtasks ->
                        taskManager.saveTask(taskText, description, "General", subtasks, tasks, coroutineScope, snackbarHostState)
                        isTaskFormVisible = false
                    },
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope
                )
            }

            // Display Task Cards horizontally under the search bar
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTasks) { task ->
                    TaskCard(
                        task = task,
                        onClick = {
                            selectedTask = task
                            isDialogOpen = true
                        },
                        color = selectedColor // Pass the selected color
                    )
                }
            }
        }

        // Progress Cards at the bottom of the screen
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                ProgressCard(task = task)
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (isDialogOpen && selectedTask != null) {
        TaskEditDialog(
            task = selectedTask!!,
            onDismiss = { isDialogOpen = false },
            onUpdate = { editedText, editedDescription, updatedSubtasks ->
                taskManager.updateTask(
                    selectedTask!!.id,
                    editedText,
                    editedDescription,
                    updatedSubtasks,
                    tasks,
                    tasks.indexOf(selectedTask!!),
                    coroutineScope,
                    snackbarHostState
                )
                isDialogOpen = false
            },
            onDelete = {
                taskManager.deleteTask(
                    selectedTask!!.id,
                    tasks,
                    tasks.indexOf(selectedTask!!),
                    coroutineScope,
                    snackbarHostState
                )
                isDialogOpen = false
            }
        )
    }
}


@Composable
fun ColorPicker(selectedColor: Color, onColorSelected: (Color) -> Unit) {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color)
                    .clickable { onColorSelected(color) } // Update selected color on click
                    .border(2.dp, if (color == selectedColor) Color.Black else Color.Transparent)
            )
        }
    }
}

@Composable
fun ProgressCard(task: Task) {
    // Calculate progress
    val completedSubtasks = task.subtasks.count { it.second }
    val totalSubtasks = task.subtasks.size
    val progress = if (totalSubtasks > 0) completedSubtasks / totalSubtasks.toFloat() else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = task.text, style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.height(8.dp))

            if (totalSubtasks > 0) {
                Text(text = "$completedSubtasks / $totalSubtasks subtasks completed")
            } else {
                Text(text = "No subtasks")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )
        }
    }
}

@Composable
fun TaskForm(
    onSave: (String, String, List<Pair<String, Boolean>>) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    var text by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var subtaskText by remember { mutableStateOf("") }
    var subtasks = remember { mutableStateListOf<Pair<String, Boolean>>() }

    Column {
        TextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Enter task") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Enter description") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = subtaskText,
            onValueChange = { subtaskText = it },
            label = { Text("Enter subtask") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            if (subtaskText.isNotEmpty()) {
                subtasks.add(Pair(subtaskText, false))
                subtaskText = ""
            }
        }) {
            Text("Add Subtask")
        }

        LazyColumn {
            items(subtasks) { (name, isCompleted) ->
                Text(text = name)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (text.isNotEmpty()) {
                onSave(text, description, subtasks.toList())
                text = ""
                description = ""
                subtasks.clear()
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Task text cannot be empty")
                }
            }
        }) {
            Text("Save Task")
        }
    }
}

@Composable
fun TaskCard(task: Task, onClick: () -> Unit, color: Color) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick)
            .background(color), // Set the background color using a Modifier
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = task.text, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = task.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TaskEditDialog(
    task: Task,
    onDismiss: () -> Unit,
    onUpdate: (String, String, List<Pair<String, Boolean>>) -> Unit,
    onDelete: () -> Unit
) {
    var editedText by remember { mutableStateOf(task.text) }
    var editedDescription by remember { mutableStateOf(task.description) }
    var editedSubtaskText by remember { mutableStateOf("") }
    var subtasks = remember { mutableStateListOf<Pair<String, Boolean>>() }
    subtasks.addAll(task.subtasks)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column {
                TextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    label = { Text("Edit Task") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = editedDescription,
                    onValueChange = { editedDescription = it },
                    label = { Text("Edit Description") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = editedSubtaskText,
                    onValueChange = { editedSubtaskText = it },
                    label = { Text("Edit Subtask") }
                )
                Button(onClick = {
                    if (editedSubtaskText.isNotEmpty()) {
                        subtasks.add(Pair(editedSubtaskText, false))
                        editedSubtaskText = ""
                    }
                }) {
                    Text("Add Subtask")
                }
                LazyColumn {
                    items(subtasks) { (name, _) ->
                        Text(text = name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdate(editedText, editedDescription, subtasks.toList())
            }) {
                Text("Update")
            }
        },
        dismissButton = {
            Button(onClick = onDelete) {
                Text("Delete")
            }
        }
    )
}

@Composable
fun SignOutButton(
    auth: FirebaseAuth,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    onSignOut: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) { // Box allows for absolute positioning
        Button(
            onClick = {
                auth.signOut()
                onSignOut()
            },
            modifier = Modifier.align(Alignment.TopEnd) // Aligns button to the top end
        ) {
            Text("Sign Out")
        }
    }
}