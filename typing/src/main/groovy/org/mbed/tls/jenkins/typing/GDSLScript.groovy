package org.mbed.tls.jenkins.typing

abstract class GDSLScript extends Script {
    private GDSLCollector collector

    void collectContributors(GDSLCollector collector) {
        this.collector = collector
        run()
    }

    protected GDSLContext context(Map args) {
        return collector.context(args)
    }

    protected GDSLScope closureScope(Map args) {
        return collector.closureScope(args)
    }

    protected GDSLScope annotatedScope(Map args) {
        return collector.annotatedScope(args)
    }

    protected void contributor(Object contexts, @DelegatesTo(GDSLContributor) Closure body) {
        collector.contributor(contexts, body)
    }

}