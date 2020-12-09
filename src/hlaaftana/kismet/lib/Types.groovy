package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.*
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetBoolean
import hlaaftana.kismet.vm.KismetNumber
import hlaaftana.kismet.vm.KismetTuple

import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.lib.Functions.typedTmpl

@CompileStatic
class Types extends LibraryModule {
    static final SingleType META_TYPE = new SingleType('Meta', [+Type.ANY] as TypeBound[])

    TypedContext typed = new TypedContext("types")
    Context defaultContext = new Context()

    static Type inferType(IKismetObject value) {
        if (value instanceof KismetNumber) value.type
        else if (value instanceof Function) Functions.FUNCTION_TYPE//func(Type.NONE, new TupleType(new Type[0]).withVarargs(Type.ANY))
        else if (value instanceof Template) Functions.TEMPLATE_TYPE
        else if (value instanceof TypeChecker) Functions.TYPE_CHECKER_TYPE
        else if (value instanceof Instructor) Functions.INSTRUCTOR_TYPE
        else if (value instanceof TypedTemplate) Functions.TYPED_TEMPLATE_TYPE
        else if (value instanceof Type) new GenericType(Types.META_TYPE, value)
        else throw new UnsupportedOperationException("Cannot infer type for kismet object $value")
    }

    Types() {
        define 'Any', new GenericType(META_TYPE, Type.ANY), Type.ANY
        define 'None', new GenericType(META_TYPE, Type.NONE), Type.NONE
        define 'cast', Functions.TYPE_CHECKER_TYPE, new TypeChecker() {
            @CompileStatic
            TypedExpression transform(TypedContext context, Expression... args) {
                final typ = ((GenericType) args[0].type(context, +META_TYPE).type).arguments[0]
                final expr = args[1].type(context)
                new TypedExpression() {
                    Type getType() { typ }

                    Instruction getInstruction() { expr.instruction }
                }
            }
        }
        define 'null', Type.NONE, Kismet.NULL
        define 'null?', func(Logic.BOOLEAN_TYPE, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(null == args[0] || null == args[0].inner())
            }
        }
        negated 'null?', 'not_null?'
        define META_TYPE
        define '.[]', typedTmpl(META_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = (SingleType) args[0].instruction.evaluate(context)
                final arg = (Type) args[1].instruction.evaluate(context)
                final typ = new GenericType(base, arg)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define '.[]', typedTmpl(META_TYPE, META_TYPE, new TupleType().withVarargs(META_TYPE)), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = (SingleType) args[0].instruction.evaluate(context)
                final arg = (Type[]) args[1].instruction.evaluate(context).inner()
                final typ = new GenericType(base, arg)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'type_of', typedTmpl(META_TYPE, Type.ANY), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, args[0].type), args[0].type)
            }
        }
        define 'as',  func { IKismetObject... a -> a[0].invokeMethod('as', [a[1].inner()] as Object[]) }
    }
}
