#### 1. Main Function

* Compile `Mobile Taobao Android`, `Mobile Youku Android`, `Mobile Cainiao Android` quickly. Less than a minute
* Build dynamic deploy patches with one single click, And install these patches to you android phone
* Create a `Atlas project`  or `Atlas Bundle`



#### 2. Installation

##### 2.1 Install from JetBrains Plugin repositories

1. Open `Preferences -> Plugins -> Browse Repositories`
2. Input `AtlasBuildTool`
3. Install And Restart `Idea`

##### 2.2 Install Local

1. Download Plugin
2. Open `Preferences -> Plugins -> Browse Repositories`
3. Install Plugin from disk...
4. Select downloaded plugin
5. restart `Idea`



#### 3. Usage

##### 3.1 Create Atlas Project or Bundle 



##### Create Atlas Project

1. `File -> New -> Atlas Projects... -> Project`.

2. It looks like creating a Normal `Android Project`  in first few steps. 

3. Then you will see the final step like this. 

   ![createAtlasProjectAndConfig](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/createAtlasProjectAndConfig.png)
     undefined**Atlas Gradle Plugin** : A Gradle Tool to build atlas project
   2. **Atlas Core**  :  Atlas Core runtime library
   3. **Atlas Update** : A Library make `Atlas` project can update through patches. (Optional) 

   ​Go to [Here](https://alibaba.github.io/atlas/guide-for-use/guide_for_build.html) to get the detail infomation of these three `Lib`

4. Then **Finish**
5.  You will get a Android Project with all Atlas depdencies after **Finish**, And the `app` is the `main dex`



##### Create Atlas Bundle

You may need to add an `Atlas bundle` to your `Atlas project` after it is created. 

So you can make a integrated experience of `Atlas`. 

Create a bundle like this :  

1. `File -> New -> Atlas Projects... -> Bundle`.

2. It looks like creating a Normal `Android Bundle`  in first few steps. 

3. Then you will see the final step like this. 

   ![createAtlasBundle](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/createAtlasBundle.png)

   Note that this step will be displayed only if this bundle is a `Android library`

   And **First of all there is must a Main dex module in current project**

   1. **Main Dex** : The Main Dex of this project. 
   2. **Bundle Property :** 
      1. **autoStart :** If this is selected, this bundle will be auto started after atlas turn alive
      2. **outOfApk :** It is means this bundle will be marked as a remote bundle. It is against with autoStart
      3. **dataBinding :** mark this bundle as a databinding bundle

   [Details](http://atlas.taobao.org/docs/guide-for-use/guide_for_build.html)


##### 3.2 Add Atlas to a exist project

1. `Tools -> Atlas -> Configure Atlas in Project`

   ![configureAtlas](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/configureAtlas.png)

2. And You will see something like this

   ![configureAtlasinProject](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/configureAtlasinProject.png)

3. Click Ok or you can select these lib's version before. `Atlas`'s properties will be added into this project immediately.

##### 3.3 Build Atlas dexPatches And install

First of all you should have some knowledge about [dexPatch](https://alibaba.github.io/atlas/update/dexpatch.html)

1. Add a Deploy Runconfiguration

   ![EditDeployConfiguration](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/EditDeployConfiguration.png)

   ![addAtlasDeployRunconfiguration](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/addAtlasDeployRunconfiguration.png)

2. Configure 

   ![configDeployConfiguration](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/configDeployConfiguration.png)

   **①. MainDexModule:** The MainDex Of this project  
   **②. Deploy:** Dynamic type,  only DexPatch for now  
   **③. apVersion :** ap's version  
   **④. versionName :** dex patch's versionCode, The Main app's version will be this `versionCode` after it is updated successfully  
   ⑤. **Increase versionName automatically :** it is means whether to increase the versionCode after the build action is done  


3. The click `Run`



##### 3.4 Build Atlas dexPatches And install

**This Function is only for Alibaba developers for the moment.**

**Make us know if you want to use this function**

1. Add a Run Runconfiguration

    ![addRunConfiguration](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/addRunConfiguration.png)  

    ![addAtlasRunConfigureAdd](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/addAtlasRunConfigureAdd.png)  

2. Configure

   ![RunconfigurationModules](https://raw.githubusercontent.com/Darin726/altasPlugin/master/resource/pic/RunconfigurationModules.png)

   * **Ap Address :** Ap's Download. 
   * **Selected App :** The fast build app. `Mobile Taobao Android `,`Mobile Taobao Youku`, `Mobile Taobao Cainiao` are supported. Make us know if you want to use this function
   * **Build Type :**  Apk or Dynamic
   * **Build Modules :**  The Modules can be build to `selected app`. one module should be selected at least. 
   * **Source | Coordinate :**  Two ways of building project. It is like `compile project` And `compile coordinate` in gradle.
   * **Selected : ** Only the selected module will be build to the final artifact `Apk or Patches`
   * **Transitive : **  Gradle's transitive property, [Detail](https://docs.gradle.org/current/dsl/org.gradle.api.artifacts.Configuration.html#org.gradle.api.artifacts.Configuration:transitive)
   * **Custom Modules :**  You Can add another coordinate dependency here. this lib will be build into the  final artifact `Apk or Patches`

3. Then click run to enjoy it

  ​
