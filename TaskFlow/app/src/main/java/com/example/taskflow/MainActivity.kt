package com.example.taskflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val taskManager = remember { TaskManagement(db, auth) }
    val currentUser = auth.currentUser
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Mutable list to hold user inputs with their Firebase document IDs, descriptions, and categories
    val tasks = remember { mutableStateListOf<Task>() }
    var selectedTaskIndex by remember { mutableStateOf<Int?>(null) }
    var isDialogOpen by remember { mutableStateOf(false) }

    // Fetch tasks associated with the user's email
    LaunchedEffect(currentUser) {
        currentUser?.email?.let { email ->
            db.collection("userInputs")
                .whereEqualTo("userEmail", email)
                .get()
                .addOnSuccessListener { documents ->
                    tasks.clear() // Clear previous tasks before adding new ones
                    for (document in documents) {
                        val taskText = document.getString("taskText") ?: ""
                        val taskDescription = document.getString("description") ?: ""

                        // Retrieve subtasks correctly
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

        // Toggle Task Form visibility
        Button(
            onClick = { isTaskFormVisible = !isTaskFormVisible },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isTaskFormVisible) "Hide Task Form" else "Add Task")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show Task Form if visible
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

        // Task List
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                val taskIndex = tasks.indexOf(task)
                TaskCard(
                    task = task,
                    onClick = {
                        selectedTaskIndex = taskIndex
                        isDialogOpen = true
                    }
                )
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    // Edit Task Dialog
    if (isDialogOpen && selectedTaskIndex != null) {
        val taskToEdit = tasks[selectedTaskIndex!!]
        TaskEditDialog(
            task = taskToEdit,
            onDismiss = { isDialogOpen = false },
            onUpdate = { editedText, editedDescription, updatedSubtasks ->
                taskManager.updateTask(
                    taskToEdit.id,
                    editedText,
                    editedDescription,
                    updatedSubtasks,
                    tasks,
                    selectedTaskIndex!!,
                    coroutineScope,
                    snackbarHostState
                )
                isDialogOpen = false
            },
            onDelete = {
                taskManager.deleteTask(
                    taskToEdit.id,
                    tasks,
                    selectedTaskIndex!!,
                    coroutineScope,
                    snackbarHostState
                )
                isDialogOpen = false
            }
        )
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
    var subtasks = remember { mutableStateListOf<Pair<String, Boolean>>() } // Mutable list for subtasks

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

        Button(
            onClick = {
                if (subtaskText.isNotEmpty()) {
                    subtasks.add(Pair(subtaskText, false)) // Add the subtask with completed status
                    subtaskText = "" // Clear subtask input
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Subtask")
        }

        // Display subtasks with checkboxes
        subtasks.forEachIndexed { index, (subtask, isCompleted) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = {
                        subtasks[index] = Pair(subtask, it) // Update completion status
                    }
                )
                Text(text = subtask)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (text.isNotEmpty()) {
                    onSave(text, description, subtasks.toList()) // Call save function with subtasks
                    text = "" // Clear task input
                    description = "" // Clear description input
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Please enter a task.")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Task")
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(100.dp)
            .clickable(onClick = onClick), // Handle click to show dialog
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = task.text) // Only show task name
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
    var updatedText by remember { mutableStateOf(task.text) }
    var updatedDescription by remember { mutableStateOf(task.description) }
    var subtasks = remember { mutableStateListOf<Pair<String, Boolean>>().apply { addAll(task.subtasks) } }
    var newSubtaskText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Task") },
        text = {
            Column {
                TextField(
                    value = updatedText,
                    onValueChange = { updatedText = it },
                    label = { Text("Task") }
                )
                TextField(
                    value = updatedDescription,
                    onValueChange = { updatedDescription = it },
                    label = { Text("Description") }
                )

                // Display existing subtasks with checkboxes
                subtasks.forEachIndexed { index, (subtask, isCompleted) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isCompleted,
                            onCheckedChange = {
                                subtasks[index] = Pair(subtask, it) // Update completion status
                            }
                        )
                        Text(text = subtask)
                        IconButton(onClick = { subtasks.removeAt(index) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove subtask")
                        }
                    }
                }

                // New subtask input
                TextField(
                    value = newSubtaskText,
                    onValueChange = { newSubtaskText = it },
                    label = { Text("New Subtask") }
                )
                Button(
                    onClick = {
                        if (newSubtaskText.isNotBlank()) {
                            subtasks.add(Pair(newSubtaskText, false)) // Add new subtask
                            newSubtaskText = "" // Clear input
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Subtask")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onUpdate(updatedText, updatedDescription, subtasks.toList()) // Pass updated data
                    onDismiss() // Dismiss after update
                },
                modifier = Modifier.fillMaxWidth() // Make it full width like the "Add Subtask" button
            ) {
                Text("Update") // Make the text same as "Add Subtask"
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween // Space between the buttons
            ) {
                // Delete Task Button in bottom left
                Button(
                    onClick = {
                        onDelete() // Call delete task function
                        onDismiss() // Dismiss dialog after delete
                    },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Task", color = MaterialTheme.colorScheme.onError)
                }

                // Cancel button on the right side
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        modifier = Modifier.fillMaxWidth()
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
