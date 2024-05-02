package org.mbed.tls.jenkins.typing

import org.codehaus.groovy.ast.ClassNode

class AnnotatedScope implements GDSLScope {
    final ClassNode cname

    AnnotatedScope(ClassNode cname) {
        this.cname = cname
    }

    boolean applies() { return false }
}
