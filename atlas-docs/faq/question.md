**Q1: 关于bundle中的application类，它什么时候被调用，其中的自定义方法怎么在bundle中调用？在bundle中调用getApplication方法，得到的是主应用的application对象，不是bundle中定义的。**

A: 首先要明确一点，bundle中的application不是最终在android系统上执行的application。我们之所以保留bundle application的设计是为了尽最大可能保留大家android的开发习惯。

bundle中的application类，是在bundle第一次安装的时候，atlas会调用application的onCreate()函数，可以在里面写一些自己bundle需要用到的初始化代码。注意不要bundle application里用this这种代码，因为是不生效的。

bundle中的application类的自定义方法，我们建议不要这么使用。可以抽离出公共的方法，放在其他地方供调用。 目前是有这个限制。如果非得在里面调用，建议改为static方法。

**Q2: 集成框架后就只有uses-permission，小米推送需要permission就提示缺少权限声明。**

A: 这是因为打包插件默认开启了去除自定义权限。 可通过属性开关关闭。 属性开关是atlas.manifestOptions.removeCustomPermission = false

**Q3: proguard-rules.pro直接定义在宿主app里面就可以了吗，不需要每个bundle都使用吧？**

A: 在宿主app里边定义就好了

**Q4: 请问 客户端拿什么属性 跟服务器比对获取补丁？**

A: 每一次补丁的发布都是相当于一次版本的升级，通过版本号的变更来决策补丁的下发

**Q5: bundle中的9patch图显示有问题，不能拉伸了。是需要有特殊设置么？**

A: alpha11版本发布解决了。

**Q6: 对于gradle 2.3.1版本是不是支持？**

A: 是的，支持2.3.x

**Q7: 导入demo后，Android Studio编译按钮编译失败。**

A: 关闭InstantRun可以正常工作。InstantRun支持正在开发中。

**Q8: Unable to delete file E:\GitHub\atlas\atlas-demo\AtlasDemo\app\build\intermediates\exploded-aar\com.android.support\animated-vector-drawable\25.3.0\jars\classes.jar**

A: 1. 杀掉java.exe进程重新编译（Windows 系统进程出现）cmd:taskkill /F /IM java.exe 2. 关闭掉as run on demand 开关

**Q9. atlas 的能力问题， 及与 andfix 的关系**

A: 1. atlas支持所有的代码及资源更新，暂时不支持新增4大组件，下个大版本支持 2. andfix只要用于修复java代码，不重启生效。 atlas能力更强，修复只是其中一部分能力

**Q10. AS 上重新编译安装不生效的问题**

A: atlas 在覆盖安装的时候，由于bundle的版本号没变化，没做清理， 下个版本会清理bundle

**Q11. 动态部署不生效的问题**

A: 不生效要看提示log再查，目前已知的已经修复，之前是由于windows句柄没关闭，导致tpatch中包含一些空文件，在dexmerge的时候失败了

**Q12. 混淆后atlas进度条消失**

A： keep 规则少了

**Q13. 自启动的bundle是在哪个线程中啊，不是主线程吗？**

A: 自启动bundle可以在任何线程中执行，不过bundle的application onCreate方法、Activity的onCreate方法等都是在主线程回调的

**Q14. 在其他进程里运行的插件，需要在什么时候安装？可不可手动控制安装的时机？**

A: 在需要的时候按需安装，例如一个Service是在单独的进程中，只要需要的时候去bind就会触发这个bundle的安装。可以手动控制安装时机，按需就好。

**Q15. 组件中有jar包和so依赖，awb的产物中也有，为什么在宿主中生成的so产物解压就没有了呢？**

A: 在打整包的时候需要配置过滤的参数，把so的架构类型指定清楚，要不然就会被裁减掉。具体可以参考 ： https://github.com/alibaba/atlas/issues/68

**Q16. app有多个进程，在一个进程中出现了host中的一个类被pathClassloader加载了，之后在该类中调用Class.forName加载插件里的一个类，因为classloader是PathClassLoader,所以报了classnotfound,理论上host中除了atlas相关的类外所有的class都应该是被DelegateClassLoader加载吧？**

A: 这种问题属于宿主中的class引用了bundle中的class，理论上是可以支持的，但是在Atlas的框架中是不推荐(也没不支持)这种反向的依赖关系。

**Q17. altas是不是不建议bundle之间相互调用资源或调用宿主的资源?**

A: Atlas框架建议bundle调用宿主的资源，而不建议bundle之间资源的相互调用。

**Q18. 宿主和bundle的关系？**

A: 宿主就相当于Android Framework，正常的Android代码中我们可以调用Framework中的接口以及使用其包含的资源，宿主对于bundle而言也是这个样子的，假如多个bundle使用同一个资源，那么这个资源可以放在宿主里边，多个bundle同时依赖这个宿主，这样避免了资源的重复。(甚至可以把宿主看成一个AAR，这个AAR的内容是打包在主dex中的)

**Q19. Atlas项目中：AtlasDemo和基于版本打包的demo这两个项目有什么区别？**

A: 一个是基于源码项目，一个是基于仓库版本依赖的。前期研究可以基于源码来，后续工程化实践推荐使用后者，手淘内部就是使用后者的。

**Q20.  awb是什么？**

A: awb是我们发明的格式，其内部结构和aar一样。 awb对应于bundle，编译最后的产物是生成到最后整包的so中。具体请参照 http://atlas.taobao.org/docs/principle-intro/Project_architectured.html