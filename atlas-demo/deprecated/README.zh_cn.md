## 工程结构

firstbundle :

    业务组件，atlas里的bundle模块， 类型是awb，结构和aar相似

app:

    apk工程， 包含 atlas 和 更新功能， 集成了 上面的业务组件


## apk 构建

具体参考 `buildApk.sh`

大致步骤如下：

1. 先发布bundle version1 到仓库
2. app 依赖 bundle versin1， 执行 ./gradlew assembleDebug
3. 安装


## 构建patch包

具体参考 `buildTpatch.sh`

大致步骤如下：

1. 先发布bundle version2 到仓库
2. app 依赖 bundle versin2，
3. 修改app工程的源码和依赖的组件版本（可选）
4. 修改app的 versionCode ， 和发布版本（必选）
5. ./gradlew clean assembleDebug -DapVersion=1.0 构建patch
6. 把patch文件上传到手机app的cache目录
7. 在手机上调用update做patch的安装
