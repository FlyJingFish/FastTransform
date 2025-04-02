package io.github.flyjingfish.fast_transform.tasks

import io.github.flyjingfish.fast_transform.beans.EntryCache
import io.github.flyjingfish.fast_transform.toTransformAll
import io.github.flyjingfish.fast_transform.utils.getNewJarName
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
    /**
     * 当你调用 [toTransformAll] 传入的第二个参数
     */
    @get:Input
    abstract var isFastDex :Boolean

    @get:InputFiles
    abstract val hideAllJars: ListProperty<RegularFile>

    @get:InputFiles
    abstract val hideAllDirectories: ListProperty<Directory>

    @get:OutputDirectory
    abstract val hideOutputDir: RegularFileProperty

    @get:OutputFile
    abstract val hideOutputFile: RegularFileProperty

    private val allJarFiles = mutableListOf<File>()

    private val allDirectoryFiles = mutableListOf<File>()

    private var is1ClassesJar = false

    private var jarOutput: JarOutputStream ?= null

    /**
     * 是否是只有一个jar包
     */
    fun singleClassesJar():Boolean{
        return is1ClassesJar
    }

    /**
     * 获取到所有输入的的jar包文件
     */
    fun allJars():List<File>{
        return allJarFiles
    }

    /**
     * 获取到所有的输入的class文件的文件夹
     */
    fun allDirectories():List<File>{
        return allDirectoryFiles
    }

    private fun readyAll(){
        val allJarFilePaths = mutableSetOf<String>()
        val allDirectoryFilePaths = mutableSetOf<String>()
        hideAllDirectories.get().forEach { directory ->
            if (directory.asFile.isDirectory){
                val jars = directory.asFile.walk().filter { it.name.endsWith(".jar") }.map { it.absolutePath }.toList()
                if (jars.isNotEmpty()){
                    allJarFilePaths.addAll(jars)
                }else{
                    allDirectoryFilePaths.add(directory.asFile.absolutePath)
                }
            }else if (directory.asFile.absolutePath.endsWith(".jar")){
                allJarFilePaths.add(directory.asFile.absolutePath)
            }else{
                allDirectoryFilePaths.add(directory.asFile.absolutePath)
            }
        }

        hideAllJars.get().forEach { jarFile ->
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
            jarOutput = JarOutputStream(BufferedOutputStream(FileOutputStream(hideOutputFile.get().asFile)))
        }

        jarEntryCaches.clear()
    }

    /**
     * 不要重写此方法
     */
    @TaskAction
    fun hideTaskAction() {
        readyAll()
        startTask()
        writeJar()
        endTask()
        jarOutput?.close()
    }

    /**
     * 此处相当于原来的 @TaskAction 的方法，是任务开始的地方，请不要重新 hideTaskAction()
     */
    abstract fun startTask()

    /**
     * 可以在此做一些善后工作
     */
    abstract fun endTask()

    private fun writeJar() = runBlocking{
        if (isFastDex){
            val fastDexJobs = mutableListOf<Deferred<Unit>>()
            val jarOutputs = mutableListOf<JarOutputStream>()
            jarEntryCaches.forEach { (jarFileName, caches) ->
                val jarFile = File(hideOutputDir.get().asFile.absolutePath, "$jarFileName.jar")
                val existingEntries = readExistingJarEntries(jarFile)
                val jarChanged = caches.any { it.isChange(existingEntries) }
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

    /**
     * 写入到jar的操作，File是原始 jar 包或原始class的文件夹，也可以自定义，一个File对应一个输出的jar包
     *
     * 如果 [isFastDex] = true 此方法只是暂存，最后统一写入jar，[isFastDex] = false 则直接写入jar
     *
     */
    fun File.saveJarEntry(jarEntryName: String,inputStream: InputStream){
        if (isFastDex){
            saveJarEntry(jarEntryName, inputStream.readAllBytes())
        }else{
            jarOutput?.saveEntry(jarEntryName,inputStream)
        }
    }

    /**
     * 写入到jar的操作，File是原始 jar 包或原始class的文件夹，也可以自定义，一个File对应一个输出的jar包
     *
     * 如果 [isFastDex] = true 此方法只是暂存，最后统一写入jar，[isFastDex] = false 则直接写入jar
     */
    fun File.saveJarEntry(jarEntryName: String,byteArray: ByteArray){
        if (isFastDex){
            val jarName = getNewJarName(is1ClassesJar, jarEntryName)

            val entryCaches = jarEntryCaches.computeIfAbsent(jarName) { mutableListOf() }
            synchronized(entryCaches){
                entryCaches.add(EntryCache(jarEntryName,byteArray))
            }
        }else{
            jarOutput?.saveEntry(jarEntryName,byteArray)
        }
    }
}