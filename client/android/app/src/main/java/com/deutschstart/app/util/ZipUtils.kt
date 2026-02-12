package com.deutschstart.app.util

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ZipUtils {
    fun unzip(zipFile: File, targetDirectory: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipInputStream ->
            var zipEntry: ZipEntry? = zipInputStream.nextEntry
            while (zipEntry != null) {
                val file = File(targetDirectory, zipEntry.name)
                // Security: prevent Zip Path Traversal
                if (!file.canonicalPath.startsWith(
                        targetDirectory.canonicalPath + File.separator
                    )
                ) {
                    throw SecurityException("Zip Path Traversal Attempt: ${zipEntry.name}")
                }

                if (zipEntry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var count: Int
                        while (zipInputStream.read(buffer).also { count = it } != -1) {
                            outputStream.write(buffer, 0, count)
                        }
                    }
                }
                zipInputStream.closeEntry()
                zipEntry = zipInputStream.nextEntry
            }
        }
    }
}
