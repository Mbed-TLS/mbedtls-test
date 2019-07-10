/* If testing the TLS tests with an Mbed Crypto PR, checkout the Mbed TLS
 * development branch and update the crypto submodule to the Mbed Crypto PR branch
 * In all other cases, the standard scm checkout will checkout the PR branch
 * we wish to test. */
def checkout_pr() {
    if (env.PR_TYPE == 'crypto' && env.REPO_TO_CHECKOUT == 'tls') {
        checkout([
            $class: 'GitSCM',
            branches: [[name: 'development']],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
                [$class: 'CloneOption',
                 timeout: 60],
                [$class: 'SubmoduleOption',
                 disableSubmodules: false,
                 parentCredentials: false,
                 recursiveSubmodules: true,
                 reference: '',
                 trackingSubmodules: false],
            ],
            submoduleCfg: [],
            userRemoteConfigs: [
                [credentialsId: env.GIT_CREDENTIALS_ID,
                 url: "git@github.com:ARMmbed/mbedtls.git"]
            ]
        ])
        dir('crypto') {
            checkout scm
        }
    } else {
        checkout scm
    }
}

def checkout_coverity_repo() {
    checkout([
        changelog: false,
        poll: false,
        scm: [
            $class: 'GitSCM',
            branches: [[name: '*/master']],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
                [$class: 'CloneOption',
                 noTags: true,
                 shallow: true],
                [$class: 'RelativeTargetDirectory',
                 relativeTargetDir: 'coverity-tools']
            ],
            submoduleCfg: [],
            userRemoteConfigs: [
                [url: 'git@github.com:ARMmbed/coverity-tools.git',
                 credentialsId: "${env.GIT_CREDENTIALS_ID}"]
            ]
        ]
    ])
}
