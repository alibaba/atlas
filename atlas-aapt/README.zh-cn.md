## atlas-aapt

此项目基于[ShakaAapt](https://github.com/rover12421/ShakaAapt)，该项目抽离了AOSP——android-7.0.0_r6中编译aapt的必要代码。

## 编译环境

具体参考AOSP编译官方文档

## atlas-aapt提供的功能

atlas-aapt在官方aapt的基础上添加了以下扩展选项：

1. --customized-package-id：一般aapt打出来包的资源id都是以7f开头，这个就是应用包资源的package-id。使用这个选项可以指定资源的package-id为其他数值，但是必须小于等于7f。（示例：--customized-package-id 126）。
2. --use-skt-package-name：一般一个包引用另一个包的资源的时候，都必须加上资源的包名来进行引用，比如引用android的资源的时候必须加上`@android:`来进行引用。使用这个选项，可以省去写包名的过程，直接找到资源。这个包的名字通常是atlas中主包的包名。
3. -B：资源动态部署用，如果你想基于一个包来打出这个包的资源patch包，那么使用该选项来指定基础包。