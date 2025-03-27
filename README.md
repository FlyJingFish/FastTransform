<p align="center">
  <strong>
    🔥🔥🔥这是一个万能的注册代码的框架插件
  </strong>
</p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=io.github.flyjingfish.FastTransform"><img
    src="https://img.shields.io/maven-central/v/io.github.FlyJingFish.FastTransform/fast-transform"
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



# 简述

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; 对于使用 AGP8 的 toTransform 的api的各位小伙伴来说都应该接入本框架，或遵循本框架的设计，这将使打包速度显著加快

### 版本限制

版本要求AGP 7.6 以上

## 使用步骤

**在开始之前可以给项目一个Star吗？非常感谢，你的支持是我唯一的动力。欢迎Star和Issues!**

### 1、在 你的插件库的类库中引入插件

```gradle

dependencies {
    implementation 'io.github.FlyJingFish.FastTransform:fast-transform:1.0.0'
}
```

### 2、使用本库


```kotlin

class MyPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.onVariants { variant ->
            val task = project.tasks.register("${variant.name}XXX", MyClassesTask::class.java)
            variant.toTransformAll(task)
        }
    }
}

```


### 最后推荐我写的另外一些库

- [AndroidAOP 帮助 Android App 改造成AOP架构的框架，只需一个注解就可以请求权限、切换线程、禁止多点、一次监测所有点击事件、监测生命周期等等](https://github.com/FlyJingFish/AndroidAOP)

- [OpenImage 轻松实现在应用内点击小图查看大图的动画放大效果](https://github.com/FlyJingFish/OpenImage)

- [ShapeImageView 支持显示任意图形，只有你想不到没有它做不到](https://github.com/FlyJingFish/ShapeImageView)

- [GraphicsDrawable 支持显示任意图形，但更轻量](https://github.com/FlyJingFish/GraphicsDrawable)

- [ModuleCommunication 解决模块间的通信需求，更有方便的router功能](https://github.com/FlyJingFish/ModuleCommunication)

- [FormatTextViewLib 支持部分文本设置加粗、斜体、大小、下划线、删除线，下划线支持自定义距离、颜色、线的宽度；支持添加网络或本地图片](https://github.com/FlyJingFish/FormatTextViewLib)

- [主页查看更多开源库](https://github.com/FlyJingFish)