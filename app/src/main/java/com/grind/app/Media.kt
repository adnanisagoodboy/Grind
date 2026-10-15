package com.grind.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.UUID

/** Helpers for user-picked audio / image / video. */
object Media {
    private val VIDEO = setOf("mp4", "webm", "mkv", "3gp", "mov", "m4v")

    fun isVideo(p: String) = p.substringAfterLast('.').lowercase() in VIDEO
    fun exists(ref: String) = ref.startsWith("asset:") || File(ref).exists()
    fun name(ref: String) =
        if (ref.startsWith("asset:")) ref.removePrefix("asset:") else File(ref).name.substringAfter('_')

    fun import(c: Context, uri: Uri): String? = try {
        val cr = c.contentResolver
        var n = "file"
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) n = it.getString(0) ?: n
        }
        if (!n.contains('.')) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(cr.getType(uri))?.let { n = "$n.$it" }
        }
        val dir = File(c.filesDir, "media").apply { mkdirs() }
        val out = File(dir, "${UUID.randomUUID().toString().take(6)}_${n.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        cr.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } }
        out.absolutePath
    } catch (e: Exception) { null }

    fun delete(ref: String) { if (!ref.startsWith("asset:")) runCatching { File(ref).delete() } }

    fun bitmap(path: String, max: Int): Bitmap? = try {
        if (isVideo(path)) {
            val r = MediaMetadataRetriever()
            r.setDataSource(path)
            val b = r.getFrameAtTime(0)
            r.release()
            b
        } else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (o.outWidth / s > max * 2) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
        }
    } catch (e: Exception) { null }
}
