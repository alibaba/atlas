# dexPatch使用示例

dexPatch是基于动态部署的基础上改进而来，所以使用时和动态部署几乎一样，唯一区别的是需要注意几个字段。

关于dexpatch的介绍以及和动态部署的区别，参照[Dexpatch 介绍][dexpatch_guide],本篇只介绍怎么使用...

# 打包

打包步骤和动态部署一致，假设当前基线版本为1.0.0 (gradle中配置)

1. 进入app目录下
2. 生成基线版本 `./gradlew clean assembleDebug publish`
3. 修改代码 <br>以firstbundle为例，将字符串`this is fragment of firstBundle`改为`firstBundle dexPatch ~`
4. 生成dexPatch包<br> `./gradlew clean assembleDebug -DapVersion=1.0.0 -DversionName=1.0.1`

PS: 
> dexPatch改动要内聚，即只改bundle自身的代码，不要修改bundle之间的依赖,如果要大改，推荐使用动态部署方式

# 检查配置文件

生成的patch文件在 `app/build/outputs/tpatch-debug/`下，检查是否存在两个文件

- patch-1.1.2@1.1.1.tpatch
- update-1.0.0.json

检查`update-1.0.0.json`文件，格式类似

```
{
	"baseVersion":"1.0.0",
	"dexPatch":true,
	"updateBundles":[
		{
			"dependency":[],
			"isMainDex":false,
			"name":"com.taobao.firstbundle",
			"srcUnitTag":"491896f216bf0fd57de8ec3b7e5fc9ae",
			"unitTag":"491896f216bf0fd57de8ec3b7e5fc9ae",
			"version":"1.1.1@1.0.8",
			"dexpatchVersion":1
		},
	],
	"updateVersion":"1.0.1"
}
```
有两个字段要注意

|字端|说明|
|---|---|
| dexPatch |开关标志，表示是否一个dexpatch包|
|dexpatchVersion|标识bundle的dexpatch版本<br>__补丁bundle的版本要大于等于客户端中bundle的版本__。否则，bundle将不会被更新。|

# 部署Patch

确保手机上目录存在 `/sdcard/Android/data/com.taobao.demo/cache/`

push文件

```
adb push build/outputs/tpatch-debug/update.1.0.0.json /sdcard/Android/data/com.taobao.demo/cache/update.1.0.0.json

adb push build/outputs/tpatch-debug/patch-*.tpatch /sdcard/Android/data/com.taobao.demo/cache
```

# demo中测试入口

主界面点开侧边栏，点击dexpatch

![][img_dexpatch_click]

重启应用,dexpatch成功~

![][img_dexpatch_result]

[dexpatch_guide]: https://alibaba.github.io/atlas/update/dexpatch.html  
[img_dexpatch_result]: img/dexpatch_result.png
[img_dexpatch_click]: img/dexpatch_ui_click.png