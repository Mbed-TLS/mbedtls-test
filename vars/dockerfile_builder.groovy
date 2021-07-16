def run_job() {
    timestamps {
        stage('dockerfile-builder') {
            jobs = [:]
            delete_tags = []
            switch (DOCKER_IMAGE_16_04_ACTION) {
                case 'build':
                    jobs += gen_jobs.gen_dockerfile_builder_job(TEST_REPO, TEST_BRANCH, 'ubuntu-16.04', DOCKER_IMAGE_16_04_TAG)
                    break
                case 'publish':
                    jobs += gen_jobs.gen_docker_image_publisher_job('ubuntu-16.04', DOCKER_IMAGE_16_04_TAG)
                    break
                case 'delete':
                    delete_tags += [DOCKER_IMAGE_16_04_TAG]
            }
            switch (DOCKER_IMAGE_18_04_ACTION) {
                case 'build':
                    jobs += gen_jobs.gen_dockerfile_builder_job(TEST_REPO, TEST_BRANCH, 'ubuntu-18.04', DOCKER_IMAGE_18_04_TAG)
                    break
                case 'publish':
                    jobs += gen_jobs.gen_docker_image_publisher_job('ubuntu-18.04', DOCKER_IMAGE_18_04_TAG)
                    break
                case 'delete':
                    delete_tags += [DOCKER_IMAGE_18_04_TAG]
            }
            jobs += gen_jobs.gen_docker_batch_delete_tags_job(delete_tags)
            jobs.failFast = false
            parallel jobs
        }
    }
}
