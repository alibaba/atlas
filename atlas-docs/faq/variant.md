## 如何构建不同的包

google插件对应的文档地址： https://developer.android.com/studio/build/build-variants.html?hl=zh-cn ， 原生是支持配置不同的buildType 以及 flavor 的， 先看一下功能对比：


### 当前的支持度


| 插件  | falvor  | 自定义buildType |
|:------------- |:---------------:| -------------:|
| google插件      | 支持 |         支持 |
| atlasplugin 构建apk      | 支持        |       不支持 |
| atlasplugin 构建动态部署   | 不支持        |     不支持   |


### 为什么没有去完全适配

1. Atlas是面向多人协作的较大工程的，配置不同的buildtype和flavor中也会带来创建更多的任务，并且如果执行assembleDebug 之类会构建所有的依赖变种，导致整体构建效率大大降低。
2. 开发精力所限，因为我们的淘宝app就是没有变种的架构，额外的功能会带来很多适配的麻烦和风险
3. 开发期我们期望我们的基线足够稳定，这样可以避免动态部署基线不兼容的问题


### 当前插件下的实践

因为build.gradle 里可以写很多的逻辑，所以配置也可以变的很灵活。 我们一般的做法就是通过打包参数来控制不同的参数值， 具体的demo可以看下下面的介绍，至于具体的其他实现，大家也可以自行扩展


#### 大致步骤

1. 在build.gradle里定义一些基础的变量，比如：

		//通过增加判断逻辑，打出不同类型的定制包
		def appId = "com.taobao.demo"
		def minVersion = 14
	
	
2. 增加一层逻辑控制根据不同的参数来修改之前定义变量的值 ， 这样如果我们打包加了 -Pbeta, 就会使用新的值了

	
		if (project.hasProperty("beta")) {
			appId = "com.taobao.atlas.beta"
			minVersion = 21
		}
	
3. 或者直接在配置的地方加上判断逻辑，使用特殊的配置
	
	
		android {
		    defaultConfig {
		        //通过增加判断逻辑，打出不同类型的定制包
		        if (project.hasProperty("beta")) {
		            buildConfigField "boolean", "API_ENV", "false"
		        }else{
		        	buildConfigField "boolean", "API_ENV", "false"
		        }
		    }
	
4. 以上build.gradle 里的配置基本完成。 后续如果要打特殊包就可以通过修改配置的构建参数来控制打具体的包了，如：

		   ./gradlew clean assembleDebug :  构建标准包
		   ./gradlew clean assembleDebug -Pbeta ： 构建特殊包

