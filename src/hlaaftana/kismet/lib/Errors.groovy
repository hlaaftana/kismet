package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.ExprBuilder
import hlaaftana.kismet.call.Expression
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.call.Instruction
import hlaaftana.kismet.call.Instructor
import hlaaftana.kismet.call.NameExpression
import hlaaftana.kismet.call.NumberExpression
import hlaaftana.kismet.call.Template
import hlaaftana.kismet.call.TypeChecker
import hlaaftana.kismet.call.TypedExpression
import hlaaftana.kismet.exceptions.KismetAssertionError
import hlaaftana.kismet.exceptions.KismetRuntimeException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KInt32
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.WrapperKismetObject

import static hlaaftana.kismet.lib.Functions.TEMPLATE_TYPE
import static hlaaftana.kismet.lib.Functions.TYPE_CHECKER_TYPE
import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.call.ExprBuilder.*

@CompileStatic
class Errors extends LibraryModule {
    TypedContext typed = new TypedContext("errors")
    Context defaultContext = new Context()
    
    Errors() {
        define 'raise', func(Type.NONE, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                throw new KismetRuntimeException(args[0])
            }
        }
        define 'raise', func(Type.NONE), new Function() {
            IKismetObject call(IKismetObject... args) {
                throw new KismetRuntimeException()
            }
        }
        define 'catch', Functions.instr(Type.ANY, Type.ANY), new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                try {
                    args[0].evaluate(m)
                    Kismet.NULL
                } catch (KismetRuntimeException ex) {
                    ex.obj // this can be null, or double null even
                }
            }
        }
        define 'raw_try', Functions.instr(Type.ANY, Type.ANY, NumberType.Int32, Type.ANY), new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                try {
                    args[0].evaluate(m)
                } catch (KismetRuntimeException ex) {
                    m.set(((KInt32) args[1].evaluate(m)).inner, ex.obj)
                    args[2].evaluate(m)
                }
            }
        }
        define 'try_catch', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                if (args[1] !instanceof NameExpression)
                    throw new UnexpectedSyntaxException('try_catch must have name argument')
                NameExpression vn = (NameExpression) args[1]
                int vi = context.addVariable(vn.text).id
                call(name('raw_try'), args[0],
                        new NumberExpression(vi), args[2]).type(context)
            }
        }
        define 'assert', Functions.INSTRUCTOR_TYPE, new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                IKismetObject val = Kismet.NULL
                for (e in args) if (!(val = e.evaluate(m)))
                    throw new KismetAssertionError('Assertion failed for instruction ' +
                            e + '. Value was ' + val)
                val
            }
        }
        define 'assert_not', Functions.INSTRUCTOR_TYPE, new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                IKismetObject val = Kismet.NULL
                for (e in args) if ((val = e.evaluate(m)))
                    throw new KismetAssertionError('Assertion failed for instruction ' +
                            e + '. Value was ' + val)
                val
            }
        }
        define 'assert_is', Functions.INSTRUCTOR_TYPE, new Instructor() {
            @Override
            IKismetObject call(Memory c, Instruction... exprs) {
                IKismetObject val = exprs[0].evaluate(c), latest
                for (e in exprs.tail()) if (val != (latest = e.evaluate(c)))
                    throw new KismetAssertionError('Assertion failed. Value was ' +
                            'expected to be ' + val +
                            ' but was ' + latest)
                val
            }
        }
        define 'assert_is_not', Functions.INSTRUCTOR_TYPE, new Instructor() {
            @Override
            IKismetObject call(Memory c, Instruction... exprs) {
                def values = [exprs[0].evaluate(c)]
                IKismetObject r, latest
                for (e in exprs.tail()) if ((r = values.find((latest = e.evaluate(c)).&equals)))
                    throw new KismetAssertionError('Assertion failed. Value was ' +
                            'expected NOT to be ' + r +
                            ' but was ' + latest)
                new WrapperKismetObject(values)
            }
        }
    }
}
