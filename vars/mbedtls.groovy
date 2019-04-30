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

@Field cmake_full_test_sh = """\
CC=%s  cmake -D CMAKE_BUILD_TYPE:String=Check .
make clean
make
make test
./programs/test/selftest -x timing
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
export LOG_FAILURE_ON_STDOUT=1
./tests/compat.sh
./tests/ssl-opt.sh
./tests/scripts/test-ref-configs.pl
"""

@Field std_make_full_config_test_sh = """\
make clean
if [ ! -f "./tests/seedfile" ]; then
    dd if=/dev/urandom of=./tests/seedfile bs=32 count=1
fi
if [ -d ./crypto -a ! -f "./crypto/tests/seedfile" ]; then
    dd if=/dev/urandom of=./crypto/tests/seedfile bs=32 count=1
fi
scripts/config.pl full
scripts/config.pl unset MBEDTLS_MEMORY_BUFFER_ALLOC_C
CC=%s make
make check
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
export LOG_FAILURE_ON_STDOUT=1
./tests/ssl-opt.sh
./tests/compat.sh
"""

@Field cmake_asan_test_sh = """\
set +e
if grep 'fno-sanitize-recover[^=]' CMakeLists.txt
then
    sed -i 's/fno-sanitize-recover/fno-sanitize-recover=undefined,integer/' CMakeLists.txt;
fi
set -e
CC=%s cmake -D CMAKE_BUILD_TYPE:String=ASan .
make
make test
./programs/test/selftest -x timing
export PATH=/usr/local/openssl-1.0.2g/bin:/usr/local/gnutls-3.4.10/bin:\$PATH
export SEED=1
export LOG_FAILURE_ON_STDOUT=1
./tests/compat.sh
./tests/ssl-opt.sh
./tests/scripts/test-ref-configs.pl
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
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12"
MSBuild ALL_BUILD.vcxproj
programs\\test\\Debug\\selftest.exe
"""

@Field win32_msvc12_64_test_bat = """\
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12 Win64"
MSBuild ALL_BUILD.vcxproj
programs\\test\\Debug\\selftest.exe
"""

@Field compiler_paths = [
    'gcc' : 'gcc',
    'gcc48' : '/usr/local/bin/gcc48',
    'clang' : 'clang',
    'cc' : 'cc'
]

@Field docker_repo = '853142832404.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls'

@Field crypto_pr = false

def gen_docker_jobs_foreach(label, platforms, compilers, script) {
    def jobs = [:]

    for (platform in platforms) {
        for (compiler in compilers) {
            def job_name = "${label}-${compiler}-${platform}"
            def docker_image_tag = "${platform}"
            def compiler_path = compiler_paths["${compiler}"]
            def shell_script = sprintf("${script}", "${compiler_path}")
            jobs[job_name] = {
                node("mbedtls && ubuntu-16.10-x64") {
                    timestamps {
                        sh 'rm -rf *'
                        deleteDir()
                        get_docker_image(platform)
                        dir('src') {
                            checkout_mbed_tls()
                            writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -x
set -v
set -e
${shell_script}
chmod -R 777 .
exit
"""
                        }
                        sh """\
chmod +x src/steps.sh
docker run --rm -u \$(id -u):\$(id -g) --entrypoint /var/lib/build/steps.sh \
-w /var/lib/build -v `pwd`/src:/var/lib/build \
-v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh $docker_repo:$docker_image_tag
"""
                    }
                }
            }
        }
    }
    return jobs
}

def gen_node_jobs_foreach(label, platforms, compilers, script) {
    def jobs = [:]

    for (platform in platforms) {
        for (compiler in compilers) {
            def job_name = "${label}-${compiler}-${platform}"
            def node_lbl = "${platform}"
            def compiler_path = compiler_paths["${compiler}"]
            def shell_script = sprintf("${script}", "${compiler_path}")
            jobs[job_name] = {
                node(node_lbl) {
                    timestamps {
                        deleteDir()
                        checkout_mbed_tls()
                        shell_script = """
export PYTHON=/usr/local/bin/python2.7
""" + shell_script
                        sh shell_script
                    }
                }
            }
        }
    }
    return jobs
}

def gen_windows_jobs(label, script) {
    def jobs = [:]

    jobs[label] = {
        node("windows-tls") {
            deleteDir()
            checkout_mbed_tls()
            bat script
        }
    }
    return jobs
}

def gen_all_sh_jobs(platform, component) {
    def jobs = [:]

    jobs["all_sh-${platform}-${component}"] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            timestamps {
                deleteDir()
                get_docker_image(platform)
                dir('src') {
                    checkout_mbed_tls()
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
git config --global user.email "you@example.com"
git config --global user.name "Your Name"
git init
git add .
git commit -m 'CI code copy'
export LOG_FAILURE_ON_STDOUT=1
set ./tests/scripts/all.sh -m --release-test --keep-going $component
"\$@"
"""
                }
                sh """\
chmod +x src/steps.sh
docker run -u \$(id -u):\$(id -g) --rm --entrypoint /var/lib/build/steps.sh \
-w /var/lib/build -v `pwd`/src:/var/lib/build \
-v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh \
--cap-add SYS_PTRACE $docker_repo:$platform
"""
            }
        }
    }
    return jobs
}

def get_docker_image(docker_image) {
    sh "\$(aws ecr get-login) && docker pull $docker_repo:$docker_image"
}

/* If testing an Mbed TLS PR, checkout the Mbed TLS PR branch.
   If testing an Mbed Crypto PR, checkout the Mbed TLS development branch
   and update the Crypto submodule to the Mbed Crypto PR branch */
def checkout_mbed_tls() {
    if (crypto_pr) {
        checkout([$class: 'GitSCM',
                  branches: [[name: 'development']],
                  doGenerateSubmoduleConfigurations: false,
                  extensions: [[$class: 'SubmoduleOption',
                                disableSubmodules: false,
                                parentCredentials: false,
                                recursiveSubmodules: true,
                                reference: '',
                                trackingSubmodules: false]],
                  submoduleCfg: [],
                  userRemoteConfigs: [[credentialsId: "${env.GIT_CREDENTIALS_ID}",
                                       url: "git@github.com:ARMmbed/mbedtls.git"]]])
        dir('crypto') {
            checkout scm
        }
    } else {
        checkout scm
    }
}

/* This runs the job using the main TLS development branch and a Mbed Crypto PR */
def run_job_with_crypto_pr() {
    crypto_pr = true
    run_job()
}

/* main job */
def run_job() {
    node {
        try {
            deleteDir()
            def one_platform = ["debian-9-x64"]
            def linux_platforms = ["debian-9-i386", "debian-9-x64"]
            def bsd_platforms = ["freebsd"]
            def bsd_compilers = ["clang"]
            def windows_platforms = ['windows']
            def windows_compilers = ['cc']
            def all_compilers = ['gcc', 'clang']
            def gcc_compilers = ['gcc']
            def asan_compilers = ['clang'] /* Change to clang once mbed TLS can compile with clang 3.8 */

            /* Linux jobs */
            def jobs = gen_docker_jobs_foreach(
                'std-make', linux_platforms, all_compilers, std_make_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'std-make-full-config', linux_platforms, all_compilers,
                std_make_full_config_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'cmake', linux_platforms, all_compilers, cmake_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'cmake-full', linux_platforms, gcc_compilers, cmake_full_test_sh
            )
            jobs = jobs + gen_docker_jobs_foreach(
                'cmake-asan', linux_platforms, asan_compilers, cmake_asan_test_sh
            )

            /* BSD jobs */
            jobs = jobs + gen_node_jobs_foreach(
                'gmake', bsd_platforms, bsd_compilers, gmake_test_sh
            )
            jobs = jobs + gen_node_jobs_foreach(
                'cmake', bsd_platforms, bsd_compilers, cmake_test_sh
            )

            /* Windows jobs */
            jobs = jobs + gen_windows_jobs('win32-mingw', win32_mingw_test_bat)
            jobs = jobs + gen_windows_jobs(
                'win32_msvc12_32', win32_msvc12_32_test_bat
            )
            jobs = jobs + gen_windows_jobs(
                'win32-msvc12_64', win32_msvc12_64_test_bat
            )
            jobs = jobs + gen_windows_jobs('iar8-mingw', iar8_mingw_test_bat)

            /* All.sh jobs */
            dir('mbedtls') {
                deleteDir()
                checkout_mbed_tls()
                all_sh_help = sh(
                    script: "./tests/scripts/all.sh --help",
                    returnStdout: true
                )
                if (all_sh_help.contains('list-components')) {
                    components = sh(
                        script: "./tests/scripts/all.sh --list-components",
                        returnStdout: true
                    ).trim().split('\n')
                    for (component in components) {
                        jobs = jobs + gen_all_sh_jobs(
                            'ubuntu-16.04', component
                        )
                    }
                    jobs = jobs + gen_all_sh_jobs('ubuntu-18.04', 'build_mingw')
                }
            }

            jobs.failFast = false
            parallel jobs
        } catch (err) {
            echo "Caught: ${err}"
            currentBuild.result = 'FAILURE'
        }
    }
}
