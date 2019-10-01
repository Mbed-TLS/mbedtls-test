import groovy.transform.Field

/*
 * This controls the timeout each job has. It does not count the time spent in
 * waiting queues and setting up the environment.
 *
 * Raas has its own resource queue with the timeout of 1000s, we need to take
 * it into account for the on-target test jobs.
 */
@Field perJobTimeout = [time: 45, raasOffset: 17, unit: 'MINUTES']

@Field compiler_paths = [
    'gcc' : 'gcc',
    'gcc48' : '/usr/local/bin/gcc48',
    'clang' : 'clang',
    'cc' : 'cc'
]

@Field docker_repo = '853142832404.dkr.ecr.eu-west-1.amazonaws.com/jenkins-mbedtls'

@Field one_platform = ["debian-9-x64"]
@Field linux_platforms = ["debian-9-i386", "debian-9-x64"]
@Field bsd_platforms = ["freebsd"]
@Field bsd_compilers = ["clang"]
@Field all_compilers = ['gcc', 'clang']
@Field gcc_compilers = ['gcc']
@Field asan_compilers = ['clang']

def get_docker_image(docker_image) {
    sh "\$(aws ecr get-login) && docker pull $docker_repo:$docker_image"
}

def get_all_sh_components() {
    node {
        /* Get components of all.sh */
        dir('src') {
            deleteDir()
            checkout_repo.checkout_repo()
            def all_sh_help = sh(
                script: "./tests/scripts/all.sh --help",
                returnStdout: true
            )
            if (all_sh_help.contains('list-components')) {
                all_sh_components = sh(
                    script: "./tests/scripts/all.sh --list-components",
                    returnStdout: true
                ).trim().split('\n')
            } else {
                error('Base branch out of date. Please rebase')
            }
        }
        return all_sh_components
    }
}
/* Check for any bad words found from pull request code and commit messages.
 * Bad words list is in file resources/bad_words.txt
 * Bad words may be a customer which can't be shown to public.
 * If words are found, this method will throw an error and PR will fail.
 */
def check_for_bad_words() {
    node {
        // can't access file bad_words.txt from the sh so we must write it there as a new file
        def BAD_WORDS = libraryResource 'bad_words.txt'
        writeFile file: 'bad_words.txt', text: BAD_WORDS
        def BAD_EXCLUDE = libraryResource 'bad_words_exclude.txt'
        writeFile file: 'bad_words_exclude.txt', text: BAD_EXCLUDE

        dir('src') {
            std_output = sh(
                        script: '''
                        set +e
                        git fetch origin $CHANGE_TARGET
                        git log FETCH_HEAD..HEAD > pr_git_log_messages.txt
                        grep -f ../bad_words.txt -Rnwi --exclude-dir=".git" --exclude-dir="crypto" . > ../bw.txt
                        grep -vi -f ../bad_words_exclude.txt ../bw.txt || [ "$?" = 1 ]
                        ''',
                        returnStdout: true
                    )
            echo std_output
            if (std_output) {
                throw new Exception("Pre Test Checks failed")
            }
        }
    }
}

def get_supported_windows_builds() {
    def is_c89 = null
    def vs_builds = []
    node {
        dir('src') {
            deleteDir()
            checkout_repo.checkout_repo()
            // Branches written in C89 (plus very minor extensions) have
            // "-Wdeclaration-after-statement" in CMakeLists.txt, so look
            // for that to determine whether the code is supposed to be C89.
            String cmakelists_contents = readFile('CMakeLists.txt')
            is_c89 = cmakelists_contents.contains('-Wdeclaration-after-statement')
        }
    }
    if (env.JOB_TYPE == 'PR') {
        vs_builds = ['2013']
    } else {
        vs_builds = ['2013', '2015', '2017']
    }
    if (is_c89) {
        vs_builds = ['2010'] + vs_builds
    }
    echo "vs_builds = ${vs_builds}"
    return ['mingw'] + vs_builds
}

def archive_zipped_log_files(job_name) {
    sh """\
for i in *.log; do
    [ -f "\$i" ] || break
    mv "\$i" "$job_name-\$i"
    xz "$job_name-\$i"
done
"""
    archiveArtifacts(
        artifacts: '*.log.xz',
        fingerprint: true,
        allowEmptyArchive: true
    )
}

def send_email(name, failed_builds, coverage_details) {
    if (failed_builds) {
        keys = failed_builds.keySet()
        failures = keys.join(", ")
        emailbody = """
${coverage_details['coverage']}

Logs: ${env.BUILD_URL}

Failures: ${failures}
"""
        subject = "${name} failed!"
        recipients = env.TEST_FAIL_EMAIL_ADDRESS
    } else {
        emailbody = """
${coverage_details['coverage']}

Logs: ${env.BUILD_URL}
"""
        subject = "${name} passed!"
        recipients = env.TEST_PASS_EMAIL_ADDRESS
    }
    echo subject
    echo emailbody
    emailext body: emailbody,
             subject: subject,
             to: recipients,
             mimeType: 'text/plain'
}

def run_release_jobs(name, jobs, failed_builds, coverage_details) {
    jobs.failFast = false
    try {
        parallel jobs
    } finally {
        if (currentBuild.rawBuild.getCauses()[0].toString().contains('TimerTriggerCause')) {
            send_email(name, failed_builds, coverage_details)
        }
    }
}
