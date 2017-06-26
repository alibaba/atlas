# atlasplugin

## changelog

### 2.3.3.beta1

0. 升级android builder 到 2.3.3版本
1. unit tag 非 diff bundle 保持 不变
2. tBuildConfig.minPackageId 可设置最小的自动分配packageId，默认35（10进制）开始
3. 加强对插件配置使用的校验
      1. 不推荐在atlasplugin之前添加google官方的插件，推荐让atlasplugin自动依赖传递进来
      2. bundle之前的依赖推荐使用bundleCompile， 如果使用compile awb，会有警告日志
      3. awb 之间允许使用 providedCompile 依赖
4. databinding优化，后续bundle的databinding需要独立配置是否启用

        tBuildConfig.dataBindingBundles 
        配置值为 bundle 的packageName 
        类型为 Set<String>
        代码： com.taobao.android.builder.extension.TBuildConfig.dataBindingBundles


### 2.3.1.rc15

1. bundleInfo 增加unique_tag
2. 增加fastMultiDex功能，该功能需要关闭android里的multidexEnabled，充分利用predex和dexcache。配置开关 `atlas.multiDexConfigs.debug.fastMultiDex=true`

### 2.3.1.rc11

1. 支持bundle配置proguard规则，仅支持keep和dontwarn

### 2.3.0.alpha15

1. 支持手淘灰度版本
2. 升级aapt
3. 增加atlasMultiDex功能,默认关闭，mtl.tBuildConfig.atlasMultiDex=true

### 2.3.0.alpha11

1. processManifest 修复 无 application ， 替换的不对
2. awb 增加 process resource 逻辑
3. butterknife 支持 


### 2.3.0.alpha10

1. 修复postprocessmanifest
2. data-bind支持
3. 升级dex_patch，支持远程bundle patch

### 2.3.0.alpha7
1. 修复某些场景下buildcache导致的构建失败
2. 修复 awo 快速调试
3. 升级 dex_patch ,兼容windows ，版本 1.1+
4. 依赖解析优化，避免递归循环

### 2.3.0.alpha4
1. 支持google的最新插件，builder ： 2.3.0 ， gradle ： 3.3  ， buildTools ：25.0.0+
2. 优化atlas插件对官方插件的侵入，允许推荐使用 ：

        apply plugin: 'com.android.application'
        apply plugin: 'com.taobao.atlas'       
3. 对多模块更好的支持，回归原本的本地开发模式
4. 新增2种依赖关系

        providedCompile ： bundle定义运行时依赖，编译运行可见， 最终不会打到bundle的so中
        bundleCompile :  在app中定义bundle的依赖
5. 远程bundle支持

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
		        classpath "com.taobao.android:atlasplugin:2.2.3.alpha+"
		    }
		}

	注意尽量不要指定 classpath "com.android.tools.build:gradle"的版本，默认使用的是 2.2.3

2. 应用plugin

		 apply plugin: 'com.android.application'
         apply plugin: 'com.taobao.atlas'

3. 配置打包参数， ./gradlew atlasList,  具体见 `配置`

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
是否启用atlas  | atlas.atlasEnabled | boolean  | true
自动生成bundle的packageId  | atlas.tBuildConfig.autoPackageId | boolean  | true
预处理manifest， 如果开启atlas，必须为true  | atlas.tBuildConfig.preProcessManifest | Boolean  | true
使用自定义的aapt， 如果开启atlas，必须为true  | atlas.tBuildConfig.useCustomAapt | Boolean  | true
aapt输出的R为常量, 建议值设置为false， 可以减少动态部署的patch包大小  | atlas.tBuildConfig.aaptConstantId | Boolean  | true
合并jar中的资源文件  | atlas.tBuildConfig.mergeJavaRes | Boolean  | false
构建基线包，建议开启，否则后面的patch包无法进行  | atlas.tBuildConfig.createAP | Boolean  | true
合并bundle jar中的资源文件  | atlas.tBuildConfig.mergeAwbJavaRes | Boolean  | false
是否依赖冲突终止打包  | atlas.tBuildConfig.abortIfDependencyConflict | boolean  | false
是否类冲突终止打包  | atlas.tBuildConfig.abortIfClassConflict | boolean  | false
自启动的bundle列表， 值是 packageName  | atlas.tBuildConfig.autoStartBundles | List  | [com.taobao.firstbundle]
提前执行的方法，格式是 className:methodName|className2:methodName2 ， 注意class和methodname都不能混淆，且方法实现是 class.method(Context)  | atlas.tBuildConfig.preLaunch | String  |
 基线的依赖坐标， 如： com.taobao.android:taobao-android-release:6.3.0-SNAPSHOT@ap   | atlas.buildTypes.debug.baseApDependency | String  | null
 基线的依赖坐标， 如： com.taobao.android:taobao-android-release:6.3.0-SNAPSHOT@ap   | atlas.buildTypes.release.baseApDependency | String  | null
使用atlas的application，包含 atlas基础初始化及multidex逻辑  | atlas.manifestOptions.replaceApplication | boolean  | true
 打动态部署 patch 包   | atlas.patchConfigs.debug.createTPatch | boolean  | true
 打动态部署 patch 包   | atlas.patchConfigs.release.createTPatch | boolean  | false


####  最简配置

    atlas {
        atlasEnabled true
    }




具体参考 `atlas-demo/AtlasDemo/app/build.gradle`
