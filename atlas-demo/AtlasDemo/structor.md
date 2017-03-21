##demo的依赖关系
    {
	"awbs":{
		"AtlasDemo:remotebundle:unspecified@awb":[],
		"AtlasDemo:publicbundle:unspecified@awb":[],
		"AtlasDemo:firstbundle:unspecified@awb":[],
		"AtlasDemo:secondbundle:unspecified@awb":[
			"AtlasDemo:secondbundlelibrary:unspecified@aar"
		]
	},
	"mainDex":[
		"AtlasDemo:activitygroupcompat:unspecified@aar",
		"AtlasDemo:lottie:unspecified@aar",
		"AtlasDemo:middlewarelibrary:unspecified@aar",
		"AtlasDemo:splashscreen:unspecified@aar",
		"com.alibaba:fastjson:1.1.45.android@jar",
		"com.android.support.constraint:constraint-layout-solver:1.0.0-alpha8@jar",
		"com.android.support.constraint:constraint-layout:1.0.0-alpha8@aar",
		"com.android.support:animated-vector-drawable:25.3.0@aar",
		"com.android.support:appcompat-v7:25.3.0@aar",
		"com.android.support:design:25.3.0@aar",
		"com.android.support:recyclerview-v7:25.3.0@aar",
		"com.android.support:support-annotations:25.3.0@jar",
		"com.android.support:support-compat:25.3.0@aar",
		"com.android.support:support-core-ui:25.3.0@aar",
		"com.android.support:support-core-utils:25.3.0@aar",
		"com.android.support:support-fragment:25.3.0@aar",
		"com.android.support:support-media-compat:25.3.0@aar",
		"com.android.support:support-v4:25.3.0@aar",
		"com.android.support:support-vector-drawable:25.3.0@aar",
		"com.android.support:transition:25.3.0@aar",
		"com.taobao.android:atlas_core:5.0.6-rc3@aar",
		"com.taobao.android:atlasupdate:1.1.4@aar"
	]
    }
 
 
 ## 一些提示
 
1. MainActivity 通过ActivityGroupCompat 实现展示bundle 内的Fragment，因为bundle只通过Component的方式可以被依赖安装
2. 左侧nativetiondraw里面展示动态部署和远程bundle的测试过程