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
