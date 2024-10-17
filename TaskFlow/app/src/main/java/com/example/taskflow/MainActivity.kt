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
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Mutable list to hold user inputs with their Firebase document IDs and descriptions
    val tasks = remember { mutableStateListOf<Triple<String, String, String>>() } // Triple(taskText, description, documentId)
    var selectedTaskIndex by remember { mutableStateOf<Int?>(null) } // Holds the index of the task being edited
    var isDialogOpen by remember { mutableStateOf(false) } // To control the visibility of the edit dialog
    var editedText by remember { mutableStateOf("") } // To hold the edited task text
    var editedDescription by remember { mutableStateOf("") } // To hold the edited task description

    // Fetch tasks associated with the user's email from Firebase when the composable is first launched
    LaunchedEffect(currentUser) {
        currentUser?.email?.let { email ->
            db.collection("userInputs")
                .whereEqualTo("userEmail", email)
                .get()
                .addOnSuccessListener { documents ->
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
                    // Get the current user's email
                    val userEmail = auth.currentUser?.email
                    if (userEmail != null) {
                        // Save task and description to Firebase Firestore with the user's email
                        val userInput = hashMapOf(
                            "taskText" to text,
                            "description" to description,
                            "userEmail" to userEmail // Add userEmail field
                        )
                        db.collection("userInputs")
                            .add(userInput)
                            .addOnSuccessListener { documentReference ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Saved successfully!")
                                    tasks.add(Triple(text, description, documentReference.id))
                                    text = "" // Clear task input
                                    description = "" // Clear description input
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
            Text("Save to Firebase")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                auth.signOut()
                val intent = Intent(context, LoginActivity::class.java)
                context.startActivity(intent)
                // Optionally finish the MainActivity to prevent users from going back with the back button
                (context as? ComponentActivity)?.finish()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Out")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display tasks in a horizontal LazyRow
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
                        // Open dialog to show task details
                        selectedTaskIndex = taskIndex
                        editedText = task.first // Get the task text
                        editedDescription = task.second // Get the description
                        isDialogOpen = true
                    }
                ) {
                    Text(
                        text = task.first, // Display task text
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                        tasks[index] = Triple(editedText, editedDescription, documentId) // Update task in the list

                        // Update task and description in Firebase Firestore using the document ID
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

