def set_crypto_pr_environment() {
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = 'crypto'
    env.REPO_TO_CHECKOUT = 'crypto'
}

def set_tls_pr_environment() {
    env.JOB_TYPE = 'PR'
    env.TARGET_REPO = 'tls'
    env.REPO_TO_CHECKOUT = 'tls'
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
}

def set_tls_release_environment() {
    env.JOB_TYPE = 'release'
    env.TARGET_REPO = 'tls'
    env.REPO_TO_CHECKOUT = 'tls'
    env.BRANCH_NAME = MBED_TLS_BRANCH
}
