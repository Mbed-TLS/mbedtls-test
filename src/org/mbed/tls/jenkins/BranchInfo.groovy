package org.mbed.tls.jenkins

class BranchInfo {
    /** The type of the repo */
    public String repo

    /** The name of the branch */
    public String branch

    /**
     * A unique prefix identifying a tested branch.
     *
     * The prefix format is [$repo-][$branch-].
     * - The repository part ($repo-) is omitted if all branches under test
     *   belong to the same repository.
     * - The branch part ($branch-) is omitted if a repository has only one
     *   branch under test.
     * - The prefix is an empty string if only one branch is under test.
     *
     * This prefix is used to distinguish resources (such as jobs, stashes, etc.)
     * associated with different branches during testing.
     */
    public String prefix

    /** Tree stash name */
    public String stash

    /** Git hash of the framework commit to use in mbedtls. Set in crypto PRs. */
    public String framework_override

    /** Map from component name to chosen platform to run it, or to null
     *  if no platform has been chosen yet. */
    public Map<String, String> all_sh_components

    /** Whether scripts/min_requirements.py is available. Older branches don't
     *  have it, so they only get what's hard-coded in the docker files on Linux,
     *  and bare python on other platforms. */
    public boolean has_min_requirements

    /** Ad hoc overrides for scripts/ci.requirements.txt, used to adjust
     *  requirements on older branches that broke due to updates of the
     *  required packages.
     *  Only used if {@link #has_min_requirements} is {@code true}. */
    public String python_requirements_override_content

    /** Name of the file containing python_requirements_override_content.
     *  The string is injected into Unix sh and Windows cmd command lines,
     *  so it must not contain any shell escapes or directory separators.
     *  Only used if {@link #has_min_requirements} is {@code true}.
     *  Set to an empty string for convenience if no override is to be
     *  done. */
    public String python_requirements_override_file

    /** All of the Visual Studio versions supported by this branch */
    public List<String> supported_vs_versions

    /** Keep track of builds that fail */
    final Set<String> failed_builds
    final Set<String> outcome_stashes

    /** Record coverage details for reporting */
    String coverage_details

    BranchInfo() {
        this.prefix = ''
        this.stash = ''
        this.framework_override = ''
        this.all_sh_components = [:]
        this.has_min_requirements = false
        this.python_requirements_override_content = ''
        this.python_requirements_override_file = ''
        this.supported_vs_versions = []
        this.failed_builds = []
        this.outcome_stashes = []
        this.coverage_details = 'Code coverage job did not run'
    }
}
