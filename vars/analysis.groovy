import groovy.transform.Field

// A static field has its content preserved across stages.
@Field static outcome_stashes = []

def stash_outcomes(job_name) {
    def stash_name = job_name + '-outcome'
    stash(name: stash_name,
          includes: '*-outcome.csv',
          allowEmpty: true)
    outcome_stashes.add(stash_name)
}

def gather_outcomes() {
    node("ubuntu-16.10-x64") {
        dir('outcomes') {
            deleteDir()
            for (stash_name in outcome_stashes) {
                unstash(stash_name)
            }
            // If there are no outcome files, the cat invocation will fail.
            // This should only happen when running a partial pipeline.
            // Let it be an error so that it's apparent if something went
            // wrong during a normal run.
            sh """
cat *.csv >../outcomes.csv
xz ../outcomes.csv
"""
            deleteDir()
        }
        archiveArtifacts(artifacts: 'outcomes.csv.xz',
                         fingerprint: true, allowEmptyArchive: true)
    }
}

def analyze_results() {
    gather_outcomes()
}
