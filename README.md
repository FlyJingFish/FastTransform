<h4 align="right">
  <strong>English</strong> | <a href="https://github.com/FlyJingFish/FastTransform/blob/master/README-zh.md">简体中文</a>
</h4>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.flyjingfish/fasttransform/"><img
    src="https://img.shields.io/maven-central/v/io.github.flyjingfish/fasttransform"
    alt="Build"
  /></a>
  <a href="https://github.com/FlyJingFish/FastTransform/stargazers"><img
    src="https://img.shields.io/github/stars/FlyJingFish/FastTransform.svg"
    alt="Downloads"
  /></a>
  <a href="https://github.com/FlyJingFish/FastTransform/network/members"><img
    src="https://img.shields.io/github/forks/FlyJingFish/FastTransform.svg"
    alt="Python Package Index"
  /></a>
  <a href="https://github.com/FlyJingFish/FastTransform/issues"><img
    src="https://img.shields.io/github/issues/FlyJingFish/FastTransform.svg"
    alt="Docker Pulls"
  /></a>
  <a href="https://github.com/FlyJingFish/FastTransform/blob/master/LICENSE"><img
    src="https://img.shields.io/github/license/FlyJingFish/FastTransform.svg"
    alt="Sponsors"
  /></a>
</p>

# Brief description

For all friends who use AGP8's toTransform API, they should connect to this framework or follow the design of this framework, which will significantly speed up the packaging speed

### Version restrictions

The version requires AGP 7.6 or above

## Usage steps

**Can you give the project a Star before starting? Thank you very much, your support is my only motivation. Stars and Issues are welcome!**

### 1. Introduce in the class library of your plugin library

```gradle

dependencies {
    implementation 'io.github.flyjingfish:fasttransform:1.0.7'
}
```

### 2. Use this library

```kotlin

class MyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            val task = project.tasks.register("${variant.name}XXX", MyClassesTask::class.java)

            /*
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.ALL)
                .use(taskProvider)
                .toTransform(
                    ScopedArtifact.CLASSES,
                    MyTask::allJars,
                    MyTask::allDirectories,
                    MyTask::outputFile
                )
            */
            variant.toTransformAll(task) //Equivalent to the above
// variant.toTransformAll(task,false) Passing false as the second parameter means using the original unaccelerated logic
        }
    }
}

//Inheriting DefaultTransformTask
abstract class MyClassesTask : DefaultTransformTask() {

    // Equivalent to the method annotated by @TaskAction before
    override fun startTask() {
        /**
         * singleClassesJar() Whether there is only one jar package, returning true means that there was a plug-in using toTransform before, and it did not use this plug-in or did not follow this design
         */
        /**
         * isFastDex is the second parameter passed in when calling toTransformAll
         */
        allDirectories().forEach { directory ->
            directory.walk().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.getRelativePath(directory)
                    val jarEntryName: String = relativePath.toClassPath()

                    FileInputStream(file).use { inputs ->
                        val cr = ClassReader(inputs)
                        val cw = ClassWriter(cr, 0)
                        cr.accept(
                            MyClassVisitor(cw),
                            ClassReader.EXPAND_FRAMES
                        )
                        cw.toByteArray().inputStream().use {
                            //write jar
                            directory.saveJarEntry(jarEntryName, it)
                        }
                    }
                }
            }
        }

        allJars().forEach { file ->
            if (file.absolutePath in ignoreJar) {
                return@forEach
            }
            val jarFile = JarFile(file)
            val enumeration = jarFile.entries()
            while (enumeration.hasMoreElements()) {
                val jarEntry = enumeration.nextElement()
                val entryName = jarEntry.name
                if (jarEntry.isDirectory || entryName.isEmpty() || entryName.startsWith("META-INF/") || "module-info.class" == entryName || !entryName.endsWith(
                        ".class"
                    )
                ) {
                    continue
                }
                jarFile.getInputStream(jarEntry).use { inputs ->
                    val cr = ClassReader(inputs)
                    val cw = ClassWriter(cr, 0)
                    cr.accept(
                        MyClassVisitor(cw),
                        ClassReader.EXPAND_FRAMES
                    )
                    cw.toByteArray().inputStream().use {
                        //write jar
                        file.saveJarEntry(jarEntryName, it)
                    }
                }
            } jarFile . close ()
        }
    }

    override fun endTask() {
        //Write some final work here
    }

}

```

## Directly accelerate existing projects

If your existing project has a plugin that uses `toTransform`, and it does not use this framework or follow the design of this framework, you can choose one of the following methods to accelerate your project

### Method 1

Depend on the plugin in `build.gradle` in the **project root directory**

- New version

    ```gradle
    
    plugins {
        //Required items 👇 Note that the apply setting must be true
        id "io.github.flyjingfish.fasttransform" version "1.0.7" apply true
    }
    ```

- Or old version

    ```gradle
    buildscript {
        dependencies {
            //Required items 👇
            classpath 'io.github.flyjingfish:fasttransform:1.0.7'
        }
    }
    apply plugin: "fast.dex"
    ```

### Method 2

In `build.gradle` in the **app module** Dependency plugins in 

```gradle 
//Required items 👇 
plugins { 
    ... 
    id "io.github.flyjingfish.fasttransform" version "1.0.7" 

```

### Finally, I recommend some other libraries I wrote

- [AndroidAOP helps Android App transform into an AOP architecture framework. With just one annotation, you can request permissions, switch threads, prohibit multiple clicks, monitor all click events at once, monitor life cycle, etc.](https://github.com/FlyJingFish/AndroidAOP)

- [OpenImage easily realizes the animated zoom effect of clicking on a small image in the application to view a large image](https://github.com/FlyJingFish/OpenImage)

- [ShapeImageView supports the display of any graphics. It can do anything you can't think of](https://github.com/FlyJingFish/ShapeImageView)

- [GraphicsDrawable supports the display of any graphics, but it is lighter](https://github.com/FlyJingFish/GraphicsDrawable)

- [ModuleCommunication Solve the communication needs between modules, and have a more convenient router function](https://github.com/FlyJingFish/ModuleCommunication)

- [FormatTextViewLib supports bold, italic, size, underline, and delete for some texts. The underline supports custom distance, color, and line width; supports adding network or local images](https://github.com/FlyJingFish/FormatTextViewLib)

- [Homepage to view more open source libraries](https://github.com/FlyJingFish)