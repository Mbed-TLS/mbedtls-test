/* This function checks out the repo that branch that the job is testing.
 * If testing the TLS tests with an Mbed Crypto PR, checkout the Mbed TLS
 * development branch and update the crypto submodule to the Mbed Crypto PR branch
 * Otherwise, check out the repo that the job is targeting*/
def checkout_repo() {
    if (env.TARGET_REPO == 'crypto' && env.REPO_TO_CHECKOUT == 'tls') {
        checkout_mbedtls_repo()
        dir('crypto') {
            checkout_mbed_crypto_repo()
        }
    } else if (env.TARGET_REPO == 'tls') {
        checkout_mbedtls_repo()
    } else if (env.TARGET_REPO == 'crypto') {
        checkout_mbed_crypto_repo()
    } else {
        throw new Exception("Cannot determine repo to checkout")
    }
}

def checkout_mbedtls_repo() {
    if (env.TARGET_REPO == 'tls' && env.CHECKOUT_METHOD == 'scm') {
        checkout scm
    } else {
        checkout_parametrized_repo(MBED_TLS_REPO, MBED_TLS_BRANCH)
    }
}

def checkout_mbed_crypto_repo() {
    if (env.TARGET_REPO == 'crypto' && env.CHECKOUT_METHOD == 'scm') {
        checkout scm
    } else {
        checkout_parametrized_repo(MBED_CRYPTO_REPO, MBED_CRYPTO_BRANCH)
    }
}

def checkout_mbed_os_example_repo(repo, branch) {
    if (env.TARGET_REPO == 'example' && env.CHECKOUT_METHOD == 'scm') {
        checkout scm
    } else {
        checkout_parametrized_repo(repo, branch)
    }
}

def checkout_parametrized_repo(repo, branch) {
    checkout([
        scm: [
            $class: 'GitSCM',
            userRemoteConfigs: [
                [url: repo, credentialsId: env.GIT_CREDENTIALS_ID]
            ],
            branches: [[name: branch]],
            extensions: [
                [$class: 'CloneOption', timeout: 60],
                [$class: 'SubmoduleOption', recursiveSubmodules: true],
                [$class: 'LocalBranch', localBranch: branch],
            ],
        ]
    ])
}

def checkout_mbed_os() {
    checkout([
        scm: [
            $class: 'GitSCM',
            userRemoteConfigs: [
                [url: MBED_OS_REPO, credentialsId: env.GIT_CREDENTIALS_ID]
            ],
            branches: [[name: MBED_OS_BRANCH]],
            extensions: [
                [$class: 'CloneOption', timeout: 60, shallow: true],
            ],
        ]
    ])
    if (env.MBED_TLS_BRANCH) {
        dir('features/mbedtls/importer') {
            dir('TARGET_IGNORE/mbedtls')
            {
                deleteDir()
                checkout_mbedtls_repo()
            }
            sh """\
ulimit -f 20971520
make all
"""
        }
    }
    if (env.MBED_CRYPTO_BRANCH) {
        dir('features/mbedtls/mbed-crypto/importer') {
            dir('TARGET_IGNORE/mbed-crypto')
            {
                deleteDir()
                checkout_mbed_crypto_repo()
            }
            sh """\
ulimit -f 20971520
make all
"""
        }
    }
}
