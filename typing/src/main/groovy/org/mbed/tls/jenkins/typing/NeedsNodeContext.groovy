package org.mbed.tls.jenkins.typing

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation used to mark methods and constructors that need to be called
 * from inside a node collector. Using this annotation on a Class is equivalent
 * to annotating all of its methods and constructors.
 */
@Target([
    ElementType.METHOD,
    ElementType.LOCAL_VARIABLE,
])
@Retention(RetentionPolicy.SOURCE)
@interface NeedsNodeContext {}