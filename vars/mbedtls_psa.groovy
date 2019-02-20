import groovy.transform.Field

@Field basic_test_sh = """\
make clean
./tests/scripts/recursion.pl library/*.c
./tests/scripts/check-generated-files.sh
./tests/scripts/check-doxy-blocks.pl
./tests/scripts/check-names.sh
./tests/scripts/check-files.py
./tests/scripts/doxygen.sh
"""

@Field std_make_test_sh = """\
make clean
CC=%s make
make check
./programs/test/selftest
"""

@Field gmake_test_sh = """\
gmake clean
CC=%s gmake
gmake check
./programs/test/selftest
"""

@Field cmake_test_sh = """\
CC=%s  cmake -D CMAKE_BUILD_TYPE:String=Check .
make clean
make
make test
./programs/test/selftest
"""

@Field cmake_full_test_sh = cmake_test_sh + """\
export SEED=1
export LOG_FAILURE_ON_STDOUT=1
./tests/scripts/test-ref-configs.pl
"""

@Field std_make_full_config_test_sh = """\
make clean
scripts/config.pl full
scripts/config.pl unset MBEDTLS_MEMORY_BUFFER_ALLOC_C
CC=%s make
make check
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
./programs/test/selftest
export SEED=1
export LOG_FAILURE_ON_STDOUT=1
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
if exist scripts\\generate_psa_constants.py scripts\\generate_psa_constants.py
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12"
MSBuild ALL_BUILD.vcxproj
programs\\test\\Debug\\selftest.exe
"""

@Field win32_msvc12_64_test_bat = """\
if exist scripts\\generate_psa_constants.py scripts\\generate_psa_constants.py
call "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\vcvarsall.bat"
cmake . -G "Visual Studio 12 Win64"
MSBuild ALL_BUILD.vcxproj
programs\\test\\Debug\\selftest.exe
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

@Field compiler_paths = [
    'gcc' : 'gcc',
    'gcc48' : '/usr/local/bin/gcc48',
    'clang' : 'clang',
    'cc' : 'cc'
]

@Field docker_repo = '853142832404.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls'

@Field all_sh_components = []

@Field scm_vars

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
                            checkout scm
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
                        checkout scm
                        if (label == 'coverity') {
                            checkout_coverity_repo()
                        }
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

def gen_simple_windows_jobs(label, script) {
    def jobs = [:]

    jobs[label] = {
        node("windows-tls") {
            deleteDir()
            checkout scm
            bat script
        }
    }
    return jobs
}

def gen_windows_tests_jobs(build) {
    def jobs = [:]

    jobs["Windows-${build}"] = {
        node("windows-tls") {
            dir("mbed-crypto") {
                deleteDir()
                checkout scm
            }
            /* The empty files are created to re-create the directory after it
             * and its contents have been removed by deleteDir. */
            dir("logs") {
                deleteDir()
                writeFile file:'_do_not_delete_this_directory.txt', text:''
            }

            dir("worktrees") {
                deleteDir()
                writeFile file:'_do_not_delete_this_directory.txt', text:''
            }
            /* libraryResource loads the file as a string. This is then
             * written to a file so that it can be run on a node. */
            def windows_testing = libraryResource 'windows/windows_testing.py'
            writeFile file: 'windows_testing.py', text: windows_testing
            bat "python windows_testing.py mbed-crypto logs $scm_vars.GIT_COMMIT -b $build"
        }
    }
    return jobs
}

def gen_all_sh_jobs(platform, component) {
    def jobs = [:]

    jobs["all_sh-${component}"] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            timestamps {
                deleteDir()
                get_docker_image(platform)
                dir('src') {
                    checkout scm
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -eux
git config --global user.email "you@example.com"
git config --global user.name "Your Name"
git init
git add .
git commit -m 'CI code copy'
export LOG_FAILURE_ON_STDOUT=1
set ./tests/scripts/all.sh --seed 4 --keep-going $component
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

def gen_abi_api_checking_job(platform) {
    def jobs = [:]

    jobs["ABI/API checking"] = {
        node('ubuntu-16.10-x64 && mbedtls') {
            timestamps {
                deleteDir()
                get_docker_image(platform)
                dir('src') {
                    checkout scm
                    sh(
                        returnStdout: true,
                        script: "git fetch origin ${CHANGE_TARGET}"
                    ).trim()
                    writeFile file: 'steps.sh', text: """\
#!/bin/sh
set -x
set -v
set -e
tests/scripts/list-identifiers.sh --internal
scripts/abi_check.py -o FETCH_HEAD -n HEAD -s identifiers --brief
exit
"""
                }
                sh """\
chmod +x src/steps.sh
docker run --rm -u \$(id -u):\$(id -g) --entrypoint /var/lib/build/steps.sh \
-w /var/lib/build -v `pwd`/src:/var/lib/build \
-v /home/ubuntu/.ssh:/home/mbedjenkins/.ssh $docker_repo:$platform
"""
            }
        }
    }
    return jobs
}

def checkout_coverity_repo() {
    checkout changelog: false, poll: false,
        scm: [
            $class: 'GitSCM',
            branches: [[name: '*/master']],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
                [$class: 'CloneOption', noTags: true, shallow: true],
                [$class: 'RelativeTargetDirectory', relativeTargetDir: 'coverity-tools']
                ],
            submoduleCfg: [],
            userRemoteConfigs: [[
                url: 'git@github.com:ARMmbed/coverity-tools.git',
                credentialsId: "${env.GIT_CREDENTIALS_ID}"]]]
}

def get_docker_image(docker_image) {
    sh "\$(aws ecr get-login) && docker pull $docker_repo:$docker_image"
}

/* main job */
def run_job() {
    githubNotify context: 'Pre Test Checks',
                 description: 'Checking if all PR tests can be run',
                 status: 'PENDING'
    githubNotify context: 'Crypto Testing',
                 description: 'Not started',
                 status: 'PENDING'
    githubNotify context: 'TLS Testing',
                 description: 'Not started',
                 status: 'PENDING'
    stage('pre-test-checks') {
        node {
            try {
                /* Get components of all.sh */
                dir('mbedtls') {
                    deleteDir()
                    scm_vars = checkout scm
                    all_sh_help = sh(
                        script: "./tests/scripts/all.sh --help",
                        returnStdout: true
                    )
                    if (all_sh_help.contains('list-components')) {
                        all_sh_components = sh(
                            script: "./tests/scripts/all.sh --list-components",
                            returnStdout: true
                        ).trim().split('\n')
                    } else {
                        def message = 'Base branch out of date. Please rebase'
                        githubNotify context: 'Pre Test Checks',
                                     description: message,
                                     status: 'FAILURE'
                        githubNotify context: 'Crypto Testing',
                                     description: 'Not run',
                                     status: 'FAILURE'
                        githubNotify context: 'TLS Testing',
                                     description: 'Not run',
                                     status: 'FAILURE'
                        error(message)
                    }
                }

                githubNotify context: 'Pre Test Checks',
                             description: 'OK',
                             status: 'SUCCESS'
            } catch (err) {
                throw (err)
            }
        }
    }
    stage('crypto-testing') {
        node {
            try {
                githubNotify context: 'Crypto Testing',
                             description: 'In progress',
                             status: 'PENDING'
                deleteDir()
                def one_platform = ["debian-9-x64"]
                def linux_platforms = ["debian-9-i386", "debian-9-x64"]
                def bsd_platforms = ["freebsd"]
                def bsd_compilers = ["clang"]
                def windows_platforms = ['windows']
                def coverity_platforms = ['coverity && gcc']
                def windows_compilers = ['cc']
                def all_compilers = ['gcc', 'clang']
                def gcc_compilers = ['gcc']
                def asan_compilers = ['clang'] /* Change to clang once mbed TLS can compile with clang 3.8 */
                def coverity_compilers = ['gcc']

                /* Linux jobs */
                def jobs = gen_docker_jobs_foreach(
                    'basic', one_platform, gcc_compilers, basic_test_sh
                )
                jobs = jobs + gen_docker_jobs_foreach(
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
                jobs = jobs + gen_simple_windows_jobs(
                    'win32-mingw', win32_mingw_test_bat
                )
                jobs = jobs + gen_simple_windows_jobs(
                    'win32_msvc12_32', win32_msvc12_32_test_bat
                )
                jobs = jobs + gen_simple_windows_jobs(
                    'win32-msvc12_64', win32_msvc12_64_test_bat
                )
                jobs = jobs + gen_simple_windows_jobs(
                    'iar8-mingw', iar8_mingw_test_bat
                )
                for (build in ['mingw', '2013']) {
                    jobs = jobs + gen_windows_tests_jobs(build)
                }

                /* Coverity jobs */
                jobs = jobs + gen_node_jobs_foreach(
                    'coverity', coverity_platforms, coverity_compilers, std_coverity_sh
                )

                /* All.sh jobs */
                for (component in all_sh_components) {
                    jobs = jobs + gen_all_sh_jobs('ubuntu-16.04', component)
                }

                jobs = jobs + gen_abi_api_checking_job('ubuntu-16.04')

                jobs.failFast = false
                parallel jobs
                githubNotify context: 'Crypto Testing',
                             description: 'All tests passed',
                             status: 'SUCCESS'
            } catch (err) {
                echo "Caught: ${err}"
                currentBuild.result = 'FAILURE'
                githubNotify context: 'Crypto Testing',
                             description: 'Test failure',
                             status: 'FAILURE'
            }
        }
    }
    stage('tls-testing') {
        try {
            githubNotify context: 'TLS Testing',
                         description: 'In progress',
                         status: 'PENDING'
            mbedtls.run_job_with_crypto_pr()
            githubNotify context: 'TLS Testing',
                         description: 'All tests passed',
                         status: 'SUCCESS'
        } catch (err) {
            githubNotify context: 'TLS Testing',
                         description: 'Test failure',
                         status: 'FAILURE'
        }
    }
}
