package com.grandetrend.app.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File

object BackupFiles {
    private fun previous(context: Context) = AtomicFile(File(context.filesDir, "backup-before-import.json"))
    fun hasPrevious(context: Context): Boolean = previous(context).baseFile.exists()

    fun read(context: Context, uri: Uri): GradeBackup =
        requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取所选文件" }.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size() + count <= BackupCodec.MAX_BYTES) { "文件超过 10 MB，暂不支持" }
                output.write(buffer, 0, count)
            }
            BackupCodec.decode(output.toByteArray())
        }

    fun export(context: Context, uri: Uri, repository: GradeRepository, previousImport: Boolean = false) {
        val bytes = if (previousImport) previous(context).openRead().use { it.readBytes() }
            else BackupCodec.encode(repository.snapshot())
        // Build and validate the entire backup before opening the destination for writing.
        BackupCodec.decode(bytes)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入所选位置" }.use { it.write(bytes); it.flush() }
    }

    fun restore(context: Context, repository: GradeRepository, backup: GradeBackup,
               overwriteExisting: Boolean, restorePreferences: Boolean): ImportResult =
        repository.importBackup(backup, overwriteExisting, restorePreferences) { before ->
            // Abort import if the recovery copy cannot be saved. AtomicFile also survives interruptions.
            val bytes = BackupCodec.encode(before)
            val file = previous(context)
            val stream = file.startWrite()
            try { stream.write(bytes); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
            check(file.openRead().use { it.readBytes().contentEquals(bytes) }) { "导入前备份未完整保存，已中止导入" }
        }
}
