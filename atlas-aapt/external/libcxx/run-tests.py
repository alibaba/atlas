#
# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
from __future__ import print_function

import argparse
import os
import subprocess
import sys


THIS_DIR = os.path.dirname(os.path.realpath(__file__))
ANDROID_DIR = os.path.realpath(os.path.join(THIS_DIR, '../..'))


class ArgParser(argparse.ArgumentParser):
    def __init__(self):
        super(ArgParser, self).__init__()
        self.add_argument(
            '--compiler', choices=('clang', 'gcc'), default='clang')
        self.add_argument(
            '--bitness', choices=(32, 64), type=int, default=32)
        self.add_argument('--host', action='store_true')


def gen_test_config(bitness, compiler, host):
    testconfig_mk_path = os.path.join(THIS_DIR, 'buildcmds/testconfig.mk')
    with open(testconfig_mk_path, 'w') as test_config:
        if compiler == 'clang':
            print('LOCAL_CLANG := true', file=test_config)
        elif compiler == 'gcc':
            print('LOCAL_CLANG := false', file=test_config)

        if bitness == 32:
            print('LOCAL_MULTILIB := 32', file=test_config)
        elif bitness == 64:
            print('LOCAL_MULTILIB := 64', file=test_config)

        if compiler == 'clang':
            print('LOCAL_CXX := $(LOCAL_PATH)/buildcmdscc $(CLANG_CXX)',
                  file=test_config)
        else:
            if host:
                prefix = 'HOST_'
            else:
                prefix = 'TARGET_'
            print('LOCAL_CXX := $(LOCAL_PATH)/buildcmdscc '
                  '$($(LOCAL_2ND_ARCH_VAR_PREFIX){}CXX)'.format(prefix),
                  file=test_config)

        if host:
            print('include $(BUILD_HOST_EXECUTABLE)', file=test_config)
        else:
            print('include $(BUILD_EXECUTABLE)', file=test_config)


def mmm(path):
    makefile = os.path.join(path, 'Android.mk')
    main_mk = 'build/core/main.mk'

    env = dict(os.environ)
    env['ONE_SHOT_MAKEFILE'] = makefile
    env['LIBCXX_TESTING'] = 'true'
    cmd = ['make', '-C', ANDROID_DIR, '-f', main_mk, 'all_modules']
    subprocess.check_call(cmd, env=env)


def gen_build_cmds(bitness, compiler, host):
    gen_test_config(bitness, compiler, host)
    mmm(os.path.join(THIS_DIR, 'buildcmds'))


def main():
    args, lit_args = ArgParser().parse_known_args()
    lit_path = os.path.join(ANDROID_DIR, 'external/llvm/utils/lit/lit.py')
    gen_build_cmds(args.bitness, args.compiler, args.host)

    mode_str = 'host' if args.host else 'device'
    android_mode_arg = '--param=android_mode=' + mode_str
    test_path = os.path.join(THIS_DIR, 'test')

    lit_args = ['-sv', android_mode_arg] + lit_args
    cmd = ['python', lit_path] + lit_args + [test_path]
    sys.exit(subprocess.call(cmd))


if __name__ == '__main__':
    main()
