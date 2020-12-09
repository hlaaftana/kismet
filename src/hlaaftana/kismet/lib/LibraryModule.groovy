package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Expression
import hlaaftana.kismet.call.Template
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.SingleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject

import static hlaaftana.kismet.call.ExprBuilder.call
import static hlaaftana.kismet.call.ExprBuilder.name
import static hlaaftana.kismet.lib.Functions.TEMPLATE_TYPE
import static hlaaftana.kismet.lib.Types.inferType

@CompileStatic
abstract class LibraryModule {
    abstract TypedContext getTyped()
    abstract Context getDefaultContext()

    void define(SingleType type) {
        define(type.name, new GenericType(Types.META_TYPE, type), type)
    }

    void define(String name, Type type, IKismetObject object) {
        typed(name, type, object)
        defaultContext.add(name, object)
    }

    void define(String name, IKismetObject object) {
        define(name, inferType(object), object)
    }

    void typed(String name, Type type, IKismetObject object) {
        typed.addVariable(name, object, type)
    }

    void alias(String old, String... news) {
        final dyn = defaultContext.get(old)
        final typ = typed.getAll(old)
        for (final n : news) {
            defaultContext.add(n, dyn)
            for (final t : typ) {
                typed.addVariable(n, t.variable.value, t.variable.type)
            }
        }
    }

    void negated(final String old, String... news) {
        def temp = new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                def arr = new ArrayList<Expression>(args.length + 1)
                arr.add(name(old))
                arr.addAll(args)
                call(name('not'), call(arr))
            }
        }
        for (final n : news) {
            defaultContext.add(n, temp)
            typed.addVariable(n, temp, TEMPLATE_TYPE)
        }
    }
}
