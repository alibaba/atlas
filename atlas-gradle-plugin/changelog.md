### 2.3.0.alpha7
1. 修复某些场景下buildcache导致的构建失败
2. 修复 awo 快速调试
3. 升级 dex_patch ,兼容windows ，版本 1.1+
4. 依赖解析优化，避免递归循环

### 2.3.0.alpha4
1. 支持google的最新插件，builder ： 2.3.0 ， gradle ： 3.3  ， buildTools ：25.0.0+
2. 优化atlas插件对官方插件的侵入，允许推荐使用 ：

        apply plugin: 'com.android.application'
        apply plugin: 'com.taobao.atlas'       
3. 对多模块更好的支持，回归原本的本地开发模式
4. 新增2种依赖关系

        providedCompile ： bundle定义运行时依赖，编译运行可见， 最终不会打到bundle的so中
        bundleCompile :  在app中定义bundle的依赖
5. 远程bundle支持