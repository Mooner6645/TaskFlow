package com.example.taskflow

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.material3.SnackbarHostState

data class Task(
    val text: String,
    val description: String,
    val id: String,
    val subtasks: List<Pair<String, Boolean>> = emptyList() // Default to an empty list
)

class TaskManagement(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {

    fun saveTask(
        taskText: String,
        description: String,
        category: String,
        subtasks: List<Pair<String, Boolean>>, // List of subtasks
        tasks: SnapshotStateList<Task>, // Main tasks
        coroutineScope: CoroutineScope,
        snackbarHostState: SnackbarHostState
    ) {
        val userEmail = auth.currentUser?.email ?: return

        // Convert subtasks to a Firestore-compatible format
        val subtasksToSave = subtasks.map {
            mapOf(
                "name" to it.first,
                "isCompleted" to it.second
            )
        }

        val newTask = hashMapOf(
            "taskText" to taskText,
            "description" to description,
            "category" to category,
            "subtasks" to subtasksToSave, // Save subtasks
            "userEmail" to userEmail
        )

        db.collection("userInputs")
            .add(newTask)
            .addOnSuccessListener { documentReference ->
                tasks.add(Task(taskText, description, documentReference.id, subtasks))
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


    fun updateTask(
        documentId: String,
        updatedText: String,
        updatedDescription: String,
        updatedSubtasks: List<Pair<String, Boolean>>, // Updated subtasks
        tasks: SnapshotStateList<Task>,
        index: Int,
        coroutineScope: CoroutineScope,
        snackbarHostState: SnackbarHostState
    ) {
        // Convert updated subtasks to Firestore-compatible format
        val subtasksToUpdate = updatedSubtasks.map {
            mapOf(
                "name" to it.first,
                "isCompleted" to it.second
            )
        }

        val updatedTask: MutableMap<String, Any> = hashMapOf(
            "taskText" to updatedText,
            "description" to updatedDescription,
            "subtasks" to subtasksToUpdate // Include updated subtasks
        )

        db.collection("userInputs").document(documentId)
            .update(updatedTask)
            .addOnSuccessListener {
                tasks[index] = Task(updatedText, updatedDescription, documentId, updatedSubtasks) // Update with new subtasks
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

    fun deleteTask(
        documentId: String,
        tasks: SnapshotStateList<Task>,
        index: Int,
        coroutineScope: CoroutineScope,
        snackbarHostState: SnackbarHostState
    ) {
        db.collection("userInputs").document(documentId)
            .delete()
            .addOnSuccessListener {
                tasks.removeAt(index)
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
}
