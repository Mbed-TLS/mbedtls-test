package org.mbed.tls.jenkins.typing

import hudson.EnvVars
import hudson.model.JobProperty
import hudson.tasks.LogRotator
import jenkins.model.BuildDiscarder
import jenkins.model.BuildDiscarderProperty
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper

interface VarScriptContext {
    def <T> T stage(String label, Closure<T> body)
    Map<String, ?> parallel(Map<String, ?> args)
    RunWrapper getCurrentBuild()
    EnvVars getEnv()
    void milestone(int ordinal)
    void properties(List<? extends JobProperty> properties)
    BuildDiscarderProperty buildDiscarder(BuildDiscarder startegy)
    LogRotator logRotator(Map<String, String> args)
    void echo(String message)
    def <T> T node(String label, @DelegatesTo(NodeContext) Closure<T> body)
    def <T> T timestamps(Closure<T> body)
    def <T> T getContext(Class<T> clazz)
}