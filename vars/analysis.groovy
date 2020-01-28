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
    node {
        // After running on an old branch which doesn't have the outcome
        // file generation mechanism, or after running a partial run,
        // there may not be any outcome file. In this case, silently
        // do nothing.
        if (!outcome_stashes.isEmpty()) {
            dir('outcomes') {
                deleteDir()
                dir('csvs') {
                    for (stash_name in outcome_stashes) {
                        unstash(stash_name)
                    }
                    // Use separate commands, not a pipeline, to get an error
                    // if cat fails.
                    sh """\
cat *.csv >../outcomes.csv
xz ../outcomes.csv
"""
                    deleteDir()
                }
                archiveArtifacts(artifacts: 'outcomes.csv.xz',
                                 fingerprint: true, allowEmptyArchive: true)
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
            githubNotify context: "${env.BRANCH_NAME} Result analysis",
                         description: 'OK',
                         status: 'SUCCESS'
        }
    } catch (err) {
        if (env.BRANCH_NAME) {
            githubNotify context: "${env.BRANCH_NAME} Result analysis",
                         description: 'Analysis failed',
                         status: 'FAILURE'
        }
        throw (err)
    }
}
