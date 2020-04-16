import groovy.transform.Field

@Field compilers = ['ARM', 'GCC_ARM']

@Field platforms_without_entropy_sources = [
    'NUCLEO_F411RE', 'NUCLEO_F303RE', 'GR_LYCHEE'
]

@Field platforms_without_ethernet = [
    'NUCLEO_F411RE', 'CY8CKIT_062_WIFI_BT', 'NRF52840_DK',
    'DISCO_L475VG_IOT01A', 'NUCLEO_F303RE', 'LPC55S69_NS',
    'GR_LYCHEE', 'TB_SENSE_12', 'DISCO_F469NI', 'DISCO_L496AG',
    'NUCLEO_F412ZG', 'NUCLEO_L476RG', 'NUCLEO_L4R5ZI', 'NUCLEO_L496ZG',
]

/* This is a minimal set of boards with the following criteria:
 * one v7-M with TRNG, one v7-M without TRNG,
 * one dual CPU PSA platform, one v8-M PSA platform
 * Currently unavailable in RaaS: LPC55S69_NS */
@Field pull_request_platforms = [
    'K64F', 'NUCLEO_F411RE', 'CY8CKIT_062_WIFI_BT', 'LPC55S69_NS',
]

/* From https://confluence.arm.com/display/IoTBU/ISG+Device+SW+SUT+list
 * Currently unavailable in RaaS: LPC55S69_NS */
@Field mbed_os_gold_platforms = [
    'K64F', 'NUCLEO_F429ZI', 'NUCLEO_F411RE', 'NRF52840_DK',
    'DISCO_L475VG_IOT01A', 'NUCLEO_F303RE', 'LPC55S69_NS'
]

/* From https://confluence.arm.com/display/IoTBU/ISG+Device+SW+SUT+list
 * Currently unavailable in RaaS: EFM32GG11_STK3701, TB_SENSE_12, DISCO_F469NI,
 * NUCLEO_F412ZG, NUCLEO_L4R5ZI, NUCLEO_L496ZG */
@Field mbed_os_silver_platforms = [
    'CY8CKIT_062_WIFI_BT', 'K66F', 'UBLOX_EVK_ODIN_W2', 'NUCLEO_F746ZG',
    'UBLOX_C030_U201', 'DISCO_F746NG', 'NUCLEO_F207ZG', 'GR_LYCHEE',
    'EFM32GG11_STK3701', 'TB_SENSE_12', 'DISCO_F469NI', 'DISCO_F769NI',
    'DISCO_L496AG', 'NUCLEO_F412ZG', 'NUCLEO_L476RG', 'NUCLEO_L4R5ZI',
    'NUCLEO_L496ZG', 'NUCLEO_F767ZI'
]

@Field raas_for_platform = [
    'K64F':'auli',
    'NUCLEO_F429ZI':'auli',
    'NUCLEO_F411RE':'auli',
    'NRF52840_DK':'auli',
    'DISCO_L475VG_IOT01A':'auli',
    'NUCLEO_F303RE':'auli',
    'LPC55S69_NS':null,
    'CY8CKIT_062_WIFI_BT':'auli',
    'K66F':'auli',
    'UBLOX_EVK_ODIN_W2':'auli',
    'NUCLEO_F746ZG':'auli',
    'UBLOX_C030_U201':'hanna',
    'DISCO_F746NG':'hanna',
    'NUCLEO_F207ZG':'ruka',
    'GR_LYCHEE':'hanna',
    'EFM32GG11_STK3701':null,
    'TB_SENSE_12':null,
    'DISCO_F469NI':null,
    'DISCO_F769NI':'kaisa',
    'DISCO_L496AG':'hanna',
    'NUCLEO_F412ZG':null,
    'NUCLEO_L476RG':'auli',
    'NUCLEO_L4R5ZI':null,
    'NUCLEO_L496ZG':null,
    'NUCLEO_F767ZI':'auli',
]

@Field examples = [
    'authcrypt': [
        'should_run': env.TEST_MBED_OS_AUTHCRYPT_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': {platforms_with_entropy_sources()},
        'compilers': compilers],
    'benchmark': [
        'should_run': env.TEST_MBED_OS_BENCHMARK_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': {platforms_with_entropy_sources()},
        'compilers': compilers],
    'hashing': [
        'should_run': env.TEST_MBED_OS_HASHING_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': {platforms_to_test()},
        'compilers': compilers],
    'tls-client': [
        'should_run': env.TEST_MBED_OS_TLS_CLIENT_EXAMPLE,
        'repo': env.MBED_OS_TLS_EXAMPLES_REPO,
        'branch': env.MBED_OS_TLS_EXAMPLES_BRANCH,
        'platforms': {platforms_with_ethernet()},
        'compilers': compilers],
    'getting-started': [
        'should_run': env.TEST_MBED_OS_CRYPTO_EXAMPLES,
        'repo': env.MBED_OS_CRYPTO_EXAMPLES_REPO,
        'branch': env.MBED_OS_CRYPTO_EXAMPLES_BRANCH,
        'platforms': {platforms_with_entropy_sources()},
        'compilers': compilers],
    'atecc608a': [
        'should_run': env.TEST_MBED_OS_ATECC608A_EXAMPLES,
        'repo': env.MBED_OS_ATECC608A_EXAMPLES_REPO,
        'branch': env.MBED_OS_ATECC608A_EXAMPLES_BRANCH,
        'platforms': {['K64F']},
        'compilers': compilers],
]

def platforms_to_test() {
    if (env.JOB_TYPE == 'PR') {
        return pull_request_platforms
    }
    switch (env.PLATFORMS_TO_TEST) {
        case 'Pull Request':
            return pull_request_platforms
        case 'Mbed OS Gold Boards':
            return mbed_os_gold_platforms
        case 'Mbed OS Silver Boards':
            return mbed_os_silver_platforms
        case 'Mbed OS Gold Boards + Mbed OS Silver Boards':
            return (mbed_os_gold_platforms + mbed_os_silver_platforms)
        default:
            throw new Exception("No example list provided")
    }
}

def platforms_with_entropy_sources() {
    return (platforms_to_test() - platforms_without_entropy_sources)
}

def platforms_with_ethernet() {
    return (platforms_to_test() - platforms_without_ethernet)
}
