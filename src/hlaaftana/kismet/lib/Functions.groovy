package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.type.*
import hlaaftana.kismet.vm.IKismetObject

import static hlaaftana.kismet.call.ExprBuilder.call

@CompileStatic
class Functions extends NativeModule {
    static final SingleType TEMPLATE_TYPE = new SingleType('Template') {
        boolean check(IKismetObject obj) { obj instanceof Template }
        boolean checkGenerics(IKismetObject obj, Type... args) { true }
    },
        TYPE_CHECKER_TYPE = new SingleType('TypeChecker') {
            boolean check(IKismetObject obj) { obj instanceof TypeChecker }
            boolean checkGenerics(IKismetObject obj, Type... args) { true }
        },
        // todo attach types to routine values
        INSTRUCTOR_TYPE = new SingleType('Instructor', [+TupleType.BASE, -Type.NONE] as TypeBound[]) {
            boolean check(IKismetObject obj) { obj instanceof Instructor }
            boolean checkGenerics(IKismetObject obj, Type... args) { true }
        },
        TYPED_TEMPLATE_TYPE = new SingleType('TypedTemplate', [+TupleType.BASE, -Type.NONE] as TypeBound[]) {
            boolean check(IKismetObject obj) { obj instanceof TypedTemplate }
            boolean checkGenerics(IKismetObject obj, Type... args) { true }
        },
        FUNCTION_TYPE = new SingleType('Function', [+TupleType.BASE, -Type.NONE] as TypeBound[]) {
            boolean check(IKismetObject obj) { obj instanceof Function }
            boolean checkGenerics(IKismetObject obj, Type... args) { true }
        }

    Functions() {
        super("functions")
        define FUNCTION_TYPE
        define TEMPLATE_TYPE
        define INSTRUCTOR_TYPE
        define TYPE_CHECKER_TYPE
        define TYPED_TEMPLATE_TYPE
        define 'call', FUNCTION_TYPE.generic(new TupleType(FUNCTION_TYPE).withVarargs(Type.ANY), Type.NONE), func { IKismetObject... args ->
            def x = args[1].inner() as Object[]
            def ar = new IKismetObject[x.length]
            for (int i = 0; i < ar.length; ++i) {
                ar[i] = Kismet.model(x[i])
            }
            ((Function) args[0]).call(ar)
        }
        define 'identity',  Function.IDENTITY
        define 'consume',  func { IKismetObject... args -> ((Function) args[1]).call(args[0]) }
        define 'tap',  func { IKismetObject... args -> ((Function) args[1]).call(args[0]); args[0] }
        define 'compose',  func { IKismetObject... args ->
            funcc { ... a ->
                def r = args[0]
                for (int i = args.length - 1; i >= 0; --i) {
                    r = ((Function) args[i]).call(r)
                }
                r
            }
        }
        define 'memoize',  func { IKismetObject... args ->
            def x = args[0]
            Map<IKismetObject[], IKismetObject> results = new HashMap<>()
            func { IKismetObject... a ->
                def p = results.get(a)
                null == p ? ((Function) x).call(a) : p
            }
        }
        define 'spread',  new Template() {
            Expression transform(Parser parser, Expression... args) {
                def m = new ArrayList<Expression>(args.length - 1)
                for (int i = 1; i < args.length; ++i) m.add(args[i])
                call(args[0], new ListExpression(m))
            }
        }
    }

    static GenericType func(Type returnType, Type... args) {
        new GenericType(FUNCTION_TYPE, new TupleType(args), returnType)
    }

    static GenericType instr(Type returnType, Type... args) {
        new GenericType(INSTRUCTOR_TYPE, new TupleType(args), returnType)
    }

    static GenericType typedTmpl(Type returnType, Type... args) {
        new GenericType(TYPED_TEMPLATE_TYPE, new TupleType(args), returnType)
    }

    static GroovyFunction func(boolean pure = false, Closure c) {
        def result = new GroovyFunction(false, c)
        result.pure = pure
        result
    }

    static GroovyFunction funcc(boolean pure = false, Closure c) {
        def result = new GroovyFunction(true, c)
        result.pure = pure
        result
    }
}
