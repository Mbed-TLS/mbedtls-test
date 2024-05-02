package org.mbed.tls.jenkins.typing

import org.codehaus.groovy.ast.ClassNode

class GDSLContext {
    final ClassNode ctype
    final GDSLScope scope

    GDSLContext(ClassNode ctype, GDSLScope scope) {
        this.ctype = ctype
        this.scope = scope
    }

    boolean applies(ClassNode node) {
        return node.isDerivedFrom(ctype) && (scope == null || scope.applies())
    }
}
