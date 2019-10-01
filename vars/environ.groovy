def set_crypto_pr_environment() {
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = 'crypto'
    env.REPO_TO_CHECKOUT = 'crypto'
    env.MBED_OS_REPO = 'git@github.com:ARMmbed/mbed-os.git'
    env.MBED_OS_BRANCH = 'master'
    env.MBED_CRYPTO_BRANCH = env.CHANGE_BRANCH
}

def set_tls_pr_environment() {
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = 'tls'
    env.REPO_TO_CHECKOUT = 'tls'
    env.MBED_OS_REPO = 'git@github.com:ARMmbed/mbed-os.git'
    env.MBED_OS_BRANCH = 'master'
    env.MBED_TLS_BRANCH = env.CHANGE_BRANCH
}

def set_crypto_release_environment() {
    env.JOB_TYPE = 'release'
    env.TARGET_REPO = 'crypto'
    env.REPO_TO_CHECKOUT = 'crypto'
    env.BRANCH_NAME = MBED_CRYPTO_BRANCH
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
    env.BRANCH_NAME = MBED_TLS_BRANCH
}
