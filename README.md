# Atlas

 atlas-core[![Download](https://api.bintray.com/packages/alibabaatlas/maven/atlas/images/download.svg)](https://bintray.com/alibabaatlas/maven/atlas/_latestVersion)
 atlas-update[![Download](https://api.bintray.com/packages/alibabaatlas/maven/atlasupdate/images/download.svg)](https://bintray.com/alibabaatlas/maven/atlasupdate/_latestVersion)
 atlas-gradle-plugin[![Download](https://api.bintray.com/packages/alibabaatlas/maven/atlasplugin/images/download.svg)](https://bintray.com/alibabaatlas/maven/atlasplugin/_latestVersion)

> A powerful Android Dynamic Component Framework.

Atlas is an Android client-side containerization framework. we call it android dynamic component framework.

Atlas provides decoupled, component, and dynamic support. Atlas covers various issues in the project coding period, Apk run-time and follow-up operation and maintenance.

In the project period, Atlas enable engineers independent development, independent debug, because their project were physical isolation.

In the apk run-time, Atlas has complete component life cycle, class isolation and other mechanisms.

In the operation and maintenance period, Atlas provide rapid incremental update and rapid upgrade capacity.

Atlas put the work into the project period as much as possible, to ensure runtime simple and stable, maintenance easy.

------

Compared with multidex solution, Atlas not only solved the limitation of the method count(65535), but also clarified the boundary of development, provied the powerful capabilities for Android development, such as Parallel Iteration, Rapid Development, Flexible Release, Dynamically Update, Quick Fix Online Failure.

Unlike some Android plugin frameworks, Atlas is a component framework (we call it Bundle), Atlas is not a multi-process framework.

------
You can see there were three main library in this project ([atlas-core](./atlas-core)/[atlas-update](./atlas-update)/[atlas-gradle-plugin](./atlas-gradle-plugin))

* [atlas-core](./atlas-core): This is client-side core library, it's job is to install each bundle, load the classes and resources on-demand when runtime.
* [atlas-update](./atlas-update): This is client-side update library, which provide dexmerge capacity for update or upgrade.
* [atlas-gradle-plugin](./atlas-gradle-plugin): This is Android Studio Gradle Plugin for engineers developing in project period, because we change some android default package mechanisms, include android aapt [atlas-aapt](./atlas-aapt).

## Use Atlas

* [Demo](./atlas-demo)
* Doc: [English](), [中文](./atlas-docs)
* DingTalk im group: Scan the follow QR code or Search group 11727755 using DingTalk(钉钉) app.
![dingtalk.png](assets/dingtalk.png) 


## Support
----------
Atlas support all Android version from Android 4.0 to 7.0. 

Follow is support status.

Runtime | Android Version | Support
------  | --------------- | --------
Dalvik  | 2.2             | Not Test
Dalvik  | 2.3             | Not Test
Dalvik  | 3.0             | Not Test
Dalvik  | 4.0-4.4         | Yes
ART     | 5.0             | Yes
ART     | 5.1             | Yes
ART     | M               | Yes
ART     | N               | Yes

<!--## Contributing

See [Atlas Contributing Guide](./CONTRIBUTING.md) for more information.
 No newline at end of file
-->