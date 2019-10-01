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

/* Currently unavailable in RaaS: LPC55S69_NS */
@Field mbed_os_gold_platforms = [
    'K64F', 'NUCLEO_F429ZI', 'NUCLEO_F411RE', 'NRF52840_DK',
    'DISCO_L475VG_IOT01A', 'NUCLEO_F303RE', 'LPC55S69_NS'
]

/* Currently unavailable in RaaS: EFM32GG11_STK3701, TB_SENSE_12, DISCO_F469NI,
 * NUCLEO_F412ZG, NUCLEO_L4R5ZI, NUCLEO_L496ZG */
@Field mbed_os_silver_platforms = [
    'CY8CKIT_062_WIFI_BT', 'K66F', 'UBLOX_EVK_ODIN_W2', 'NUCLEO_F746ZG',
    'UBLOX_C030_U201', 'DISCO_F746NG', 'NUCLEO_F207ZG', 'GR_LYCHEE',
    'EFM32GG11_STK3701', 'TB_SENSE_12', 'DISCO_F469NI', 'DISCO_F769NI',
    'DISCO_L496AG', 'NUCLEO_F412ZG', 'NUCLEO_L476RG', 'NUCLEO_L4R5ZI',
    'NUCLEO_L496ZG', 'NUCLEO_F767ZI'
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
