package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.*
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetBoolean
import hlaaftana.kismet.vm.KismetDistinct
import hlaaftana.kismet.vm.KismetNumber
import hlaaftana.kismet.vm.Memory

import static hlaaftana.kismet.lib.Functions.*
import static hlaaftana.kismet.lib.Functions.TYPE_CHECKER_TYPE

@CompileStatic
class Types extends NativeModule {
    static final SingleType META_TYPE = new SingleType('Meta', +Type.ANY)  {
        boolean check(IKismetObject obj) { obj instanceof Type }
        boolean checkGenerics(IKismetObject obj, Type... args) { args[0].relation((Type) obj).assignableFrom }
    },
        TYPE_BOUND_TYPE = new SingleType('TypeBound', +META_TYPE)

    static Type inferType(IKismetObject value) {
        if (value instanceof KismetNumber) value.type
        else if (value instanceof Function) FUNCTION_TYPE//func(Type.NONE, new TupleType(new Type[0]).withVarargs(Type.ANY))
        else if (value instanceof Template) TEMPLATE_TYPE
        else if (value instanceof TypeChecker) TYPE_CHECKER_TYPE
        else if (value instanceof Instructor) INSTRUCTOR_TYPE
        else if (value instanceof TypedTemplate) TYPED_TEMPLATE_TYPE
        else if (value instanceof Type) new GenericType(Types.META_TYPE, value)
        else throw new UnsupportedOperationException("Cannot infer type for kismet object $value")
    }

    Types() {
        super("types")
        define 'Any', new GenericType(META_TYPE, Type.ANY), Type.ANY
        define 'None', new GenericType(META_TYPE, Type.NONE), Type.NONE
        define 'cast', TYPE_CHECKER_TYPE, new TypeChecker() {
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
        define 'prefer_type', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                def typ = args[1].type(context, +new UnionType(TYPE_BOUND_TYPE, META_TYPE)).instruction.evaluate(context)
                args[0].type(context, typ instanceof TypeBound ? typ : new TypeBound((Type) typ))
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
        define 'union_type', TYPED_TEMPLATE_TYPE.generic(new TupleType().withVarargs(META_TYPE), META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final types = new Type[args.length]
                for (int i = 0; i < args.length; ++i)
                    types[i] = (Type) args[i].instruction.evaluate(context)
                final typ = new UnionType(types)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'intersection_type', TYPED_TEMPLATE_TYPE.generic(new TupleType().withVarargs(META_TYPE), META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final types = new Type[args.length]
                for (int i = 0; i < args.length; ++i)
                    types[i] = (Type) args[i].instruction.evaluate(context)
                final typ = new IntersectionType(types)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'distinct', typedTmpl(META_TYPE, META_TYPE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = (Type) args[0].instruction.evaluate(context)
                final typ = new DistinctType(base)
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'instantiate', typedTmpl(Type.NONE, META_TYPE/*.generic(AnyDistinctType.ANY)*/, Type.ANY), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final typ = (DistinctType) args[0].instruction.evaluate(context)
                final inst = args[1].instruction
                new BasicTypedExpression(typ, new Instruction() {
                    IKismetObject evaluate(Memory c) {
                        new KismetDistinct(typ, inst.evaluate(c))
                    }
                })
            }
        }
        define 'undistinct', typedTmpl(Type.NONE, AnyDistinctType.ANY), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = ((DistinctType) args[0].type).inner
                final inst = args[0].instruction
                new BasicTypedExpression(base, new Instruction() {
                    IKismetObject evaluate(Memory c) {
                        ((KismetDistinct) inst.evaluate(c)).inner
                    }
                })
            }
        }
        define 'undistinct', typedTmpl(META_TYPE, META_TYPE.generic(AnyDistinctType.ANY)), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                final base = (DistinctType) args[0].instruction.evaluate(context)
                final typ = base.inner
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
            }
        }
        define 'type_of', typedTmpl(META_TYPE, Type.ANY), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<Type>(new GenericType(META_TYPE, args[0].type), args[0].type)
            }
        }
        define 'as',  func { IKismetObject... a -> a[0].invokeMethod('as', [a[1].inner()] as Object[]) }
        define 'assignable_to?', typedTmpl(Logic.BOOLEAN_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<KismetBoolean>(Logic.BOOLEAN_TYPE,
                        KismetBoolean.from(unmeta(args[0].type).relation(unmeta(args[1].type)).assignableTo))
            }
        }
        define 'assignable_from?', typedTmpl(Logic.BOOLEAN_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<KismetBoolean>(Logic.BOOLEAN_TYPE,
                    KismetBoolean.from(unmeta(args[0].type).relation(unmeta(args[1].type)).assignableFrom))
            }
        }
        define 'subtype?', typedTmpl(Logic.BOOLEAN_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<KismetBoolean>(Logic.BOOLEAN_TYPE,
                    KismetBoolean.from(unmeta(args[0].type).relation(unmeta(args[1].type)).sub))
            }
        }
        define 'supertype?', typedTmpl(Logic.BOOLEAN_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                new TypedConstantExpression<KismetBoolean>(Logic.BOOLEAN_TYPE,
                    KismetBoolean.from(unmeta(args[0].type).relation(unmeta(args[1].type)).super))
            }
        }
        define 'covariant', func(TYPE_BOUND_TYPE, META_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                TypeBound.co((Type) args[0])
            }
        }
        define 'contravariant', func(TYPE_BOUND_TYPE, META_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                TypeBound.contra((Type) args[0])
            }
        }
        define 'invariant', func(TYPE_BOUND_TYPE, META_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                TypeBound.invar((Type) args[0])
            }
        }
        define 'covariant?', func(Logic.BOOLEAN_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((TypeBound) args[0]).variance == TypeBound.Variance.COVARIANT)
            }
        }
        define 'contravariant?', func(Logic.BOOLEAN_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((TypeBound) args[0]).variance == TypeBound.Variance.CONTRAVARIANT)
            }
        }
        define 'invariant?', func(Logic.BOOLEAN_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((TypeBound) args[0]).variance == TypeBound.Variance.INVARIANT)
            }
        }
        define 'get_type', func(META_TYPE, TYPE_BOUND_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((TypeBound) args[0]).type
            }
        }
    }

    static Type unmeta(Type t) {
        t instanceof GenericType && t.base == META_TYPE ? t.arguments[0] : t
    }
}
