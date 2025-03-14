package org.mbed.tls.jenkins.typing

import org.jenkinsci.plugins.pipeline.utility.steps.fs.FileWrapper;

/**
 * Marker interface used to indicate that a method calls its Callable parameter inside a
 * node context by annotating the parameter with {@link groovy.lang.DelegatesTo DelegatesTo(NodeContext)}
 * @see groovy.lang.DelegatesTo
 */
interface NodeContext {
    FileWrapper[] findFiles(Map<String, String> args)
    void stash(Map<String, ?> args)
    void unstash(String name)
    def <T> T dir(String path, Closure<T> body)
    Object sh(Map<String, ?> args)
    void sh(String script)
    void deleteDir()
    void archiveArtifacts(Map<String, ?> args)
}
