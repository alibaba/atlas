#!/bin/bash
CXX=$1
ARGS=${*:2}
DIR=external/libcxx/buildcmds
echo $ANDROID_BUILD_TOP/$CXX > $DIR/cxx_under_test

echo $ARGS | grep -P '\S+\.cpp\b' > /dev/null
if [ $? -eq 0 ]; then
  echo $ARGS | perl -ne 's/\S+\.cpp\b/%SOURCE%/; print' \
             | perl -ne 's/\S+\.o\b/%OUT%/; print' > $DIR/cxx.cmds
else
  echo $ARGS | perl -ne 's/out\/\S+\/EXECUTABLES\/\S+\.o\b/%SOURCE%/; print' \
             | perl -ne 's/-o\s+\S+\b/-o %OUT%/; print' > $DIR/link.cmds
fi

$CXX $ARGS
