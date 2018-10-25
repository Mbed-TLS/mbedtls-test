import groovy.transform.Field

@Field std_make_test_sh = """make clean
CC=%s make
make check
./programs/test/selftest
"""

@Field gmake_test_sh = """gmake clean
CC=%s gmake
gmake check
./programs/test/selftest
"""

@Field cmake_test_sh = """CC=%s  cmake -D CMAKE_BUILD_TYPE:String=Check .
make clean
make
make test
./programs/test/selftest
"""

@Field cmake_full_test_sh = cmake_test_sh + """
openssl version
gnutls-serv -v
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
./tests/compat.sh
find . -name c-srv-1.log|xargs cat 
./tests/ssl-opt.sh
./tests/scripts/test-ref-configs.pl
"""

@Field cmake_asan_test_sh = """
set +e
if grep 'fno-sanitize-recover[^=]' CMakeLists.txt
then
    sed -i 's/fno-sanitize-recover/fno-sanitize-recover=undefined,integer/' CMakeLists.txt;
fi
set -e
CC=%s cmake -D CMAKE_BUILD_TYPE:String=ASan .
make
make test
./programs/test/selftest
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
export LOG_FAILURE_ON_STDOUT=1
./tests/compat.sh
./tests/ssl-opt.sh
./tests/scripts/test-ref-configs.pl
"""

@Field win32_mingw_test_bat = """
cmake . -G "MinGW Makefiles" -DCMAKE_C_COMPILER="gcc"
mingw32-make
ctest -VV
"""

@Field iar8_mingw_test_bat = """
perl scripts/config.pl baremetal
cmake -D CMAKE_BUILD_TYPE:String=Check -DCMAKE_C_COMPILER="iccarm" -G "MinGW Makefiles" .
mingw32-make lib
"""

@Field win32_msvc12_32_test_bat = """
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12"
MSBuild ALL_BUILD.vcxproj
"""

@Field win32_msvc12_64_test_bat = """
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12 Win64"
MSBuild ALL_BUILD.vcxproj
"""

@Field compiler_paths = [
    'gcc' : 'gcc',
    'gcc48' : '/usr/local/bin/gcc48',
    'clang' : 'clang',
    'cc' : 'cc'
]

def gen_jobs_foreach ( label, platforms, compilers, script ){
    def jobs = [:]

    for ( platform in platforms ){
        for ( compiler in compilers ){
            def job_name = "${label}-${compiler}-${platform}"
            def docker_image_tag = "${platform}"
            def compiler_path = compiler_paths["${compiler}"]
            def shell_script = sprintf( "${script}", "${compiler_path}" )
            jobs[job_name] = {
                node( "mbedtls && ubuntu-16.10-x64" ){
                    timestamps {
                        sh 'rm -rf *'
                        deleteDir()
                        dir('src'){
                            checkout scm
                            writeFile file: 'steps.sh', text: """#!/bin/sh
set -x
set -v
set -e
${shell_script}
chmod -R 777 .
exit
"""
                        }
                        sh """
chmod +x src/steps.sh
docker run --rm -u \$(id -u):\$(id -g) --entrypoint /var/lib/build/steps.sh -w /var/lib/build -v `pwd`/src:/var/lib/build -v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh ${docker_image_tag}
"""
                    }
                }
            }
        }
    }
    return jobs
}

def gen_freebsd_jobs_foreach ( label, platforms, compilers, script ){
    def jobs = [:]

    for ( platform in platforms ){
        for ( compiler in compilers ){
            def job_name = "${label}-${compiler}-${platform}"
            def node_lbl = "${platform}"
            def compiler_path = compiler_paths["${compiler}"]
            def shell_script = sprintf( "${script}", "${compiler_path}" )
            jobs[job_name] = {
                node( node_lbl ){
                    timestamps {
                        deleteDir()
                        checkout scm
                        sh shell_script
                    }
                }
            }
        }
    }
    return jobs
}

def gen_windows_jobs ( label, script ) {
    def jobs = [:]

    jobs[label] = {
        node ("windows-tls") {
            deleteDir()
            checkout scm
            bat script
        }
    }
    return jobs
}

/* main job */
def run_job(){
    node {
        try {
            deleteDir()
            def linux_platforms = [ "debian-i386", "debian-x64" ]
            def bsd_platforms = [ "freebsd" ]
            def bsd_compilers = [ "clang" ]
            def windows_platforms = ['windows']
            def windows_compilers = ['cc']
            def all_compilers = ['gcc', 'clang']
            def gcc_compilers = ['gcc']
            def asan_compilers = ['clang'] /* Change to clang once mbed TLS can compile with clang 3.8 */

            /* Linux jobs */
            def jobs = gen_jobs_foreach( 'std-make', linux_platforms, all_compilers, std_make_test_sh )
            jobs = jobs + gen_jobs_foreach( 'cmake', linux_platforms, all_compilers, cmake_test_sh )
            jobs = jobs + gen_jobs_foreach( 'cmake-full', linux_platforms, gcc_compilers, cmake_full_test_sh )
            jobs = jobs + gen_jobs_foreach( 'cmake-asan', linux_platforms, asan_compilers, cmake_asan_test_sh )
            jobs['doxygen'] = {
                node ("mbedtls && ubuntu-16.10-x64") {
                deleteDir()
                dir('src') {
                    checkout scm
                    sh './tests/scripts/doxygen.sh'
                    }
                }
            }
        
            /* BSD jobs */
            jobs = jobs + gen_freebsd_jobs_foreach( 'gmake', bsd_platforms, bsd_compilers, gmake_test_sh )
            jobs = jobs + gen_freebsd_jobs_foreach( 'cmake', bsd_platforms, bsd_compilers, cmake_test_sh )
        
            /* Windows jobs */
            jobs = jobs + gen_windows_jobs( 'win32-mingw', win32_mingw_test_bat )
            jobs = jobs + gen_windows_jobs( 'win32_msvc12_32-mingw', win32_msvc12_32_test_bat )
            jobs = jobs + gen_windows_jobs( 'win32-win32_msvc12_64', win32_msvc12_64_test_bat )
            jobs = jobs + gen_windows_jobs( 'iar8-mingw', iar8_mingw_test_bat )

            jobs.failFast = false
            parallel jobs
        } catch ( err ) {
            throw( err );
        }
    }
}
