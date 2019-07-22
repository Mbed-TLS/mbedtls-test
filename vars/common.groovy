import groovy.transform.Field

/*
 * This controls the timeout each job has. It does not count the time spent in
 * waiting queues and setting up the environment.
 *
 * Raas has its own resource queue with the timeout of 1000s, we need to take
 * it into account for the on-target test jobs.
 */
@Field perJobTimeout = [time: 45, raasOffset: 17, unit: 'MINUTES']

@Field compiler_paths = [
    'gcc' : 'gcc',
    'gcc48' : '/usr/local/bin/gcc48',
    'clang' : 'clang',
    'cc' : 'cc'
]

@Field compilers = ['ARM', 'GCC_ARM', 'IAR']

@Field all_platforms = [
    'K64F', 'NUCLEO_F429ZI', 'UBLOX_EVK_ODIN_W2', 'NUCLEO_F746ZG',
    'CY8CKIT_062_WIFI_BT', 'NUCLEO_F411RE'
]

@Field platforms_with_entropy_sources = [
    'K64F', 'NUCLEO_F429ZI', 'UBLOX_EVK_ODIN_W2', 'NUCLEO_F746ZG',
    'CY8CKIT_062_WIFI_BT'
]

@Field platforms_with_ethernet = [
    'K64F', 'NUCLEO_F429ZI', 'UBLOX_EVK_ODIN_W2', 'NUCLEO_F746ZG'
]

@Field examples = [
    'authcrypt': [
        'should_run': env.TEST_MBED_OS_AUTHCRYPT_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': platforms_with_entropy_sources,
        'compilers': compilers],
    'benchmark': [
        'should_run': env.TEST_MBED_OS_BENCHMARK_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': platforms_with_entropy_sources,
        'compilers': compilers],
    'hashing': [
        'should_run': env.TEST_MBED_OS_HASHING_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': all_platforms,
        'compilers': compilers],
    'tls-client': [
        'should_run': env.TEST_MBED_OS_TLS_CLIENT_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': platforms_with_ethernet,
        'compilers': compilers],
    'mbed-crypto': [
        'should_run': env.TEST_MBED_OS_CRYPTO_EXAMPLES,
        'repo': env.MBED_OS_CRYPTO_EXAMPLES_REPO,
        'branch': env.MBED_OS_CRYPTO_EXAMPLES_BRANCH,
        'platforms': platforms_with_entropy_sources,
        'compilers': compilers],
    'atecc608a': [
        'should_run': env.TEST_MBED_OS_ATECC608A_EXAMPLES,
        'repo': env.MBED_OS_ATECC608A_EXAMPLES_REPO,
        'branch': env.MBED_OS_ATECC608A_EXAMPLES_BRANCH,
        'platforms': ['K64F'],
        'compilers': ['GCC_ARM']],
]

@Field docker_repo = '853142832404.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls'

@Field one_platform = ["debian-9-x64"]
@Field linux_platforms = ["debian-9-i386", "debian-9-x64"]
@Field bsd_platforms = ["freebsd"]
@Field bsd_compilers = ["clang"]
@Field coverity_platforms = ['coverity && gcc']
@Field all_compilers = ['gcc', 'clang']
@Field gcc_compilers = ['gcc']
@Field asan_compilers = ['clang']
@Field coverity_compilers = ['gcc']

def get_docker_image(docker_image) {
    sh "\$(aws ecr get-login) && docker pull $docker_repo:$docker_image"
}

def get_all_sh_components() {
    node {
        /* Get components of all.sh */
        dir('src') {
            deleteDir()
            checkout_repo.checkout_repo()
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
                error('Base branch out of date. Please rebase')
            }
        }
        return all_sh_components
    }
}
