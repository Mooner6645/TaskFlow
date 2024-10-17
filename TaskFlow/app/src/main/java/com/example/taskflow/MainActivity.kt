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

    // Predefined categories
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

        // Use the updated CategoryDropdown
        CategoryDropdown(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it } // Update selected category
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
fun CategoryDropdown(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = selectedCategory,
            onValueChange = {}, // No-op to prevent typing
            label = { Text("Select Category") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }, // Open dropdown on click
            readOnly = true // Make it read-only to indicate it's a dropdown
        )

        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false } // Close dropdown when clicked outside
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onCategorySelected(category) // Set the selected category
                        expanded = false // Close the dropdown after selection
                    }
                )
            }
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
                Button(onClick = onDelete) {
                    Text("Delete Task")
                }
            }
        }
    )
}

// Firebase interaction functions
private fun saveTaskToFirebase(
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
    val newTask = hashMapOf(
        "taskText" to taskText,
        "description" to description,
        "category" to category,
        "userEmail" to userEmail
    )

    db.collection("userInputs")
        .add(newTask)
        .addOnSuccessListener { documentReference ->
            tasks.add(Triple(taskText, description, documentReference.id))
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Task added successfully.")
            }
        }
        .addOnFailureListener { e ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error adding task: ${e.message}")
            }
        }
}

private fun updateTaskInFirebase(
    db: FirebaseFirestore,
    documentId: String,
    updatedText: String,
    updatedDescription: String,
    tasks: SnapshotStateList<Triple<String, String, String>>,
    index: Int,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    // Create a mutable map with type MutableMap<String, Any>
    val updatedTask: MutableMap<String, Any> = hashMapOf(
        "taskText" to updatedText,
        "description" to updatedDescription
    )

    db.collection("userInputs").document(documentId)
        .update(updatedTask)
        .addOnSuccessListener {
            tasks[index] = Triple(updatedText, updatedDescription, documentId) // Update the task in the list
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Task updated successfully.")
            }
        }
        .addOnFailureListener { e ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Error updating task: ${e.message}")
            }
        }
}


private fun deleteTaskFromFirebase(
    db: FirebaseFirestore,
    documentId: String,
    tasks: SnapshotStateList<Triple<String, String, String>>,
    index: Int,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    db.collection("userInputs").document(documentId)
        .delete()
        .addOnSuccessListener {
            tasks.removeAt(index) // Remove the task from the list
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Task deleted successfully.")
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
    Button(
        onClick = {
            auth.signOut()
            onSignOut()
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Sign Out")
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTaskForm() {
    val snackbarHostState = SnackbarHostState()
    val coroutineScope = rememberCoroutineScope()

    TaskForm(
        onSave = { _, _, _ -> },
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope
    )
}
