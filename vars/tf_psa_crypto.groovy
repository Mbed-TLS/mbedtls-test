void run_pr_job() {
    // Initialize CHANGE_TARGET and IS_RESTRICTED
    environ.set_pr_environment('TF-PSA-Crypto', true)

    String mbedtls_branch = 'development'
    // Test PRs targeting an LTS branch with the compatible Mbed-TLS LTS branch
    if (env.CHANGE_TARGET ==~ /tf-psa-crypto-1\.1(-restricted)?/) {
        mbedtls_branch = 'mbedtls-4.1'
    }

    if (env.IS_RESTRICTED) {
        mbedtls_branch += '-restricted'
    }

    mbedtls.run_pr_job(true, [mbedtls_branch], [env.BRANCH_NAME])
}
