package io.github.flyjingfish.fast_transform.tasks

import io.github.flyjingfish.fast_transform.beans.EntryCache
import io.github.flyjingfish.fast_transform.utils.computeMD5
import io.github.flyjingfish.fast_transform.utils.isChange
import io.github.flyjingfish.fast_transform.utils.saveEntry
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

abstract class DefaultTransformTask: DefaultTask() {
    @get:Input
    abstract var isFastDex :Boolean

    @get:InputFiles
    abstract val allJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val allDirectories: ListProperty<Directory>

    @get:OutputDirectory
    abstract val outputDir: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val allJarFiles = mutableListOf<File>()

    private val allDirectoryFiles = mutableListOf<File>()

    private var is1ClassesJar = false

    private var jarOutput: JarOutputStream ?= null

    fun singleClassesJar():Boolean{
        return is1ClassesJar
    }

    fun allJars():List<File>{
        return allJarFiles
    }

    fun allDirectories():List<File>{
        return allDirectoryFiles
    }

    private fun readyAll(){
        val allJarFilePaths = mutableSetOf<String>()
        val allDirectoryFilePaths = mutableSetOf<String>()
        allDirectories.get().forEach { directory ->
            if (directory.asFile.isDirectory){
                val jars = directory.asFile.walk().filter { it.name.endsWith(".jar") }.map { it.absolutePath }.toList()
                if (jars.isNotEmpty()){
                    allJarFilePaths.addAll(jars)
                }else{
                    allDirectoryFilePaths.add(directory.asFile.absolutePath)
                }
            }
        }

        allJars.get().forEach { jarFile ->
            if (jarFile.asFile.isFile){
                allJarFilePaths.add(jarFile.asFile.absolutePath)
            }else{
                val isClassDirectory = jarFile.asFile.walk().filter { it.name.endsWith(".class") }.toList().isNotEmpty()

                if (isClassDirectory){
                    allDirectoryFilePaths.add(jarFile.asFile.absolutePath)
                }else{
                    val jars = jarFile.asFile.walk().filter { it.name.endsWith(".jar") }.map { it.absolutePath }.toList()
                    if (jars.isNotEmpty()){
                        allJarFilePaths.addAll(jars)
                    }
                }
            }
        }
        allDirectoryFiles.addAll(allDirectoryFilePaths.map(::File))
        allJarFiles.addAll(allJarFilePaths.map(::File))

        is1ClassesJar = allDirectoryFiles.isEmpty() && allJarFiles.size == 1

        if (!isFastDex){
            jarOutput = JarOutputStream(BufferedOutputStream(FileOutputStream(outputFile.get().asFile)))
        }
        jarOutput?.close()

        jarEntryCaches.clear()
    }

    @TaskAction
    fun taskAction() {
        readyAll()
        scan()
        writeJar()
        endTask()
    }

    abstract fun scan()
    abstract fun endTask()

    private fun writeJar() = runBlocking{
        if (isFastDex){
            val fastDexJobs = mutableListOf<Deferred<Unit>>()
            val jarOutputs = mutableListOf<JarOutputStream>()
            jarEntryCaches.forEach { (jarFileName, caches) ->
                val jarFile = File(outputDir.get().asFile.absolutePath, "$jarFileName.jar")
                val existingEntries = readExistingJarEntries(jarFile)
                var jarChanged = false
                for (cache in caches) {
                    if (cache.isChange(existingEntries)){
                        jarChanged = true
                        break
                    }
                }
                if (jarChanged){
                    val jarOutput = JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile)))
                    jarOutputs.add(jarOutput)
                    for (cache in caches) {
                        val job = async(Dispatchers.IO) {
                            try {
                                jarOutput.saveEntry(cache.jarEntryName,cache.byteArray)
                            } catch (e: Exception) {
                                logger.error("Merge jar error3 entry:[${cache.jarEntryName}], error message:$e,通常情况下你需要先重启Android Studio,然后clean一下项目即可，如果还有问题请到Github联系作者")
                            }
                        }
                        fastDexJobs.add(job)
                    }
                }

            }
            fastDexJobs.awaitAll()
            for (jarOutput1 in jarOutputs) {
                withContext(Dispatchers.IO) {
                    jarOutput1.close()
                }
            }
        }
    }


    private fun readExistingJarEntries(jarFile: File): Map<String, ByteArray?> {
        if (!jarFile.exists()) return emptyMap()

        val entries = mutableMapOf<String, ByteArray>()
        JarFile(jarFile).use { jar ->
            jar.entries().asSequence().forEach { entry ->
                if (!entry.isDirectory) {
                    jar.getInputStream(entry).use { inputStream ->
                        entries[entry.name] = inputStream.readBytes()
                    }
                }
            }
        }
        return entries
    }
    private val jarEntryCaches = ConcurrentHashMap<String,MutableList<EntryCache>>()
    fun File.saveJarEntry(jarEntryName: String,inputStream: InputStream){
        if (isFastDex){
            saveJarEntry(jarEntryName, inputStream.readAllBytes())
        }else{
            jarOutput?.saveEntry(jarEntryName,inputStream)
        }
    }

    fun File.saveJarEntry(jarEntryName: String,byteArray: ByteArray){
        if (isFastDex){
            val jarFileName = absolutePath.computeMD5()

            val parts = jarEntryName.split("/")
            val jarName = if (is1ClassesJar){
                when {
                    parts.size >= 4 -> parts.subList(0, 3).joinToString("/").computeMD5()
                    parts.size > 1 -> parts.dropLast(1).joinToString("/").computeMD5()
                    else -> jarFileName
                }
            }else{
                jarFileName
            }

            val entryCaches = jarEntryCaches.computeIfAbsent(jarName) { mutableListOf() }
            synchronized(entryCaches){
                entryCaches.add(EntryCache(jarEntryName,byteArray))
            }
        }else{
            jarOutput?.saveEntry(jarEntryName,byteArray)
        }
    }
}