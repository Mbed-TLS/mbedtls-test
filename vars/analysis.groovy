/*
 *  Copyright (c) 2019-2021, Arm Limited, All Rights Reserved
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may
 *  not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  This file is part of Mbed TLS (https://www.trustedfirmware.org/projects/mbed-tls/)
 */

import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Function

import groovy.transform.Field

import com.cloudbees.groovy.cps.NonCPS
import hudson.FilePath
import hudson.Launcher
import hudson.model.TaskListener
import jenkins.util.BuildListenerAdapter
import net.sf.json.JSONObject

import org.mbed.tls.jenkins.JobTimestamps

// A static field has its content preserved across stages.
@Field static outcome_stashes = []

@Field private static ConcurrentMap<String, ConcurrentMap<String, JobTimestamps>> timestamps =
        new ConcurrentHashMap<String, ConcurrentMap<String, JobTimestamps>>();

void record_timestamps(String group, String job_name, Callable<Void> body, String node_label = null) {
    def ts = new JobTimestamps()
    def group_map = timestamps.computeIfAbsent(group, new Function<String, ConcurrentMap<String, JobTimestamps>>() {
        @Override
        @NonCPS
        ConcurrentMap<String, JobTimestamps> apply(String key) {
            return new ConcurrentHashMap<String, JobTimestamps>()
        }
    })

    if (group_map.putIfAbsent(job_name, ts) != null) {
        throw new IllegalArgumentException("Group and job name pair '$group:$job_name' used multiple times.")
    }

    try {
        def stamped_body = {
            ts.start = System.currentTimeMillis()
            body()
        }
        if (node_label != null) {
            node(node_label, stamped_body)
        } else {
            stamped_body()
        }
    } finally {
        ts.end = System.currentTimeMillis()
    }
}

void node_record_timestamps(String node_label, String job_name, Callable<Void> body) {
    record_timestamps(node_label, job_name, body, node_label)
}

void record_inner_timestamps(String group, String job_name, Callable<Void> body) {
    def ts = timestamps[group][job_name]
    if (ts == null) {
        throw new NoSuchElementException(job_name)
    }
    ts.innerStart = System.currentTimeMillis()
    try {
        body()
    } finally {
        ts.innerEnd = System.currentTimeMillis()
    }
}

// Takes a filename -> content map, and produces archives for each entry, without launching an executor
void archive_strings(Map<String, String> artifacts) {
    def dir = new FilePath(Files.createTempDirectory('artifacts').toFile())
    try {
        def files = (Map<String, String>) artifacts.collectEntries {
            name, content -> [(name): dir.createTextTempFile(name, null, content).name]
        }
        currentBuild.rawBuild.pickArtifactManager().archive(
            dir, (Launcher) getContext(Launcher), BuildListenerAdapter.wrap((TaskListener) getContext(TaskListener)), files)
    } finally {
        dir.deleteRecursive()
    }
}

void print_timestamps() {
    writeFile(
        file: 'timestamps.json',
        text: JSONObject.fromObject([
            job: currentBuild.fullProjectName,
            build: currentBuild.number,
            main: timestamps.remove('main'),
            subtasks: timestamps
        ]).toString()
    )
    archiveArtifacts(artifacts: 'timestamps.json')
}

def stash_outcomes(job_name) {
    def stash_name = job_name + '-outcome'
    if (findFiles(glob: '*-outcome.csv')) {
        stash(name: stash_name,
              includes: '*-outcome.csv',
              allowEmpty: true)
        outcome_stashes.add(stash_name)
    }
}

// In a directory with the source tree available, process the outcome files
// from all the jobs.
def process_outcomes() {
    dir('csvs') {
        for (stash_name in outcome_stashes) {
            unstash(stash_name)
        }
        sh 'cat *.csv >../outcomes.csv'
        deleteDir()
    }

    // The complete outcome file is ~14MB compressed as I write.
    // Often we just want the failures, so make an artifact with just those.
    // Only produce a failure file if there was a failing job (otherwise
    // we'd just waste time creating an empty file).
    if (gen_jobs.failed_builds) {
        sh '''\
awk -F';' '$5 == "FAIL"' outcomes.csv >"failures.csv"
# Compress the failure list if it is large (for some value of large)
if [ "$(wc -c <failures.csv)" -gt 99999 ]; then
    xz failures.csv
fi
'''
    }

    try {
        if (fileExists('tests/scripts/analyze_outcomes.py')) {
            sh 'tests/scripts/analyze_outcomes.py outcomes.csv'
        }
    } finally {
        sh 'xz outcomes.csv'
        archiveArtifacts(artifacts: 'outcomes.csv.xz, failures.csv*',
                         fingerprint: true,
                         allowEmptyArchive: true)
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
    dir('outcomes') {
        deleteDir()
        try {
            checkout_repo.checkout_repo()
            process_outcomes()
        } finally {
            deleteDir()
        }
    }
}

def analyze_results() {
    node('helper-container-host') {
        gather_outcomes()
        print_timestamps()
    }
}

def analyze_results_and_notify_github() {
    try {
        analyze_results()
        common.maybe_notify_github "Result analysis", 'SUCCESS', 'OK'
    } catch (err) {
        common.maybe_notify_github "Result analysis", 'FAILURE',
                                   'Analysis failed'
        throw (err)
    }
}
