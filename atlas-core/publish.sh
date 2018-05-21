#!/usr/bin/env bash
./gradlew clean assemble bintray -DcommitTag=true
git push --follow-tags
