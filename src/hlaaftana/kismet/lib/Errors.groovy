package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.call.Instruction
import hlaaftana.kismet.call.Instructor
import hlaaftana.kismet.exceptions.KismetAssertionError
import hlaaftana.kismet.exceptions.KismetException
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetString
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.WrapperKismetObject

import static hlaaftana.kismet.lib.Functions.func

@CompileStatic
class Errors extends LibraryModule {
    TypedContext typed = new TypedContext("errors")
    Context defaultContext = new Context()
    
    Errors() {
        define 'raise', func(Type.NONE, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                throw new KismetException(((KismetString) args[0]).inner())
            }
        }
        define 'raise', func(Type.NONE), new Function() {
            IKismetObject call(IKismetObject... args) {
                throw new KismetException()
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
                    throw new KismetAssertionError('Assertion failed for instruction ' +
                            e + '. Value was expected to be ' + val +
                            ' but was ' + latest)
                val
            }
        }
        define 'assert_isn\'t', Functions.INSTRUCTOR_TYPE, new Instructor() {
            @Override
            IKismetObject call(Memory c, Instruction... exprs) {
                def values = [exprs[0].evaluate(c)]
                IKismetObject r, latest
                for (e in exprs.tail()) if ((r = values.find((latest = e.evaluate(c)).&equals)))
                    throw new KismetAssertionError('Assertion failed for instruction ' +
                            e + '. Value was expected NOT to be ' + r +
                            ' but was ' + latest)
                new WrapperKismetObject(values)
            }
        }
    }
}
