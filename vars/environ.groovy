def set_crypto_pr_environment(is_production) {
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = 'crypto'
    env.REPO_TO_CHECKOUT = 'crypto'
    if (is_production) {
        set_common_pr_production_environment()
        set_crypto_pr_production_environment()
    } else {
        env.CHECKOUT_METHOD = 'parametrized'
    }
}

def set_tls_pr_environment(is_production) {
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = 'tls'
    env.REPO_TO_CHECKOUT = 'tls'
    if (is_production) {
        set_common_pr_production_environment()
        set_tls_pr_production_environment()
    } else {
        env.CHECKOUT_METHOD = 'parametrized'
    }
}

def set_common_pr_production_environment() {
    env.CHECKOUT_METHOD = 'scm'
    env.RUN_LINUX_SCRIPTS = 'true'
    env.RUN_FREEBSD = 'true'
    env.RUN_WINDOWS_TEST = 'true'
    env.RUN_ALL_SH = 'true'
    env.RUN_ABI_CHECK = 'true'
    env.MBED_OS_REPO = 'git@github.com:ARMmbed/mbed-os.git'
    env.MBED_OS_BRANCH = 'master'
    env.MBED_OS_TLS_EXAMPLES_REPO = 'git@github.com:ARMmbed/mbed-os-example-tls.git'
    env.MBED_OS_TLS_EXAMPLES_BRANCH = 'master'
    if (['development', 'development-restricted', 'feature-psa'].contains(CHANGE_TARGET)) {
        env.TEST_MBED_OS_AUTHCRYPT_EXAMPLE = 'true'
        env.TEST_MBED_OS_BENCHMARK_EXAMPLE = 'true'
        env.TEST_MBED_OS_HASHING_EXAMPLE = 'true'
        env.TEST_MBED_OS_TLS_CLIENT_EXAMPLE = 'true'
    }
}

def set_crypto_pr_production_environment() {
    env.RUN_CRYPTO_TESTS_OF_CRYPTO_PR = 'true'
    env.RUN_TLS_TESTS_OF_CRYPTO_PR = 'true'
    env.MBED_CRYPTO_BRANCH = env.CHANGE_BRANCH
    env.MBED_OS_CRYPTO_EXAMPLES_REPO = 'git@github.com:ARMmbed/mbed-os-example-mbed-crypto.git'
    env.MBED_OS_CRYPTO_EXAMPLES_BRANCH = 'master'
    env.MBED_OS_ATECC608A_EXAMPLES_REPO = 'git@github.com:ARMmbed/mbed-os-example-atecc608a.git'
    env.MBED_OS_ATECC608A_EXAMPLES_BRANCH = 'master'
    if (['development', 'development-restricted', 'feature-psa'].contains(CHANGE_TARGET)) {
        env.TEST_MBED_OS_CRYPTO_EXAMPLES = 'true'
        env.TEST_MBED_OS_ATECC608A_EXAMPLES = 'true'
    }
}

def set_tls_pr_production_environment() {
    env.MBED_TLS_BRANCH = env.CHANGE_BRANCH
}

def set_crypto_release_environment() {
    env.JOB_TYPE = 'release'
    env.TARGET_REPO = 'crypto'
    env.REPO_TO_CHECKOUT = 'crypto'
    env.CHECKOUT_METHOD = 'parametrized'
    if (TEST_MBED_OS_TLS_EXAMPLES == 'true') {
        env.TEST_MBED_OS_AUTHCRYPT_EXAMPLE = 'true'
        env.TEST_MBED_OS_BENCHMARK_EXAMPLE = 'true'
        env.TEST_MBED_OS_HASHING_EXAMPLE = 'true'
        env.TEST_MBED_OS_TLS_CLIENT_EXAMPLE = 'true'
    }
    if (env.TEST_FAIL_EMAIL_ADDRESS == null) {
        env.TEST_FAIL_EMAIL_ADDRESS = 'mbed-crypto-eng@arm.com'
    }
    if (env.TEST_PASS_EMAIL_ADDRESS == null) {
        env.TEST_PASS_EMAIL_ADDRESS = "jaeden.amero@arm.com; oliver.harper@arm.com"
    }
}

def set_tls_release_environment() {
    env.JOB_TYPE = 'release'
    env.TARGET_REPO = 'tls'
    env.REPO_TO_CHECKOUT = 'tls'
    env.CHECKOUT_METHOD = 'parametrized'
}

def set_mbed_os_example_pr_environment(example) {
    env.JOB_TYPE = 'PR'
    env.CHECKOUT_METHOD = 'scm'
    env.TARGET_REPO = 'example'
    switch (example) {
        case 'TLS':
            env.TEST_MBED_OS_AUTHCRYPT_EXAMPLE = 'true'
            env.TEST_MBED_OS_BENCHMARK_EXAMPLE = 'true'
            env.TEST_MBED_OS_HASHING_EXAMPLE = 'true'
            env.TEST_MBED_OS_TLS_CLIENT_EXAMPLE = 'true'
            break
        case 'Crypto':
            env.TEST_MBED_OS_CRYPTO_EXAMPLES = 'true'
            break
        case 'ATECC608A':
            env.TEST_MBED_OS_ATECC608A_EXAMPLES = 'true'
            break
        default:
            throw new Exception("No example specified")
    }
}
