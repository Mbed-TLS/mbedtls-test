import org.mbed.tls.jenkins.RepoType

void run_pr_job() {
    mbedtls.run_pr_job(RepoType.TF_PSA_CRYPTO, true, ['development'])
}
