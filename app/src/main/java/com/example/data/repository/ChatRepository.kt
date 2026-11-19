package com.example.data.repository

import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.MessageRole
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireSignedInUser(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing chats.")
        check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user access." }
        return uid
    }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireSignedInUser(userId)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val cleanName = displayName.trim().ifEmpty { "Lumore User" }.take(80)
            val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(120)

            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", cleanName)
                    if (cleanEmail != null) put("email", cleanEmail)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updatePayload = buildMap<String, Any> {
                    put("displayName", cleanName)
                    if (cleanEmail != null) put("email", cleanEmail)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeThreads(userId: String): Flow<List<ChatThread>> =
        db.collection("users").document(userId).collection("chatThreads")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    fun observeThread(userId: String, threadId: String): Flow<ChatThread?> =
        db.collection("users").document(userId)
            .collection("chatThreads").document(threadId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }

    fun observeMessages(userId: String, threadId: String): Flow<List<ChatMessage>> =
        db.collection("users").document(userId)
            .collection("chatThreads").document(threadId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }

    suspend fun saveTurn(
        userId: String,
        existingThreadId: String?,
        existingMessageCount: Long,
        threadTitle: String,
        userText: String,
        userHasImage: Boolean,
        assistantText: String
    ): Result<String> {
        var targetPath = "users/$userId/chatThreads"
        return try {
            val uid = requireSignedInUser(userId)
            val threadsCollection = db.collection("users").document(uid).collection("chatThreads")
            val isNewThread = existingThreadId.isNullOrBlank()
            val threadRef = if (isNewThread) threadsCollection.document() else threadsCollection.document(existingThreadId!!)
            val threadId = threadRef.id
            targetPath = threadRef.path

            val cleanTitle = threadTitle.trim().ifEmpty { "Lumore Session" }.take(120)
            val cleanUserText = userText.trim().ifEmpty { "Shared an image" }.take(16000)
            val cleanAssistantText = assistantText.trim().ifEmpty { "Done." }.take(16000)
            val cleanPreview = cleanAssistantText.replace('\n', ' ').take(240)

            val userMsgRef = threadRef.collection("messages").document()
            val assistantMsgRef = threadRef.collection("messages").document()

            val batch = db.batch()
            if (isNewThread) {
                batch.set(
                    threadRef,
                    mapOf(
                        "userId" to uid,
                        "title" to cleanTitle,
                        "lastMessagePreview" to cleanPreview,
                        "bookmarked" to false,
                        "messageCount" to 2L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.update(
                    threadRef,
                    mapOf(
                        "title" to cleanTitle,
                        "lastMessagePreview" to cleanPreview,
                        "messageCount" to (existingMessageCount + 2L).coerceAtMost(5000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.set(
                userMsgRef,
                mapOf(
                    "userId" to uid,
                    "threadId" to threadId,
                    "role" to MessageRole.USER.name,
                    "text" to cleanUserText,
                    "hasImage" to userHasImage,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                assistantMsgRef,
                mapOf(
                    "userId" to uid,
                    "threadId" to threadId,
                    "role" to MessageRole.ASSISTANT.name,
                    "text" to cleanAssistantText,
                    "hasImage" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            batch.commit().await()
            Result.success(threadId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, targetPath)
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(
        userId: String,
        threadId: String,
        bookmarked: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/chatThreads/$threadId"
        return try {
            val uid = requireSignedInUser(userId)
            require(threadId.isNotBlank()) { "Thread ID cannot be blank." }
            db.collection("users").document(uid)
                .collection("chatThreads").document(threadId)
                .update(
                    mapOf(
                        "bookmarked" to bookmarked,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun renameThread(
        userId: String,
        threadId: String,
        newTitle: String
    ): Result<Unit> {
        val docPath = "users/$userId/chatThreads/$threadId"
        return try {
            val uid = requireSignedInUser(userId)
            require(threadId.isNotBlank()) { "Thread ID cannot be blank." }
            val cleanTitle = newTitle.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Thread title cannot be blank." }
            db.collection("users").document(uid)
                .collection("chatThreads").document(threadId)
                .update(
                    mapOf(
                        "title" to cleanTitle,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteThread(userId: String, threadId: String): Result<Unit> {
        val docPath = "users/$userId/chatThreads/$threadId"
        return try {
            val uid = requireSignedInUser(userId)
            require(threadId.isNotBlank()) { "Thread ID cannot be blank." }
            val threadRef = db.collection("users").document(uid)
                .collection("chatThreads").document(threadId)
            val messagesSnapshot = threadRef.collection("messages").get().await()
            val batch = db.batch()
            messagesSnapshot.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.delete(threadRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
