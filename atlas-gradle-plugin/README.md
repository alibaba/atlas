## atlasplugin

支持 atlas 工程打包的gradle 插件， 基于 google 官方的 android builder （2.2.3）

### 基本概念

1. atlasplugin : 支持atlas的gradle打包插件，支持打 apk 和 awb
2. awb ： 包结构上和 aar 一致， 在 atlas 里每一个 awb 被认为是一个独立的业务模块，有独立的application， awb 之前默认是不能相互依赖 ， 具体参考  `atlas-demo/firstbundle`
3. ap : 基线包， 里面包含该版本对应的 apk 产物及其他打包过程中的中间产物， 是打 patch 包的必要条件

### 主要功能

1. 支持 awb 类型依赖
2. 支持 bundle 的package
3. 支持 动态部署


### 接入

1. 引用插件

		buildscript {
		    repositories {
		        mavenLocal()
		        jcenter()
		    }
		    dependencies {
		        classpath "com.taobao.android:atlasplugin:2.2.3.rc4"
		    }
		}

	注意尽量不要指定 classpath "com.android.tools.build:gradle"的版本，默认使用的是 2.2.3

2. 应用plugin

		apply plugin: 'com.taobao.atlas.application'

	注意不能同时 apply com.android.application

3. 配置打包参数， 具体见 `配置`

4. 执行构建 ./gradlew assembleDebug 或者 assembleRelease

5. 构建产物：

	1. build/outputs/apk/xx.apk , 构建的产物apk   
	2. build/outputs/apk/xx.ap , 构建的基线包， 里面包含 apk 和其他的打包中间配置
	3. build/outputs/dependencyTree-debug.json , 整个工程的依赖树
	4. build/outputs/atlasConfig.json , 整个打包的 atlas 配置参数
	5. build/outputs/packageIdFile.properties , 每个bundle对应的packageId 列表
	6. build/outputs/tpatch-debug , debug 包 patch产物
	7. build/outputs/tpatch-release , release 包 patch产物


### 配置

#### 配置列表

 功能  | 配置名称 |  类型 | 值
 ------------- | ------------- | ------------- | -------------
是否启用atlas  | mtl.atlasEnabled | boolean  | true
自动生成bundle的packageId  | mtl.tBuildConfig.autoPackageId | boolean  | true
预处理manifest， 如果开启atlas，必须为true  | mtl.tBuildConfig.preProcessManifest | Boolean  | true
使用自定义的aapt， 如果开启atlas，必须为true  | mtl.tBuildConfig.useCustomAapt | Boolean  | true
aapt输出的R为常量, 建议值设置为false， 可以减少动态部署的patch包大小  | mtl.tBuildConfig.aaptConstantId | Boolean  | false
合并jar中的资源文件  | mtl.tBuildConfig.mergeJavaRes | Boolean  | false
构建基线包，建议开启，否则后面的patch包无法进行  | mtl.tBuildConfig.createAP | Boolean  | true
合并bundle jar中的资源文件  | mtl.tBuildConfig.mergeAwbJavaRes | Boolean  | false
自启动的bundle列表， 值是 packageName  | mtl.tBuildConfig.autoStartBundles | List  | [com.taobao.firstbundle]
提前执行的方法，格式是 className:methodName|className2:methodName2 ， 注意class和methodname都不能混淆，且方法实现是 class.method(Context)  | mtl.tBuildConfig.preLaunch | String  |
 基线的依赖坐标， 如： com.taobao.android:taobao-android-release:6.3.0-SNAPSHOT@ap   | mtl.buildTypes.debug.baseApDependency | String  | null
 基线的依赖坐标， 如： com.taobao.android:taobao-android-release:6.3.0-SNAPSHOT@ap   | mtl.buildTypes.release.baseApDependency | String  | null
使用atlas的application，包含 atlas基础初始化及multidex逻辑  | mtl.manifestOptions.replaceApplication | boolean  | true
 打andfix patch 包   | mtl.patchConfigs.debug.createAPatch | boolean  | false
 打动态部署 patch 包   | mtl.patchConfigs.debug.createTPatch | boolean  | true
 andfix 打包过滤 class 列表文件   | mtl.patchConfigs.debug.filterFile | File  | null
 andfix 打包过滤 class 列表文件   | mtl.patchConfigs.debug.filterClasses | Set  | []
 打andfix patch 包   | mtl.patchConfigs.release.createAPatch | boolean  | false
 打动态部署 patch 包   | mtl.patchConfigs.release.createTPatch | boolean  | false
 andfix 打包过滤 class 列表文件   | mtl.patchConfigs.release.filterFile | File  | null
 andfix 打包过滤 class 列表文件   | mtl.patchConfigs.release.filterClasses | Set  | []


####  最简配置

    mtl {
        atlasEnabled true
    }




具体参考 `atlas-demo/app/build.gradle`
