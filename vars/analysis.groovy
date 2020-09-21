import groovy.transform.Field

// A static field has its content preserved across stages.
@Field static outcome_stashes = []

def stash_outcomes(job_name) {
    def stash_name = job_name + '-outcome'
    if (findFiles(glob: '*-outcome.csv')) {
        stash(name: stash_name,
              includes: '*-outcome.csv',
              allowEmpty: true)
        outcome_stashes.add(stash_name)
    }
}

def gather_outcomes() {
    // After running on an old branch which doesn't have the outcome
    // file generation mechanism, or after running a partial run,
    // there may not be any outcome file. In this case, silently
    // do nothing.
    if (outcome_stashes.isEmpty()) {
        return
    }
    node {
        dir('outcomes') {
            deleteDir()
            try {
                checkout_repo.checkout_repo()
                dir('csvs') {
                    for (stash_name in outcome_stashes) {
                        unstash(stash_name)
                    }
                    sh 'cat *.csv >../outcomes.csv'
                    deleteDir()
                }
                try {
                    if (fileExists('tests/scripts/analyze_outcomes.py')) {
                        sh 'tests/scripts/analyze_outcomes.py outcomes.csv'
                    }
                } finally {
                    sh 'xz outcomes.csv'
                    archiveArtifacts(artifacts: 'outcomes.csv.xz',
                    fingerprint: true, allowEmptyArchive: true)
                }
            } finally {
                deleteDir()
            }
        }
    }
}

def analyze_results() {
    gather_outcomes()
}

def analyze_results_and_notify_github() {
    try {
        analyze_results()
        if (env.BRANCH_NAME) {
            common.maybe_notify_github "Result analysis", 'SUCCESS',
                                       'OK'
        }
    } catch (err) {
        if (env.BRANCH_NAME) {
            common.maybe_notify_github "Result analysis", 'FAILURE',
                                       'Analysis failed'
        }
        throw (err)
    }
}
