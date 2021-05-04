import groovy.transform.Field

@Field std_make_test_sh = """\
make clean
CC=%s make
make check
./programs/test/selftest -x timing
"""

@Field gmake_test_sh = """\
gmake clean
CC=%s gmake
gmake check
./programs/test/selftest -x timing
"""

@Field cmake_test_sh = """\
CC=%s  cmake -D CMAKE_BUILD_TYPE:String=Check .
make clean
make
make test
./programs/test/selftest -x timing
"""

@Field win32_mingw_test_bat = """\
if exist scripts\\make_generated_files.bat scripts\\make_generated_files.bat || exit /b
cmake . -G "MinGW Makefiles" -DCMAKE_C_COMPILER="gcc" || exit /b
mingw32-make || exit /b
mingw32-make test || exit /b
ctest -VV || exit /b
programs\\test\\selftest.exe || exit /b
"""

@Field iar8_mingw_test_bat = """\
if exist scripts\\make_generated_files.bat scripts\\make_generated_files.bat || exit /b
perl scripts/config.pl baremetal || exit /b
cmake -D CMAKE_BUILD_TYPE:String=Check -DCMAKE_C_COMPILER="iccarm" -G "MinGW Makefiles" . || exit /b
mingw32-make lib || exit /b
"""

@Field win32_msvc12_32_test_bat = """\
if exist scripts\\make_generated_files.bat scripts\\make_generated_files.bat || exit /b
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat" || exit /b
cmake . -G "Visual Studio 12" || exit /b
MSBuild ALL_BUILD.vcxproj || exit /b
programs\\test\\Debug\\selftest.exe || exit /b
"""

@Field win32_msvc12_64_test_bat = """\
if exist scripts\\make_generated_files.bat scripts\\make_generated_files.bat || exit /b
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat" || exit /b
cmake . -G "Visual Studio 12 Win64" || exit /b
MSBuild ALL_BUILD.vcxproj || exit /b
programs\\test\\Debug\\selftest.exe || exit /b
"""

@Field cmake_full_test_sh = """\
CC=%s  cmake -D CMAKE_BUILD_TYPE:String=Check .
make clean
make
make test
./programs/test/selftest -x timing
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
if [ -f "./tests/compat.sh" ]; then
    ./tests/compat.sh
    ./tests/ssl-opt.sh
fi
./tests/scripts/test-ref-configs.pl
"""

@Field std_make_full_config_test_sh = """\
make clean
if [ ! -f "./tests/seedfile" ]; then
    dd if=/dev/urandom of=./tests/seedfile bs=64 count=1
fi
if [ -d ./crypto/tests -a ! -f "./crypto/tests/seedfile" ]; then
    cp tests/seedfile crypto/tests/seedfile
fi
scripts/config.pl full
scripts/config.pl unset MBEDTLS_MEMORY_BUFFER_ALLOC_C
CC=%s make
make check
if [ -f "./tests/compat.sh" ]; then
    export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
    export SEED=1
    ./tests/ssl-opt.sh
    ./tests/compat.sh
fi
"""

@Field cmake_asan_test_sh = """\
set +e
if grep 'fno-sanitize-recover[^=]' CMakeLists.txt
then
    sed -i 's/fno-sanitize-recover/fno-sanitize-recover=undefined,integer/' CMakeLists.txt;
fi
CC=%s cmake -D CMAKE_BUILD_TYPE:String=ASan .
make
make test
./programs/test/selftest -x timing
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
if [ -f "./tests/compat.sh" ]; then
    ./tests/compat.sh
    ./tests/ssl-opt.sh
fi
./tests/scripts/test-ref-configs.pl
"""
