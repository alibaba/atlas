
#Atlas   [Browse this in English ](en/index.html)

[<img src='https://render.alipay.com/p/s/taobaonpm_click/atlas_aliyun_logo' align='right' style=' width:360px;height:120 px'/>](https://render.alipay.com/p/s/taobaonpm_click/atlas_aliyun_logo_click) 
<img src='dingtalk.png' align='left'style=' width:130px;height:130 px'/>
<br>扫码加入atlas钉钉交流群</br>  
<br />
<br />
<br />
<br />
<br />
 
## 简介
Atlas是伴随着手机淘宝的不断发展而衍生出来的一个运行于Android系统上的一个容器化框架，我们也叫动态组件化(Dynamic Bundle)框架。它主要提供了解耦化、组件化、动态性的支持。覆盖了工程师的工程编码期、Apk运行期以及后续运维期的各种问题。

Atlas对app的划分如下图所示:

![][relation]

|拆分|定位|说明|
|:---|:---|:---|
|host|基础支持|包含独立的中间件，以及一个Base的工程，里面可能包含应用的Application，应用icon等基础性内容|
|bundle|业务层基本单位|__运行期按需动态加载__。bundle可以调用host的代码和资源，同时bundle之间允许存在依赖关系。|


> 与插件化框架不同的是，Atlas是一个组件框架，Atlas不是一个多进程的框架，他主要完成的就是在运行环境中按需地去完成各个bundle的安装，加载类和资源。

## Atlas提供哪些功能

__从app的开发期--运行期--上线后的运维期，Atlas提供了一整套完整的支持__

|周期|说明|
|:---|:---|
|工程期|实现host、bundle独立开发、调试的功能，bundle独立|
|运行期|实现完整的组件生命周期的映射，类隔离、资源共享等机制|
|运维期|增量更新修复能力，提供对class、so以及资源的增量更新修复能力，快速升级|

以一个app的开发的生命周期为例，参见 [demo][demo]

### 开发期

提供gradle插件，简化开发者接入的负担。

需要说明的是，gradle插件不会侵入正常的开发流程，host和bundle都可以独立的进行开发和调试。

|plugin|说明|
|:---|:---|
|com.taobao.atlas|用于host工程，用于设置对bundle的依赖和其它atlas的功能|
|com.taobao.atlas.library|对应bundle工程，用于将module转化成atlas所需要的bundle依赖，不会侵入正常的开发环境|


### 运行期

|功能|说明|
|:---|:---|
|四大组件支持|支持运行bundle中的四大组件|
|共享代码资源|bundle可以直接使用host中的代码和资源|
|bundle按需加载|业务需要时，才会去加载对应bundle中的代码和资源|
|远程bundle|减少包体积。不常用的bundle放在云端，需要时按需下载。当用户设备空间紧张时,可以清理掉一些长期不用的组件|
|解释执行|为了降低用户等待时间，Atlas框架在dalivk系统上首次使用bundle时关闭了verify，在ART系统上首次使用时关闭了dex2oat走解释执行。同时后台通过异步任务走原生的dexopt过程，为下次使用做好准备|


### 运维期 

__动态部署__ 是容器一个最重要的功能。基于此:

- 业务可以灵活发布自己的需求
- 有故障的业务可以及时修复或者回滚
- 同时动态部署的快速覆盖能力在灰度等场景下可以更快地收到所需的效果。

| 功能 | 说明 |
|:---|:---|
|动态部署|构建时会与之前版本的dex进行字节码级别的diff，生成tpatch包。最终下发到用户手机的patch仅包含变化class组成的dex和更改或者新增的资源文件|
|dexpatch|以动态部署技术方案为基础，可以看作是动态部署的子集，专注于单个bundle的故障修复。由于做的事小而精，所以编译构建速度、线上生效速度都是远远快于动态部署|

## 如何接入

我们比较倾向于用一个比较干净的壳子工程来作为容器架构下的打包工程,称之为__main_builder__，__main_builder__建议只存在AndroidManifest.xml、构建文件及部分资源内容。

- manifest：文件用于单独管理apk的icon，版本号，versioncode等；
- gradle：用于配置主apk的依赖及控制构建参数

以 [demo][demo] 为例

1. 引用Atlas插件及依赖仓库，修改工程gradle文件

	```
	buildscript {
		repositories { jcenter()}
		dependencies {
			classpath "com.taobao.android:atlasplugin:2.3.3.beta2"
		}
	}
	```
2. bundle接入，修改bundle的gradle，参见[demo][demo]中的firstbundle配置
	
	```
	apply plugin: 'com.taobao.atlas.library'

	atlas {
		//声明为awb 即bundle工程
    	bundleConfig { awbBundle true }
	}
	```
3. 容器接入，修改app模块的gradle文件

	```
	apply plugin: 'com.taobao.atlas.application'
	dependencies {
		//核心sdk
    	compile('com.taobao.android:atlas_core:5.0.0@aar') {
     		transitive = true
 		}
 		//如果不需要用到atlas的动态部署功能，不需要依赖atlasupdate
 		compile 'com.taobao.android:atlasupdate:1.0.8@aar'
 		
 		//设置bundle依赖
 		bundleCompile project(':firstbundle')
 	}
 	atlas {
      atlasEnabled true
      //...
   }
	```

至此，接入完成，不需要接入方调用任何初始化函数，详细的文档参 [接入文档][atlas_doc_guide_use]，

## Support

支持版本 4.0 to 7.0. 

Runtime | Android Version | Support
------  | --------------- | --------
Dalvik  | 2.2             | Not Test
Dalvik  | 2.3             | Not Test
Dalvik  | 3.0             | Not Test
Dalvik  | 4.0-4.4         | Yes
ART     | 5.0             | Yes
ART     | 5.1             | Yes
ART     | M               | Yes
ART     | N               | Yes
	
## 其它

- [FAQ][faq] 
- [文档][atlas_doc]
- [Atlas-手淘组件化框架的前世今生和未来的路][atlas_histroy]

[relation]: img/relation.png
[demo]: https://github.com/alibaba/atlas/blob/master/atlas-demo/AtlasDemo
[atlas_histroy]: https://mp.weixin.qq.com/s?__biz=MzAxNDEwNjk5OQ==&mid=2650400348&idx=1&sn=99bc1bce932c5b9000d5b54afa2de70e
[atlas_doc_guide_use]: https://alibaba.github.io/atlas/guide-for-use/guide_for_build.html
[atlas_doc]: https://alibaba.github.io/atlas/
[faq]: https://alibaba.github.io/atlas/faq/question.html
[atlas_dev]: img/atlas_dev.svg
[atlas_runtime_maintenance]: img/atlas_runtime_maintenance.svg