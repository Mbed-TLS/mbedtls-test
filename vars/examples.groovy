import groovy.transform.Field

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
    'getting-started': [
        'should_run': env.TEST_MBED_OS_CRYPTO_EXAMPLES,
        'repo': env.MBED_OS_CRYPTO_EXAMPLES_REPO,
        'branch': env.MBED_OS_CRYPTO_EXAMPLES_BRANCH,
        'platforms': platforms_with_entropy_sources,
        /* ARM removed temporarily due to a compilation issue, this is being
         * tracked in https://jira.arm.com/browse/IOTCRYPT-920 */
        'compilers': ['GCC_ARM', 'IAR']],
    'atecc608a': [
        'should_run': env.TEST_MBED_OS_ATECC608A_EXAMPLES,
        'repo': env.MBED_OS_ATECC608A_EXAMPLES_REPO,
        'branch': env.MBED_OS_ATECC608A_EXAMPLES_BRANCH,
        'platforms': ['K64F'],
        'compilers': ['GCC_ARM']],
]
