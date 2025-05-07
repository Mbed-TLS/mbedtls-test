/*
 *  Copyright (c) 2019-2021, Arm Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)
 */

import groovy.transform.Field

@Field static final String win32_mingw_test_bat = '''\
perl --version
python --version
cmake --version
mingw32-make --version
set CC=gcc
if exist scripts\\make_generated_files.bat call scripts\\make_generated_files.bat || exit
cmake . -G "MinGW Makefiles" || exit
mingw32-make || exit
mingw32-make test || exit
ctest -VV || exit
programs\\test\\selftest.exe || exit
'''

@Field static final String iar8_mingw_test_bat = '''\
perl --version
python --version
cmake --version
iccarm --version
set CC=iccarm
if exist scripts\\make_generated_files.bat call scripts\\make_generated_files.bat || exit
perl scripts/config.pl baremetal || exit
cmake -D CMAKE_BUILD_TYPE:String=Check -G "MinGW Makefiles" . || exit
mingw32-make lib || exit
'''

@Field static final String win32_msvc15_32_test_bat = '''\
perl --version
python --version
cmake --version
set VSCMD_START_DIR=%cd%
call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvarsall.bat" x86 || exit
set CC=cl
if exist scripts\\make_generated_files.bat call scripts\\make_generated_files.bat || exit
cmake . -G "Visual Studio 15 2017" -A Win32 || exit
MSBuild ALL_BUILD.vcxproj || exit
programs\\test\\Debug\\selftest.exe || exit
'''

@Field static final String win32_msvc15_64_test_bat = '''\
perl --version
python --version
cmake --version
set VSCMD_START_DIR=%cd%
call "C:\\Program Files (x86)\\Microsoft Visual Studio\\2017\\Community\\VC\\Auxiliary\\Build\\vcvarsall.bat" x64 || exit
set CC=cl
if exist scripts\\make_generated_files.bat call scripts\\make_generated_files.bat || exit
cmake . -G "Visual Studio 15 2017" -A x64 || exit
MSBuild ALL_BUILD.vcxproj || exit
programs\\test\\Debug\\selftest.exe || exit
'''
