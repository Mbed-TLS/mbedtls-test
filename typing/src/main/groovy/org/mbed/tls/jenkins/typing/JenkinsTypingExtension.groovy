package org.mbed.tls.jenkins.typing

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension
import org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor

@CompileStatic
class JenkinsTypingExtension extends AbstractTypeCheckingExtension {

    final Set<String> varScripts = [
            'analysis',
            'checkout_repo',
            'common',
            'dockerfile_builder',
            'environ',
            'gen_jobs',
            'mbedtls',
            'scripts',
    ] as Set

    JenkinsTypingExtension(StaticTypeCheckingVisitor typeCheckingVisitor) {
        super(typeCheckingVisitor)
    }

    @Override
    boolean beforeVisitClass(ClassNode node) {
        println "Visiting Class $node.name"
        println "Annotations: ${node.annotations*.classNode.name}"
        return false
    }

    @Override
    boolean beforeVisitMethod(MethodNode node) {
//        println "Visiting Method $node.name"
//        println "Annotations: ${node.annotations*.classNode.name}"
        if (!node.static) {
            delegatesTo(classNodeFor(VarScriptContext))
            if (isAnnotatedBy(node, NeedsNodeContext)) {
                delegatesTo(classNodeFor(NodeContext))
            }
        }
        return false
    }

//    @Override
//    void onMethodSelection(Expression expression, MethodNode target) {
//        println "Method selected: $target.name"
//    }

    @Override
    boolean beforeMethodCall(MethodCall call) {
        try {
            // Fix the type checker mistaking the global variables generated for scripts under var/ to be
            // class references.
//            println "Before method call: $call.receiver $call.methodAsString ${call.getClass()}"
            if (call instanceof MethodCallExpression) {
                if (!context.enclosingMethod.static) {
                    def receiver = call.receiver
                    if (receiver instanceof ClassExpression) {
                        if (receiver.text in varScripts) {
                            def var = new VariableExpression(receiver.text, ((ClassExpression) receiver).type)
                            // Errors raised by code without source position set are ignored
                            var.setSourcePosition(receiver)
                            def newCall = new MethodCallExpression(var, call.method, call.arguments)
                            newCall.setSourcePosition(call)
//                            println 'visit newCall'
                            // Type check the generated method call
                            newCall.visit(typeCheckingVisitor)
                            // Propagate the return type information from the generated call to the original
                            storeType(call, getType(newCall))
                            // Skip the rest of the type checking for the original call
                            return true
                        }
                    }
                } else {
//                    println 'Ignoring static function'
                }
            }
        } catch (Exception e) {
            context.errorCollector.addException(e, context.source)
        }
        return false
    }

//    @Override
//    boolean handleUnresolvedVariableExpression(VariableExpression var) {
//        try {
//            println "Variable: $var"
//            def prop = new PropertyExpression(VariableExpression.THIS_EXPRESSION, var.name)
//            prop.implicitThis = true
//            prop.setSourcePosition(var)
//            prop.visit(typeCheckingVisitor)
//            storeType(var, getType(prop))
//            return true
//        } catch (Exception e) {
//            context.errorCollector.addException(e, context.source)
//        }
//        return false
//    }

    @Override
    boolean handleUnresolvedProperty(PropertyExpression exp) {
        try {
            println "Property: $exp"
            if (exp.objectExpression instanceof ClassExpression) {
                if (exp.objectExpression.text in varScripts) {
                    def var = new VariableExpression(exp.objectExpression.text, exp.objectExpression.type)
                    var.setSourcePosition(exp.objectExpression)
                    def prop = new PropertyExpression(var, exp.property)
                    prop.setSourcePosition(exp)
                    prop.visit(typeCheckingVisitor)
                    storeType(exp, getType(prop))
                    return true
                }
//            } else {
//                if (getType(exp.objectExpression).name in varScripts) {
//                    def name = exp.propertyAsString
//                    println name
//                    switch (name) {
//                        case varScripts:
//                            println 'varScripts!!!!!'
//                            storeType(exp, context.source.AST.unit.getClass(name))
//                            return true
////                        case 'currentBuild':
////                            storeType(exp, classNodeFor(Class.forName('org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper')))
////                            return true
////                        case 'env':
////                            storeType(exp, classNodeFor(Class.forName('hudson.EnvVars')))
////                            return true
//                    }
//                }
            }
        } catch (Exception e) {
            context.errorCollector.addException(e, context.source)
        }
        return false
    }

    @Override
    List<MethodNode> handleMissingMethod(ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call) {
        // receiver is the inferred type of the receiver
        // name is the name of the called method
        // argList is the list of arguments the method was called with
        // argTypes is the array of inferred types for each argument
        // call is the method call for which we couldn’t find a target method
        try {
            println "Method: $name"
            println "Class: $receiver.name, Receiver: $call.receiver"
            if (currentScope == null) {
                if (enclosingBinaryExpressionStack.any { exp -> isAnnotatedBy(exp, NeedsNodeContext) }) {
                    delegatesTo(classNodeFor(NodeContext))
                }
                if (call instanceof MethodCallExpression) {
                    newScope()
                    typeCheckingVisitor.visitMethodCallExpression(call)
                    scopeExit()
                    MethodNode method = getTargetMethod(call)
                    return [method].findAll()
                }
            }
//            List<MethodNode> methods = collector.findMethod(receiver, name, argList, argTypes, call)
//            println methods
//            if (methods.empty && context.enclosingClosure != null) {
//                def dvar = new VariableExpression('delegate')
//                dvar.visit(typeCheckingVisitor)
//                def dtype = getType(dvar)
//                if (receiver != dtype) {
//                    methods = collector.findMethod(dtype, name, argList, argTypes, call)
//                }
//                println methods
//            }
//            return methods
        } catch (Exception e) {
            context.errorCollector.addException(e, context.source)
        }
        return []
    }

}
