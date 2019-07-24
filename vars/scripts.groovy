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
cmake . -G "MinGW Makefiles" -DCMAKE_C_COMPILER="gcc"
mingw32-make
mingw32-make test
ctest -VV
programs\\test\\selftest.exe
"""

@Field iar8_mingw_test_bat = """\
perl scripts/config.pl baremetal
cmake -D CMAKE_BUILD_TYPE:String=Check -DCMAKE_C_COMPILER="iccarm" -G "MinGW Makefiles" .
mingw32-make lib
"""

@Field win32_msvc12_32_test_bat = """\
if exist scripts\\generate_psa_constants.py \
    scripts\\generate_psa_constants.py
if exist crypto\\scripts\\generate_psa_constants.py \
    crypto\\scripts\\generate_psa_constants.py
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12"
MSBuild ALL_BUILD.vcxproj
programs\\test\\Debug\\selftest.exe
"""

@Field win32_msvc12_64_test_bat = """\
if exist scripts\\generate_psa_constants.py \
    scripts\\generate_psa_constants.py
if exist crypto\\scripts\\generate_psa_constants.py \
    crypto\\scripts\\generate_psa_constants.py
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12 Win64"
MSBuild ALL_BUILD.vcxproj
programs\\test\\Debug\\selftest.exe
"""

@Field cmake_full_test_sh = """\
CC=%s  cmake -D CMAKE_BUILD_TYPE:String=Check .
make clean
make
make test
./programs/test/selftest -x timing
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
export LOG_FAILURE_ON_STDOUT=1
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
if [ -d ./crypto -a ! -f "./crypto/tests/seedfile" ]; then
    cp tests/seedfile crypto/tests/seedfile
fi
scripts/config.pl full
scripts/config.pl unset MBEDTLS_MEMORY_BUFFER_ALLOC_C
CC=%s make
make check
if [ -f "./tests/compat.sh" ]; then
    export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
    export SEED=1
    export LOG_FAILURE_ON_STDOUT=1
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
export LOG_FAILURE_ON_STDOUT=1
if [ -f "./tests/compat.sh" ]; then
    ./tests/compat.sh
    ./tests/ssl-opt.sh
fi
./tests/scripts/test-ref-configs.pl
"""

@Field std_coverity_sh = """\
python coverity-tools/coverity_build.py \
    --dir coverity \
    -v \
    --skip-html-report \
    -i '.*' \
    --aggressiveness-level high \
    --do-not-fail-on-issues \
    -c make programs CC=%s

tar -zcvf coverity-PSA-Crypto-Coverity.tar.gz coverity
aws s3 cp coverity-PSA-Crypto-Coverity.tar.gz s3://coverity-reports
"""
