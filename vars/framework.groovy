void run_pr_job(is_production=true) {
    environ.set_framework_pr_environment(is_production)
    common.run_pr_job(is_production, ['development', 'mbedtls-3.6'])
}

void run_release_job() {
    enviorn.set_framework_release_environment()
    common.run_release_job(['development', 'mbedtls-3.6'])
}