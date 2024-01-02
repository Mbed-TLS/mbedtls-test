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
import java.nio.file.NoSuchFileException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Function

import groovy.transform.Field

import com.cloudbees.groovy.cps.NonCPS
import hudson.FilePath
import hudson.Launcher
import hudson.model.ItemGroup
import hudson.model.Job
import hudson.model.Run
import hudson.model.TaskListener
import hudson.plugins.git.util.BuildData
import jenkins.branch.MultiBranchProject
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

void main_record_timestamps(String job_name, Callable<Void> body) {
    timestamps {
        try {
            record_timestamps('main', job_name, body)
        } catch (exception) {
            /* If an exception has propagated this far without modifying the build result,
             * set it to FAILED, so that archive_timestamps() reports it correctly. */
            if (currentBuild.currentResult == 'SUCCESS') {
                currentBuild.result = 'FAILED'
            }
            throw exception
        } finally {
            stage('archive-timestamps') {
                archive_timestamps()
            }
        }
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

// Gather the build information we store in timestamp files, and write it in a provided / new map
@NonCPS
static Map<String, Object> get_build_info(Run build, Map<String, Object> info=[:]) {
    def data = build.getActions(BuildData).find({ data -> data.remoteUrls[0] =~ /mbedtls-test/ })
    def project = build.parent
    def group = project.parent
    if (group instanceof MultiBranchProject) {
        project = group
    }
    def env = build.getEnvironment(TaskListener.NULL)
    def pr = env.CHANGE_ID

    info.version = 1
    info.testCommit = data.lastBuiltRevision.sha1String
    info.job = project.name
    info.branch = env.CHANGE_TARGET ?: env.MBED_TLS_BRANCH
    if (pr) {
        info.pr = pr as Integer
    }
    info.build = build.number
    info.result = build.result?.toString() ?: 'SUCCESS'
    return info
}

void archive_timestamps() {
    archive_strings([
        'timestamps.json': JSONObject.fromObject(get_build_info(currentBuild.rawBuild, [
            subtasks: timestamps
        ])).toString()
    ])
}

@NonCPS
static List<JSONObject> gather_timestamps_since(ItemGroup project_root, long threshold_ms) {
    List<JSONObject> builds = []
    def names = ['mbed-tls-nightly-tests', 'mbed-tls-pr-head', 'mbed-tls-pr-merge']
    for (def name : names) {
        def item = project_root.getItem(name)
        def jobs
        if (item instanceof ItemGroup<Job<Job, Run>>) {
            jobs = item.items
        } else {
            assert item instanceof Job<Job, Run>
            jobs = [item]
        }
        for (def job : jobs) {
            for (def build : job.builds) {
                if (!build.building && build.startTimeInMillis + build.duration >= threshold_ms) {
                    JSONObject json
                    try {
                        json = JSONObject.fromObject(build.artifactManager.root().child('timestamps.json').open().text)
                    } catch (NoSuchFileException ignored) {
                        continue
                    }

                    if (json.getOrDefault('version', 0) < 1) {
                        // Convert to version 1 format
                        get_build_info(build, json)
                        ((JSONObject) json.subtasks).main = json.remove('main')
                    }
                    builds.add(json)
                }
            }
        }
    }
    return builds
}

void gather_timestamps() {
    stage('gather-timestamps') {
        def builds = gather_timestamps_since(currentBuild.rawBuild.parent.parent, currentBuild.startTimeInMillis - 24 * 60 * 60 * 1000)
        def group_path = ['testCommit': null, 'job': null, 'branch': null, 'pr': -1, 'build': -1, 'result': null]
        def ts_format = ['start', 'end', 'innerStart', 'innerEnd']

        List<String> records = [(group_path.keySet() + ['group', 'subtask'] + ts_format).join(',')]
        builds.collectMany(records, { build ->
            def build_header = group_path.collect(build.&getOrDefault)
            return ((Map<String, Map<String, JSONObject>>) build.subtasks).collectMany { group, tasks ->
                tasks.collect { subtask, ts ->
                    return ts_format.collect(build_header + [group, subtask], ts.&get).join(',')
                }
            }
        })

        archive_strings([
            'timestamps-bundle.json': JSONObject.fromObject([builds: builds]).toString(),
            'timestamps-bundle.csv' : records.join('\n')
        ])
    }
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

    // The complete outcome file is 2.1GB uncompressed / 56MB compressed as I write.
    // Often we just want the failures, so make an artifact with just those.
    // Only produce a failure file if there was a failing job (otherwise
    // we'd just waste time creating an empty file).
    //
    // Note that grep ';FAIL;' could pick up false positives, if another field such
    // as test description or test suite was "FAIL".
    if (gen_jobs.failed_builds) {
        sh '''\
grep ';FAIL;' outcomes.csv >"failures.csv"
# Compress the failure list if it is large (for some value of large)
if [ "$(wc -c <failures.csv)" -gt 99999 ]; then
    LC_ALL=C xz -0 -T8 failures.csv
fi
'''
    }

    try {
        if (fileExists('tests/scripts/analyze_outcomes.py')) {
            record_inner_timestamps('helper-container-host', 'result-analysis') {
                sh 'tests/scripts/analyze_outcomes.py outcomes.csv'
            }
        }
    } finally {
        sh 'xz -0 -T8 outcomes.csv'
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
    node_record_timestamps('helper-container-host', 'result-analysis') {
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
}

void analyze_results() {
    try {
        gather_outcomes()
    } catch (err) {
        common.maybe_notify_github('FAILURE', 'Result analysis failed')
        throw (err)
    }
}
