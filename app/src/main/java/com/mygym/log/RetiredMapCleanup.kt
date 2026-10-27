package com.mygym.log

import android.content.Context
import java.io.File

/** Remove only retired map resources; activity databases and backups are untouched. */
object RetiredMapCleanup {
    fun run(context: Context): Boolean = try {
        val root=context.filesDir.canonicalFile
        val maps=File(root,"maps")
        // Refuse unexpected links rather than following a target outside app storage.
        if(maps.canonicalFile!=maps.absoluteFile) false
        else {
            val removed=!maps.exists() || maps.deleteRecursively()
            if(removed) context.getSharedPreferences("offline-maps",Context.MODE_PRIVATE).edit().clear().apply()
            removed
        }
    } catch(_: Exception) { false } // Retry next launch; cleanup must never prevent tracking.
}
