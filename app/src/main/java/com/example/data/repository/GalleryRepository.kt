package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.GeneratedCreation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class GalleryRepository(private val appContext: Context) {

    private fun userDir(userId: String): File {
        val safeUid = userId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val dir = File(appContext.filesDir, "lumore_gallery/$safeUid")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun indexFile(userId: String): File = File(userDir(userId), "creations.json")

    suspend fun loadCreations(userId: String): List<GeneratedCreation> = withContext(Dispatchers.IO) {
        val file = indexFile(userId)
        if (!file.exists()) return@withContext emptyList()
        try {
            val raw = file.readText()
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val path = obj.optString("filePath")
                    if (path.isNotBlank() && File(path).exists()) {
                        add(
                            GeneratedCreation(
                                id = obj.optString("id"),
                                prompt = obj.optString("prompt"),
                                styleLabel = obj.optString("styleLabel"),
                                aspectLabel = obj.optString("aspectLabel"),
                                filePath = path,
                                createdAtMillis = obj.optLong("createdAtMillis"),
                                isRemix = obj.optBoolean("isRemix", false)
                            )
                        )
                    }
                }
            }.sortedByDescending { it.createdAtMillis }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveCreation(
        userId: String,
        bitmap: Bitmap,
        prompt: String,
        styleLabel: String,
        aspectLabel: String,
        isRemix: Boolean
    ): GeneratedCreation = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val dir = userDir(userId)
        val imageFile = File(dir, "lumore_$id.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val creation = GeneratedCreation(
            id = id,
            prompt = prompt,
            styleLabel = styleLabel,
            aspectLabel = aspectLabel,
            filePath = imageFile.absolutePath,
            createdAtMillis = System.currentTimeMillis(),
            isRemix = isRemix
        )
        val current = loadCreations(userId).toMutableList()
        current.add(0, creation)
        writeIndex(userId, current.take(40))
        creation
    }

    suspend fun deleteCreation(userId: String, creationId: String) = withContext(Dispatchers.IO) {
        val current = loadCreations(userId)
        val target = current.firstOrNull { it.id == creationId }
        if (target != null) {
            File(target.filePath).delete()
        }
        writeIndex(userId, current.filterNot { it.id == creationId })
    }

    suspend fun loadBitmap(filePath: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    suspend fun saveToDevicePhotos(bitmap: Bitmap, title: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val filename = "Lumore_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/LumoreAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(IllegalStateException("Could not create MediaStore entry."))

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return@withContext Result.failure(IllegalStateException("Could not open output stream."))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success("Saved to Pictures/LumoreAI")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createShareUri(bitmap: Bitmap): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(appContext.cacheDir, "shared_images")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, "lumore_share_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeIndex(userId: String, items: List<GeneratedCreation>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("prompt", item.prompt)
                    put("styleLabel", item.styleLabel)
                    put("aspectLabel", item.aspectLabel)
                    put("filePath", item.filePath)
                    put("createdAtMillis", item.createdAtMillis)
                    put("isRemix", item.isRemix)
                }
            )
        }
        indexFile(userId).writeText(array.toString())
    }
}
