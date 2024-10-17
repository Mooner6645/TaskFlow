package com.example.taskflow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.taskflow.ui.theme.TaskFlowTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    var text by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isTaskFormVisible by remember { mutableStateOf(false) } // State to track task form visibility
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Mutable list to hold user inputs with their Firebase document IDs and descriptions
    val tasks = remember { mutableStateListOf<Triple<String, String, String>>() }
    var selectedTaskIndex by remember { mutableStateOf<Int?>(null) }
    var isDialogOpen by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf("") }
    var editedDescription by remember { mutableStateOf("") }

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
        // "Add Task" Button
        Button(
            onClick = { isTaskFormVisible = !isTaskFormVisible }, // Toggle the form visibility
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isTaskFormVisible) "Hide Task Form" else "Add Task")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Show task form if "Add Task" button is clicked
        if (isTaskFormVisible) {
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

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (text.isNotEmpty()) {
                            val userEmail = auth.currentUser?.email
                            if (userEmail != null) {
                                val userInput = hashMapOf(
                                    "taskText" to text,
                                    "description" to description,
                                    "userEmail" to userEmail
                                )
                                db.collection("userInputs")
                                    .add(userInput)
                                    .addOnSuccessListener { documentReference ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Saved successfully!")
                                            tasks.add(Triple(text, description, documentReference.id))
                                            text = "" // Clear task input
                                            description = "" // Clear description input
                                            isTaskFormVisible = false // Hide task form after saving
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Failed to save: ${e.message}")
                                        }
                                    }
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("User not authenticated.")
                                }
                            }
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

            Spacer(modifier = Modifier.height(16.dp))
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
                Card(
                    modifier = Modifier
                        .width(200.dp)
                        .height(100.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    onClick = {
                        selectedTaskIndex = taskIndex
                        editedText = task.first
                        editedDescription = task.second
                        isDialogOpen = true
                    }
                ) {
                    Text(
                        text = task.first,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }

    // Edit Task Dialog
    if (isDialogOpen && selectedTaskIndex != null) {
        AlertDialog(
            onDismissRequest = { isDialogOpen = false },
            confirmButton = {
                Button(onClick = {
                    selectedTaskIndex?.let { index ->
                        val (oldTask, oldDescription, documentId) = tasks[index]
                        tasks[index] = Triple(editedText, editedDescription, documentId)

                        db.collection("userInputs")
                            .document(documentId)
                            .update(mapOf(
                                "taskText" to editedText,
                                "description" to editedDescription
                            ))
                            .addOnSuccessListener {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Task updated successfully!")
                                }
                            }
                            .addOnFailureListener { e ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to update: ${e.message}")
                                }
                            }
                    }
                    isDialogOpen = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Button(onClick = { isDialogOpen = false }) {
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
                        onClick = {
                            selectedTaskIndex?.let { index ->
                                val (taskText, taskDescription, documentId) = tasks[index]
                                db.collection("userInputs").document(documentId)
                                    .delete()
                                    .addOnSuccessListener {
                                        coroutineScope.launch {
                                            tasks.removeAt(index) // Remove task from the list
                                            snackbarHostState.showSnackbar("Task deleted successfully!")
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Failed to delete task: ${e.message}")
                                        }
                                    }
                            }
                            isDialogOpen = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Task")
                    }
                }
            }
        )
    }
}


@Preview(showBackground = true)
@Composable
fun SaveToFirebaseScreenPreview() {
    TaskFlowTheme {
        SaveToFirebaseScreen()
    }
}

