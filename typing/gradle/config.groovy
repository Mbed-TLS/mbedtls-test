import groovy.transform.TypeChecked
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

configuration.scriptBaseClass = 'org.mbed.tls.jenkins.typing.GDSLScript'
configuration.addCompilationCustomizers(
        new ASTTransformationCustomizer(TypeChecked)
)