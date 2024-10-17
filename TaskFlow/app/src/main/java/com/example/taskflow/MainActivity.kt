package com.example.taskflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
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
    val currentUser = auth.currentUser
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Mutable list to hold user inputs with their Firebase document IDs, descriptions, and categories
    val tasks = remember { mutableStateListOf<Triple<String, String, String>>() }
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
                        tasks.add(Triple(taskText, taskDescription, document.id))
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
                onSave = { taskText, description, category ->
                    saveTaskToFirebase(db, auth, taskText, description, category, tasks, coroutineScope, snackbarHostState)
                    isTaskFormVisible = false
                },
                snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope // Pass coroutineScope here
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
        TaskEditDialog(
            tasks = tasks,
            index = selectedTaskIndex!!,
            onDismiss = { isDialogOpen = false },
            onUpdate = { editedText, editedDescription ->
                updateTaskInFirebase(
                    db,
                    tasks[selectedTaskIndex!!].third,
                    editedText,
                    editedDescription,
                    tasks,
                    selectedTaskIndex!!,
                    coroutineScope,
                    snackbarHostState
                )
                isDialogOpen = false
            },
            onDelete = {
                deleteTaskFromFirebase(
                    db,
                    tasks[selectedTaskIndex!!].third,
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
    onSave: (String, String, String) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    var text by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    val categories = listOf("Work", "Personal", "Fitness", "Study", "Other")

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

        CategoryDropdown(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (text.isNotEmpty() && selectedCategory.isNotEmpty()) {
                    onSave(text, description, selectedCategory)
                    text = "" // Clear task input
                    description = "" // Clear description input
                    selectedCategory = "" // Clear category selection
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Please enter a task and select a category.")
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
    task: Triple<String, String, String>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .height(100.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Text(
            text = task.first,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun TaskEditDialog(
    tasks: List<Triple<String, String, String>>,
    index: Int,
    onDismiss: () -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var editedText by remember { mutableStateOf(tasks[index].first) }
    var editedDescription by remember { mutableStateOf(tasks[index].second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onUpdate(editedText, editedDescription) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        },
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
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Task")
                }
            }
        }
    )
}

@Composable
fun CategoryDropdown(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = selectedCategory,
            onValueChange = {},
            label = { Text("Select Category") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            readOnly = true
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Helper Functions for Firebase Operations

fun saveTaskToFirebase(
    db: FirebaseFirestore,
    auth: FirebaseAuth,
    taskText: String,
    description: String,
    category: String,
    tasks: SnapshotStateList<Triple<String, String, String>>,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val userEmail = auth.currentUser?.email ?: return

    val taskData = hashMapOf(
        "taskText" to taskText,
        "description" to description,
        "category" to category,
        "userEmail" to userEmail
    )

    db.collection("userInputs")
        .add(taskData)
        .addOnSuccessListener { documentReference ->
            tasks.add(Triple(taskText, description, documentReference.id))
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Task added successfully!")
            }
        }
        .addOnFailureListener { e ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error adding task: ${e.message}")
            }
        }
}

fun updateTaskInFirebase(
    db: FirebaseFirestore,
    taskId: String,
    editedText: String,
    editedDescription: String,
    tasks: SnapshotStateList<Triple<String, String, String>>,
    index: Int,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    // Create a map with the updated task data
    val taskData = hashMapOf(
        "taskText" to editedText,
        "description" to editedDescription
    ) as Map<String, Any> // Explicitly cast to Map<String, Any>

    db.collection("userInputs").document(taskId)
        .update(taskData)
        .addOnSuccessListener {
            tasks[index] = Triple(editedText, editedDescription, taskId) // Update local task list
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Task updated successfully!")
            }
        }
        .addOnFailureListener { e ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error updating task: ${e.message}")
            }
        }
}


fun deleteTaskFromFirebase(
    db: FirebaseFirestore,
    taskId: String,
    tasks: SnapshotStateList<Triple<String, String, String>>,
    index: Int,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    db.collection("userInputs").document(taskId)
        .delete()
        .addOnSuccessListener {
            tasks.removeAt(index) // Remove task from local list
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Task deleted successfully!")
            }
        }
        .addOnFailureListener { e ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error deleting task: ${e.message}")
            }
        }
}

@Composable
fun SignOutButton(
    auth: FirebaseAuth,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    onSignOut: () -> Unit
) {
    Button(onClick = {
        auth.signOut()
        onSignOut()
    }) {
        Text("Sign Out")
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TaskFlowTheme {
        SaveToFirebaseScreen()
    }
}
