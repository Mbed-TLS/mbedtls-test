package org.mbed.tls.jenkins.typing

class ClosureScope implements GDSLScope {
    final String methodName
    final boolean isArg

    ClosureScope(String methodName, boolean isArg) {
        this.methodName = methodName
        this.isArg = isArg
    }

    boolean applies() { return false }
}
