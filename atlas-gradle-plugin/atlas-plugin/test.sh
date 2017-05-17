cd ../../atlas-demo/AtlasDemo/
./gradlew clean assembleDebug --stacktrace
./gradlew publish

./gradlew clean assembleDebug -DapVersion=1.0.0 --stacktrace
cd ../../atlas-gradle-plugin/atlas-plugin