## 如何正确使用Bundle
### bundle的工程配置
 1. bundle自身工程build.gradle里面需要声明为awb:
 	
 	    atlas.bundleConfig.awbBundle = true

 2. 建议修改plugin，更好的支持aar传递依赖等问题（可以采用原生com.android.library）
 	    
 	    apply plugin: 'com.taobao.atlas.library'
 	    buildscript {
		    repositories {
		        mavenLocal()
		        jcenter()
		    }
		    dependencies {
		        classpath "com.android.tools.build:gradle:2.1"
		        classpath "com.taobao.android:atlasplugin:1.0.0" //使用com.taobao.atlas.library时需要配置atlasplugin等classpath
		    }
		}

3. 外部配置
	1. 在主客工程的packageId.properties中声明资源段（也可以通过mtl.tBuildConfig.autoPackageId设置自动分配packageID）
	
	        com.taobao.android:firstbundle=34//groupId:artifactId=NUM
	2. 在主客工程的build.gradle中添加awb依赖

            compile("com.taobao.android:firstbundle:1.0.0@awb")

###  bundle的注意点
遵循代码规范可以有效避免在运行时遇到难以排查的问题。

1. Bundle的AndroidManifest中不能有对bundle内的资源的引用；比如Activity的Theme，需要声明在主apk中。Bundle的Manifest会合并进主Manifest，如果有bundle的资源引用会直接引发构建出错；另外可以选择的方式是AndroidManifest里面不加Theme，改用在Activity的onCreate方法里面调用setTheme的方式
2. Activity通过overridePendingTransition使用的切换动画的文件要放在主apk中；
3. Bundle内的Class最好以特定的packageName开头，resource文件能够带有特定的前缀。这样一来可以避免资源或者类名重复而引起的覆盖，也可以在出现问题的时候及时定位到出问题的模块
4. Bundle内如果有用到自定义style,那么style的parent如果也是自定义的话，parent的定义必须位于主apk中，这是由于5.0以后系统内style查找的固有逻辑导致的，容器内暂不能完全兼容
5. Bundle内部如果有so，则安装时so由于无法解压到apk lib目录中，对于直接通过native层使用dlopen来使用so的情况，会存在一定限制，且会影响后续so动态部署，所以目前bundle内so不建议使用dlopen的方式来使用
 
6. Bundle内有使用主进程的contentProvider,则Bundle的AndroidManifest的contentprovider声明中最好带上

```
	android:multiprocess="true"
	android:process=":XX"
```
这样可以避免主进程一起来就去startBundle导致启动时间过长

### 初始化Bundle
bundle可以被认为是一个小型的apk，因此每个bundle的初始化都是从Bundle的Application开始的。Application被声明在bundle的AndroidManifest.xml里面

**注意点：**虽然启动方式类似与普通app，先执行attachBaseContext，再执行onCreate，不过有两个不同点需要引起注意：

1. Application 初始化的线程基于start bundle时的线程，这样导致了bundle可能会运行在主线程，也可能是在子线程，所以初始化的代码如果对线程敏感的需要注意：如果需要强制主线程的可以通过new Handler(Looper.getMainLooper()).post 去初始化

2. bundle的Application并不是被系统真正认可的Application，所以需要Application作为参数进行初始化的方法可以传入getBaseContext()，Bundle Application的mBase实际上就是apk真正的application，最好不要直接传入bundle的Application本身进行初始化，会存在潜在的风险。

### Bundle如何提供服务给其他Bundle
**Bundle虽然提供了依赖的方式，但是这种方式如果使用不当反而会带来隐患，且与bundle本身的封装性相违背。**通常如果某个中间件是以bundle的形式存在，那声明依赖是可行的。如果两个本身相对独立的业务bundle存在某几个功能接口的依赖，则可以通过服务的方式，服务提供方提供aidl的接口，被使用方或者主apk依赖；同时自己bundle内部实现service的功能。

### 主动启动Bundle
默认情况下，Bundle只往外暴露了android原生的component,运行期按需加载。某些特殊的bundle（比如说监控性质的）本身与其他代码完全独立，且又需要在某个时间点启动运行的，可以通过如下方式去启动：
加上Bundle的PackageName为：com.sample.bundleA

```

        Atlas.getInstance().installBundleTransitivelyAsync(
        new String[]{"com.sample.bundleA"}, 
                new BundleInstaller.InstallListener() {
            @Override
            public void onFinished() {
                BundleImpl impl = (BundleImpl) Atlas.getInstance().getBundle("com.sample.bundleA");
                if(impl!=null){
                    try {
                        impl.start();
                    } catch (BundleException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        
