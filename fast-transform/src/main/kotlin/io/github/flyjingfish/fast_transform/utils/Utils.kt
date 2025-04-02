package io.github.flyjingfish.fast_transform.utils

import io.github.flyjingfish.fast_transform.beans.EntryCache
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

fun String.computeMD5(): String {
    return try {
        val messageDigest = MessageDigest.getInstance("MD5")
        val digestBytes = messageDigest.digest(toByteArray())
        bytesToHex(digestBytes)
    } catch (var3: NoSuchAlgorithmException) {
        throw IllegalStateException(var3)
    }
}

fun bytesToHex(bytes: ByteArray): String {
    val hexString = StringBuilder()
    for (b in bytes) {
        val hex = Integer.toHexString(0xff and b.toInt())
        if (hex.length == 1) {
            hexString.append('0')
        }
        hexString.append(hex)
    }
    return hexString.toString()
}

internal fun EntryCache.isChange(existingEntries: Map<String, ByteArray?>):Boolean {
    val safeEntryName = jarEntryName.removePrefix("/")
    val newData = byteArray
    // 如果 JAR 中已存在相同条目，并且内容相同，则跳过写入
    if (existingEntries[safeEntryName]?.contentEquals(newData) == true) {
        return false
    }
    return true
}

fun JarOutputStream.saveEntry(entryName: String, inputStream: InputStream) {
    synchronized(this){
        putNextEntry(JarEntry(entryName))
        inputStream.copyTo( this)
        closeEntry()
    }

}

fun JarOutputStream.saveEntry(entryName: String, data: ByteArray) {
    synchronized(this){
        putNextEntry(JarEntry(entryName))
        write(data)
        closeEntry()
    }
}

fun File.getNewJarName(is1ClassesJar :Boolean,jarEntryName: String):String{
    val jarFileName = absolutePath.computeMD5()
    val parts = jarEntryName.split("/")
    return if (is1ClassesJar){
        when {
            parts.size >= 3 -> parts.subList(0, 2).joinToString("/").computeMD5()
            parts.size > 1 -> parts.dropLast(1).joinToString("/").computeMD5()
            else -> jarFileName
        }
    }else{
        jarFileName
    }

}