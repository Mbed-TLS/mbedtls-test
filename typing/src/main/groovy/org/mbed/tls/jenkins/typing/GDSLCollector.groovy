package org.mbed.tls.jenkins.typing

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.MethodCall
import org.codehaus.groovy.ast.stmt.EmptyStatement
import org.codehaus.groovy.control.ResolveVisitor
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor

import org.objectweb.asm.Opcodes

class GDSLCollector {

    private List<Contributor> contributors = []
    private Map<String, ClassNode> dynamicClasses = [:]
    private StaticTypeCheckingVisitor typeCheckingVisitor

    GDSLCollector(StaticTypeCheckingVisitor typeCheckingVisitor) {
        this.typeCheckingVisitor = typeCheckingVisitor
        def context = typeCheckingVisitor.typeCheckingContext
        def source = context.source
        try {
            def gdsl = new JenkinsGDSL()
            gdsl.collectContributors(this)
            def dummy = ClassHelper.make('<dummy>')
            dummy.module = source.AST
            dynamicClasses.each { name, classNode ->
                dummy.addField(name, Opcodes.ACC_PUBLIC, classNode, null)
            }
            def resolver = new ResolveVisitor(context.compilationUnit)
            resolver.startResolving(dummy, source)
        } catch (Exception e) {
            context.errorCollector.addException(e, source)
        }
    }

    List<MethodNode> findMethod(ClassNode receiver, String name, ArgumentListExpression argList, ClassNode[] argTypes, MethodCall call) {
        List<MethodNode> candidates = []
        for (def contrib : contributors) {
            if (contrib.applies(receiver)) {
                candidates.addAll(contrib.container.getDeclaredMethods(name))
            }
        }
        return StaticTypeCheckingSupport.chooseBestMethod(receiver, candidates, argTypes)
    }

    // Methods available inside a GDSL script

    GDSLContext context(Map args) {
        def ctype = getArg(args, ClassNode, 'ctype')
        def scope = getArg(args, GDSLScope, 'scope')
        return new GDSLContext(ctype, scope)
    }

    GDSLScope closureScope(Map args) {
        def methodName = getArg(args, String, 'methodName')
        def isArg = getArg(args, Boolean, 'isArg') as boolean
        return new ClosureScope(methodName, isArg)
    }

    GDSLScope annotatedScope(Map args) {
        def cname = getArg(args, ClassNode, 'cname')
        return new AnnotatedScope(cname)
    }

    void contributor(Object ctx, @DelegatesTo(GDSLContributor) Closure body) {
        List<GDSLContext> contexts
        if (ctx instanceof List<GDSLContext>) {
            contexts = ctx
        } else if (ctx instanceof GDSLContext) {
            contexts = [(GDSLContext) ctx]
        } else {
            throw new IllegalArgumentException("Argument 'contexts' must be an instance of Context or List<Contexts>")
        }
        Contributor contrib = new Contributor(contexts)
        body.delegate = contrib
        body.call()
        contributors.add(contrib)
    }

    private class Contributor implements GDSLContributor {
        final List<GDSLContext> contexts
        final ClassNode container = ClassHelper.make('<dummy>')

        Contributor(List<GDSLContext> contexts) {
            this.contexts = contexts
        }

        boolean applies(ClassNode node) {
            return contexts.any({ ctx -> ctx.applies(node) })
        }

        // Methods available inside a GDSL contributor definition

        void method(Map args) {
            String name = getArg(args, String, 'name', false)
            ClassNode type = getArg(args, ClassNode, 'type')
            List<Map> namedParams = getList(args, Map, 'namedParams')
            Map<String, ClassNode> positionalParams = getMap(args, String, ClassNode, 'params')
            List<Parameter> params = []
            if (namedParams != null && namedParams.size() > 0) {
                params.add(new Parameter(ClassHelper.MAP_TYPE, '<args>'))
            }
            positionalParams.collect(params, { key, value -> new Parameter(value, key) })
            def method = container.addMethod(
                    name,
                    Opcodes.ACC_PUBLIC,
                    type,
                    params as Parameter[],
                    ClassNode.EMPTY_ARRAY,
                    EmptyStatement.INSTANCE
            )
        }

        void property(Map args) {
            String name = getArg(args, String, 'name', false)
            ClassNode type = getArg(args, ClassNode, 'type')
            container.addProperty(name, Opcodes.ACC_PUBLIC, type, null, null, null)
        }

        Map parameter(Map args) {
            return args
        }
    }

    // Helper methods

    private ClassNode makeClassNode(Object name, Object type) {
        if (type == null) {
            return ClassHelper.OBJECT_TYPE
        } else if (type instanceof Class) {
            return ClassHelper.make(type)
        } else if (type instanceof String) {
            ClassNode node = dynamicClasses.get(type)
            if (node == null) {
                if (type.contains('<')) {
                    // Class uses generics, use the full Groovy type parser.
                    // Putting the type name inside a generic parameter forces groovy to consider it a type.
                    // This fails if $type is a primitive type, but those are handled by the fast path below.
                    def context = typeCheckingVisitor.typeCheckingContext
                    def sourceUnit = new SourceUnit(
                            '<dummy>', "DummyClass<$type> dummy() {}",
                            context.compilationUnit.configuration,
                            context.compilationUnit.classLoader,
                            context.errorCollector
                    )
                    sourceUnit.parse()
                    sourceUnit.nextPhase()
                    sourceUnit.convert()
                    node = sourceUnit.AST.methods[0].returnType.genericsTypes[0].type
                } else {
                    // Class doesn't use generics, just call ClassHelper.make()
                    node = ClassHelper.make(type)
                }
                if (!node.resolved) {
                    dynamicClasses.put(type, node)
                }
            }
            return node
        }
        throw new IllegalArgumentException("Argument '$name' must be an instance of java.lang.String or java.lang.Class")
    }

    private <T, N> T getArg(Map<N, ?> args, Class<T> clazz, N name, boolean allowNull = true) {
        def value = args[name]
        if (value == null && !allowNull) {
            throw new IllegalArgumentException("Argument '$name' must not be 'null'")
        } else if (clazz == ClassNode) {
            return (T) makeClassNode(name, value)
        } else if (value != null && !clazz.isInstance(value)) {
            throw new IllegalArgumentException("Argument '$name' must be an instance of $clazz.name")
        } else {
            return (T) value
        }
    }

    private <K, V, N> Map<K, V> getMap(Map<N, ?> args, Class<K> keyClass, Class<V> valueClass, N name, boolean allowNull = true) {
        def map = getArg(args, Map, name, allowNull)
        return (Map<K, V>) map?.collectEntries { key, value ->
            if (!keyClass.isInstance(key)) {
                throw new IllegalArgumentException("Key '$key' of argument '$name' must be an instance of $keyClass.name")
            } else if (valueClass == ClassNode) {
                return [key, makeClassNode(key, value)]
            } else if (!valueClass.isInstance(value)) {
                throw new IllegalArgumentException("The value of entry '$key' of argument '$name' must be an instance of $valueClass.name")
            } else {
                return [key, value]
            }
        }
    }

    private <E, N> List<E> getList(Map<N, ?> args, Class<E> clazz, N name, boolean allowNull = true) {
        def list = getArg(args, List, name, allowNull)
        return list?.collect { element ->
            if (clazz == ClassNode) {
                return (E) makeClassNode(name, element)
            } else if (!clazz.isInstance(element)) {
                throw new IllegalArgumentException("Element '$element' of argument '$name' must be an instance of $clazz.name")
            } else {
                return (E) element
            }
        }
    }
}