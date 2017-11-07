title: 动态部署排查经验
date: 2014-12-22 12:39:04



# 概述

经常有同学在答疑群上提出疑问`为什么动态部署不生效？`，然后就是一通排查。由于是远程交流，代价实在是太高。这篇文章主要是和大家分享一下动态部署排查的一些经验，希望能够帮助大家快速定位、解决问题。

另外，根据历史经验，大部分都是使用不规范造成的，为了避免这些奇奇怪怪的问题，大家最好按照[Atlas工程结构指南][atlas_use_guid]以及[容器接入][guide_for_build] 这两篇文章提到的规范，来组织工程结构。


# 快速排查三连


从demo中一个例子开始，假设我们发了1.0.0的Ap,之后修改了splashscreen和firstbundle两个地方的代码,__并且修改了对应的版本号__

||基线版本|新版本|
|:---|:---|:---|
|整包(apk)|1.0.0|1.0.1|
|splashscreen（maindex）|1.0.0|1.0.1|
|firstbundle（bundle）|1.0.1|1.0.2|


## 1. 打包产物检查

首先检查打包的产物，按照历史经验，90%的问题都是因为打包产物不对造成的。


### 第一点，依赖变动

打开产物中的`dependencyDiff.json`的文件，这个文件记录了本次改动相对`AP`的依赖变化信息

```
{
	"awbDiffs":["com.atlas.demo:firstbundle"],
	"mainDexDiffs":["com.atlas.demo:splashscreen(1.0.0@aar=>1.0.1@aar)"],
	"modifyLines":[
		"com.atlas.demo:firstbundle(1.0.1=>1.0.2)"
	],
	"newAwbs":[]
}
```

可以看到，firstbundle 和 splashscreen 的版本变化和改动一致，没有问题。

如果对不上，说明打包产物错误，下面是两个常见的原因，需要同学们自己做一下排查。

|根本原因|可能的错误操作|
|:---|:---|
|修改引入了其它隐含的改动|在发布lib/bundle版本时，带动了其它lib／bundle的版本变化|
|AP不对,mvn仓库中的AP，和预期的不是同一个|1.重新publish了AP,老AP被覆盖了<br>2.gradle在本地仓库做了一份cache，是否和远程仓库中的一致|





### 第二点，验证配置文件

打开`update-1.0.0.json`

```
{
	//基线版本
	"baseVersion":"1.0.0",
	"updateBundles":[
		{
			"dependency":[
				"com.taobao.publicBundle"
			],
			"isMainDex":false,
			"name":"com.taobao.firstbundle",
			//基线依赖1.0.0中的唯一tag
			"srcUnitTag":"1lxlheevwoeeg",
			//动态部署版本1.0.1的tag
			"unitTag":"1x6r8iuzs90ff",
			"version":"1.0.0@1.0.2"
		},
		{
			"isMainDex":true,
			"name":"com.taobao.maindex",
			"srcUnitTag":"1nf3phs9djbuj",
			"unitTag":"31jv86qvt1gc",
			"version":"1.0.0@1.0.1"
		}
	],
	//动态部署更新版本
	"updateVersion":"1.0.1"
}
```

1. 涉及改动确认，配置中的diff信息和我们的改动一致（两个部分，firstbundle和maindex）

2. 涉及版本确认，`baseVersion`(1.0.0), `updateVersion`(1.0.1),分别对应打包命令的`apVersion`和`versionName`,表明是1.0.0->1.0.1的diff信息。
3. srcUnitTag 和 unittag确认。（如果同学们没有按照规范来，这个地方很容易出错，导致动态部署失败）

关于第三个，我们以firstbundle为例，详细说明。首先，看下在配置文件`update-1.0.0.json`中的值:

|key|val|
|:---|:---|
| srcUnitTag | 1lxlheevwoeeg |
| unitTag | 1x6r8iuzs90ff |

ok,我们再解压1.0.0的ap文件，打开`atlasFrameworkProperties.json`文件，关键字搜索`com.taobao.firstbundle`

```
{
	"bundleInfo":[
		{
			"pkgName":"com.taobao.firstbundle",
			"unique_tag":"1lxlheevwoeeg",
			"version":"1.0.0@1.0.1"
		},
		//...
	]
}
```

各位，有没有灵光一闪？是的，unittag和srcunittag是有联系的，客户端用来验证patch的合法性，看图:

![][img_unit_tag_list]

以上是正确的使用姿势，下面是常见的一些常见的错误

|错误|原因|
|:---|:---|
|`update-xxx.json`中bundle的srcUnittag和`AP`中的unitttag不一样|AP不对，比对的AP和预想的不是同一个|
|在update-xxx.json文件中，某个bundle的srcUnittag和unittag一样|通常来说都是改了代码，但是没有变更依赖lib/bundle的版本号|

	

### 第三点，diff代码验证

解压tpatch

```
└── patch-1.0.1@1.0.0
    ├── libcom_taobao_firstbundle
    │   └── classes.dex
    └── libcom_taobao_maindex.so
```

反编译Dex，查看patch出来的代码和修改的是否一致。


比如修改了firstbundle 中的`first.java`和splashscreen中的`maindex.java`,diff出来的产物就一定如上图所示:

- libcom\_taobao\_firstbundle，反编译classes.dex，有且只有`first.class`这个diff
- libcom\_taobao\_maindex.so，解压反编译classes.dex，有且只有`maindex.class`这个diff
- 改了几个类这里就会对应diff出几个，__不会多也不会少__。


如果有异常，和第上面一样

- 还是AP有问题
- 或者本次修改隐含带入了其它改动。


## 2. 客户端检查

通常来说，如果打包产物如果没有问题，动态部署一般都是可以正常生效的。

首先，__确保手机存储卡容量足够(至少100m吧)__，然后在进行下面的排查步骤。

按照文档，触发patch更新操作，观察本地上两个地方

```
/data/data/\${pkg}/files/bundleBaseline/updateinfo
/data/data/pkg/files/storage/\${bundle\_pkg\_name}/bundle.zip
```

pkg为app的包名

如果同时存在`updateinfo`和`updateinfo_new`两个文件,以`updateinfo_new`为准

bundle\_pkg\_name为bundle的包名，maindex的包名为`com.taobao.maindex`

### 重启前

在动态部署提示重启前，执行 `cat updateInfo` 命令

```
1.0.1Ecom.taobao.maindex@31jv86qvt1gc;com.taobao.firstbundle@1x6r8iuzs90ff;
```
格式很简单

|动态部署版本|maindex tag|firstbudnle tag|
|:---|:---|:---|
|1.0.1|31jv86qvt1gc|1x6r8iuzs90ff|

对应前面打包产物`update-1.0.0.json`中的记录的unittag，数据没有问题。

查看merge的产物是否存在，以firstbundle为例，maindex同理。

cd到和`/data/data/com.taobao.demo/files/storage/com.taobao.firstbundle`

```
bullhead:/data/data/com.taobao.demo/files/storage/com.taobao.firstbundle # ls
1x6r8iuzs90ff lock 
```
目录名为记录的tag`1x6r8iuzs90ff`,cd 进去

```
bullhead:/data/data/com.taobao.demo/files/storage/com.taobao.firstbundle/1x6r8iuzs90ff # ls
bundle.dex bundle.zip lock meta
```
可以看到产物merge成功了，反编译budnle.zip（apk），可以验证改动



### 重启后

还是查看这两个文件，updateinfo的内容有没有变，或者storage下merge后bundle的产物是否存在

```
/data/data/\${pkg}/files/bundleBaseline/updateinfo
/data/data/pkg/files/storage/\${bundle\_pkg\_name}/bundle.zip
```

没有异常，说明本次动态部署生效。

如果有异常，返回检查第一步的产物。







[atlas_use_guid]: https://alibaba.github.io/atlas/
[guide_for_build]: https://alibaba.github.io/atlas/guide-for-use/guide_for_build.html
[img_unit_tag_list]: dynamic_help_img/unit_tag_list.png

