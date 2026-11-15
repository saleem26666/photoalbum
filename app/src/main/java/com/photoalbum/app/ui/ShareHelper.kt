package com.photoalbum.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {
    fun shareItems(
        context: Context,
        uris: List<Uri>,
        mimeTypes: List<String?>,
        packageName: String? = null
    ): Boolean {
        if (uris.isEmpty()) return false

        val shareable = uris.map { ensureShareableUri(context, it) }
        val mime = commonMime(mimeTypes)

        val intent = if (shareable.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareable.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareable))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (packageName != null) {
            intent.setPackage(packageName)
            return try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }

        val chooser = Intent.createChooser(intent, "Share via")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
        return true
    }

    fun shareToWhatsApp(context: Context, uris: List<Uri>, mimeTypes: List<String?>): Boolean {
        val ok = shareItems(context, uris, mimeTypes, "com.whatsapp")
        if (ok) return true
        return shareItems(context, uris, mimeTypes, "com.whatsapp.w4b")
    }

    private fun commonMime(types: List<String?>): String {
        val nonNull = types.filterNotNull()
        if (nonNull.isEmpty()) return "*/*"
        if (nonNull.all { it.startsWith("image/") }) return "image/*"
        if (nonNull.all { it.startsWith("video/") }) return "video/*"
        if (nonNull.all { it.startsWith("audio/") }) return "audio/*"
        if (nonNull.distinct().size == 1) return nonNull.first()
        return "*/*"
    }

    private fun ensureShareableUri(context: Context, uri: Uri): Uri {
        if (uri.scheme == "content") return uri
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return uri)
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
        return uri
    }
}
