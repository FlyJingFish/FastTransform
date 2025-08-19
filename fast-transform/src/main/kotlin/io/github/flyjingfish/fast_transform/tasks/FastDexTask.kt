package io.github.flyjingfish.fast_transform.tasks

import com.android.build.gradle.internal.tasks.DexArchiveBuilderTask
import io.github.flyjingfish.fast_transform.beans.EntryCache
import io.github.flyjingfish.fast_transform.utils.RecordFrom
import io.github.flyjingfish.fast_transform.utils.RuntimeProject
import io.github.flyjingfish.fast_transform.utils.adapterOSPath
import io.github.flyjingfish.fast_transform.utils.getNewJarName
import io.github.flyjingfish.fast_transform.utils.isChange
import io.github.flyjingfish.fast_transform.utils.saveEntry
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class FastDexTask(private val dexTask : DexArchiveBuilderTask,private val runtimeProject: RuntimeProject) {


    private val is1ClassesJar = true
    private lateinit var outputDir: File
    private lateinit var singleJar : File
    private fun interceptTask():Boolean{
        val classesList = mutableSetOf<String>()
        for (projectClass in dexTask.projectClasses) {
            classesList.add(projectClass.absolutePath)
        }
        val classFileList = classesList.map(::File)

        if (classFileList.size == 1){
            if (classFileList[0].isFile && classFileList[0].absolutePath.endsWith(".jar")){
                outputDir = File(runtimeProject.buildDir.absolutePath,"intermediates/classes/${dexTask.name}FastDex/All/".adapterOSPath())
                singleJar = classFileList[0]
                jarEntryCaches.clear()
                if (!outputDir.exists()){
                    outputDir.mkdirs()
                }
                return false
            }else if (classFileList[0].isDirectory){
                val outputDir = classFileList[0]
                outputDir.listFiles()?.let { files ->
                    RecordFrom.setFrom(dexTask, files,runtimeProject)
                }
                return true
            }
        }
        return true
    }

    /**
     * 不要重写此方法
     */
    fun taskAction() {
        if (interceptTask()){
            return
        }
        startTask()
        writeJar()

        outputDir.listFiles()?.filter { file -> file.name != singleJar.name}?.let { files ->
            RecordFrom.setFrom(dexTask, files,runtimeProject)
        }
    }

    /**
     * 此处相当于原来的 @TaskAction 的方法，是任务开始的地方，请不要重新 hideTaskAction()
     */
    private fun startTask() = runBlocking{
        val jarFiles = mutableListOf<JarFile>()
        val wovenCodeJobs = mutableListOf<Deferred<Unit>>()
        val jarFile = JarFile(singleJar)
        val enumeration = jarFile.entries()
        while (enumeration.hasMoreElements()) {
            val jarEntry = enumeration.nextElement()
            val entryName = jarEntry.name
            if (jarEntry.isDirectory || entryName.isEmpty() || entryName.startsWith("META-INF/") || "module-info.class" == entryName || !entryName.endsWith(".class")) {
                continue
            }
            val job = async(Dispatchers.IO) {
                jarFile.getInputStream(jarEntry).use {
                    singleJar.saveJarEntry(entryName,it)
                }
            }
            wovenCodeJobs.add(job)
        }
        jarFiles.add(jarFile)
        wovenCodeJobs.awaitAll()
        for (jarFile in jarFiles) {
            withContext(Dispatchers.IO) {
                jarFile.close()
            }
        }
    }


    private fun writeJar() = runBlocking{
        val fastDexJobs = mutableListOf<Deferred<Unit>>()
        val jarOutputs = mutableListOf<JarOutputStream>()
        jarEntryCaches.forEach { (jarFileName, caches) ->
            val jarFile = File(outputDir.absolutePath, "$jarFileName.jar")
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
                            println("Merge jar error3 entry:[${cache.jarEntryName}], error message:$e,通常情况下你需要先重启Android Studio,然后clean一下项目即可，如果还有问题请到Github联系作者")
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
        saveJarEntry(jarEntryName, inputStream.readAllBytes())
    }

    /**
     * 写入到jar的操作，File是原始 jar 包或原始class的文件夹，也可以自定义，一个File对应一个输出的jar包
     *
     * 如果 [isFastDex] = true 此方法只是暂存，最后统一写入jar，[isFastDex] = false 则直接写入jar
     */
    fun File.saveJarEntry(jarEntryName: String,byteArray: ByteArray){
        val jarName = getNewJarName(is1ClassesJar, jarEntryName)

        val entryCaches = jarEntryCaches.computeIfAbsent(jarName) { mutableListOf() }
        synchronized(entryCaches){
            entryCaches.add(EntryCache(jarEntryName,byteArray))
        }
    }
}