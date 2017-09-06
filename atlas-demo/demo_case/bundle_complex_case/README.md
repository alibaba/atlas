# Tree for Project

```
├── README.md
├── app
│   ├── build.gradle
│   ├── libs
│   ├── proguard-rules.pro
│   └── src
├── componets
│   ├── base
│   ├── firstbundle
│   ├── publicbundle
│   └── secondbundle
├── build.gradle
├── gradle.properties
├── gradlew
└── settings.gradle
```


|module|instruction|
|:---|:---|
|app|1. no JAVA or Native code<br>2. Just gradle or other config for pkg build|
|base|1. like traditional app modle<br>2. put it here so that other componets can depend on it directly|
|xxxbundle|bundle modlue|

# Tree for componets

```
├── base
│   ├── build.gradle
│   ├── libs
│   └── src
├── firstbundle
│   ├── build.gradle
│   ├── bundleBaseInfoFile.json
│   ├── libs
│   └── src
├── publicbundle
│   ├── build.gradle
│   ├── libs
│   └── src
└── secondbundle
    ├── build.gradle
    ├── libs
    └── src

```

# Usage for bundle dependencies


`bundleBaseInfoFile.json`

```
{
  "name": "firstbundle",
  "dependency": ["com.taobao.atlas.complex.publicbundle"]
}
```

means `Firstbundle` depend on `PublicBundle`


# Usage for bundleFragmen

see code In baseModule and SecondModule 

# Usage for base module dependencies

you can see `build.gradle`and usage like `Env.Debug` in bundle module.

in sample ,nearly all of bundle are depend on base moduel。