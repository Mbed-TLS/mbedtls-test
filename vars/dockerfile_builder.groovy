/*
 *  Copyright The Mbed TLS Contributors
 *  SPDX-License-Identifier: Apache-2.0
 */

def run_job() {
    timestamps {
        stage('dockerfile-builder') {
            def jobs = common.linux_platforms.collectEntries { platform ->
                String snake_case = platform.toUpperCase(Locale.ROOT).replaceAll(/[^A-Z0-9]/, '_')
                if (env.getProperty("BUILD_${snake_case}_DOCKER_IMAGE") == 'true') {
                    return gen_jobs.gen_dockerfile_builder_job(platform, true)
                }
                return Collections.emptyMap()
            }

            jobs.failFast = false
            parallel jobs
        }
    }
}
