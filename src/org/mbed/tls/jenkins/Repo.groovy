package org.mbed.tls.jenkins

import com.cloudbees.groovy.cps.NonCPS

enum Repo {
    TLS,
    TF_PSA_CRYPTO,
    FRAMEWORK,
    EXAMPLE,

    // The Groovy CPS code generator fails to declare this implicit method,
    // which causes runtime exceptions.
    @NonCPS
    static <T extends Enum<T>> T valueOf(Class<T> clazz, String name) {
        return Enum.valueOf(clazz, name)
    }
}