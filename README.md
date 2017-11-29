# Atlas

[![license](http://img.shields.io/badge/license-Apache2.0-brightgreen.svg?style=flat)](https://github.com/alibaba/atlas/blob/master/LICENSE)
[![Release Version](https://img.shields.io/badge/atlas_core-5.0.7.55-orange.svg)](https://github.com/alibaba/atlas/releases/tag/5.0.7.55) 
[![Release Version](https://img.shields.io/badge/atlasupdate-1.1.4.14-blue.svg)](https://github.com/alibaba/atlas/releases/tag/5.0.7.55) 
[![Release Version](https://img.shields.io/badge/atlasplugin-2.3.3.rc41-green.svg)](https://github.com/alibaba/atlas/releases/tag/5.0.7.55) 

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
* Doc: [English](https://alibaba.github.io/atlas/en/index.html), [中文](https://alibaba.github.io/atlas/index.html)
* DingTalk im group: Scan the follow QR code or Search group 11727755 using DingTalk(钉钉) app.
![dingtalk.png](assets/dingtalk.png) 


## Support
----------
Atlas support all Android version from Android 4.0 to 8.0. 

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
ART     | 8.0             | Yes

<!--## Contributing

See [Atlas Contributing Guide](./CONTRIBUTING.md) for more information.
 No newline at end of file
-->