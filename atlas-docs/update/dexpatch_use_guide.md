# DexPatch使用示例

DexPatch是以动态部署技术方案为基础，以快速解决线上故障为唯一目的的动态化方案。

简单来说，动态部署是针对apk级别的动态升级，DexPatch是针对Bundle级别的动态修复(主dex可以认为是一个Bundle)

详细介绍参照 [DexPatch介绍 ][dexpatch_guide]

# DexPatch与动态部署异同

|不同点| DexPatch|动态部署|
|:----|:----|:---|
|场景定位|bundle级别，代码动态修复|apk动态升级|
|灵活度|各个bundle随时下发|集成升级|
|构建速度|很快|一般|
|生效速度|快|一般|
|修改范围|bundle自身内聚|apk范围|
|versionName|不改| +1|
|java| 支持 |支持|
|so|x|支持|
|resource|x|支持|

# 版本

|依赖|版本|
|:---|:---|
| atlasplugin |2.3.3.rc12-1|
|atlas_core|5.0.7.41|
|atlasupdate|1.1.4.11|


# 打包

Dexpatch需要一个ap版本作为参照，和现在的代码比对做diff。假设当前版本为1.0.0 (gradle中配置)

## 发布版本

如果之前发布过ap版本，可以跳过此节。假设从未发布过ap，按照如下步骤，发布1.0.0的ap

1. 进入app目录下
2. 生成基线版本 `./gradlew clean assembleDebug`
3. 发布ap到仓库中 `./gradlew publish

## 打patch


1. 基于ap所属的版本(1.0.0)，修改代码 <br>以firstbundle为例，将字符串`this is fragment of firstBundle`改为`firstBundle dexPatch`

2. 修改依赖版本，将firbundle中grddle的verion改为`version = '1.0.1'`
	
	> 这里也可以修改firbundle依赖的某个aar的版本。一句话，你要通过版本号告诉编译器，我这个bundle的依赖变了
4. 指定参照的版本，生成dexPatch包<br> `./gradlew clean DexPatchPackageDebug -DapVersion=1.0.0`

PS: 
> 这里要强调一下，代码的修改要内聚。<br>假设A依赖B,修改时，只改A或B自身的代码，不支持修改A与B之间的接口。


# 部署Patch

生成的patch文件在 `app/build/outputs`下，检查是否存在两个文件

- 1.0.0@1.0.0.tpatch
- dexpatch-1.0.0.json


清空 `/sdcard/Android/data/com.taobao.demo/cache/`，并将上述两个文件push到上述路径中


# demo中测试入口

主界面点开侧边栏，点击dexpatch

![][img_dexpatch_click]

重启应用,dexpatch成功~

![][img_dexpatch_result]

[dexpatch_guide]: https://alibaba.github.io/atlas/update/dexpatch.html  
[img_dexpatch_result]: img/dexpatch_result.png
[img_dexpatch_click]: img/dexpatch_ui_click.png