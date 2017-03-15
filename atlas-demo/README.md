## Project Structure

firstbundle :

Business component, the bundle module in the atlas, type is the awb which structure similar to aar

app:

Apk project, contain atlas and update function, integrates the above business components(first bundle)


## APK build

Specific reference `buildApk.sh`

General steps are as follows:
1. The first release bundle version1 to the repository
2. App dependency on bundle versin1, execute ./gradlew assembleDebug
3. Installation


## Building patch package

Specific reference `buildTpatch.sh`

General steps are as follows:

1. release bundle version2 to the repository
2. The app dependency on bundle version2,
3. Modify the app project source and depend on the version of the component (optional)
4. Modify the app versionCode, and release (required)
5. ./gradlew clean assembleDebug -DapVersion=1.0 build patch
6. Push the patch files to the app cache directory
7. Calls the update patch installation on the phone
