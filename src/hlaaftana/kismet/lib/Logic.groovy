package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.call.Instruction
import hlaaftana.kismet.call.Instructor
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.SingleType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetBoolean
import hlaaftana.kismet.vm.Memory

import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.lib.Functions.instr

@CompileStatic
class Logic extends NativeModule {
    static final SingleType BOOLEAN_TYPE = new SingleType('Boolean')  {
        boolean check(IKismetObject obj) { obj instanceof KismetBoolean }
        boolean checkGenerics(IKismetObject obj, Type... args) { true }
    }

    Logic() {
        super("logic")
        define BOOLEAN_TYPE
        define 'true', BOOLEAN_TYPE, KismetBoolean.TRUE
        define 'false', BOOLEAN_TYPE, KismetBoolean.FALSE
        define 'and', new GenericType(Functions.INSTRUCTOR_TYPE, new TupleType(new Type[0]).withVarargs(BOOLEAN_TYPE), BOOLEAN_TYPE), new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                KismetBoolean last = KismetBoolean.TRUE
                for (it in args) if (!(last = (KismetBoolean) it.evaluate(m)).inner) return KismetBoolean.FALSE
                last
            }
        }
        define 'or', new GenericType(Functions.INSTRUCTOR_TYPE, new TupleType(new Type[0]).withVarargs(BOOLEAN_TYPE), BOOLEAN_TYPE), new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                KismetBoolean last = KismetBoolean.FALSE
                for (it in args) if ((last = (KismetBoolean) it.evaluate(m)).inner) return KismetBoolean.TRUE
                last
            }
        }
        define 'and', instr(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                KismetBoolean.from(((KismetBoolean) args[0].evaluate(m)).inner && ((KismetBoolean) args[1].evaluate(m)).inner)
            }
        }
        define 'or', instr(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                KismetBoolean.from(((KismetBoolean) args[0].evaluate(m)).inner || ((KismetBoolean) args[1].evaluate(m)).inner)
            }
        }
        // instructors have more priority, so we can also define function versions hopefully
        define 'and', new GenericType(Functions.FUNCTION_TYPE, new TupleType(new Type[0]).withVarargs(BOOLEAN_TYPE), BOOLEAN_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean last = KismetBoolean.TRUE
                for (it in args) if (!(last = (KismetBoolean) it).inner) return KismetBoolean.FALSE
                last
            }
        }
        define 'or', new GenericType(Functions.FUNCTION_TYPE, new TupleType(new Type[0]).withVarargs(BOOLEAN_TYPE), BOOLEAN_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean last = KismetBoolean.FALSE
                for (it in args) if ((last = (KismetBoolean) it).inner) return KismetBoolean.TRUE
                last
            }
        }
        define 'and', func(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetBoolean) args[0]).inner && ((KismetBoolean) args[1]).inner)
            }
        }
        define 'or', func(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetBoolean) args[0]).inner || ((KismetBoolean) args[1]).inner)
            }
        }
        define 'xor', func(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(((KismetBoolean) args[0]).inner ^ ((KismetBoolean) args[1]).inner)
            }
        }
        define 'bool', func(BOOLEAN_TYPE, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0].inner() as boolean)
            }
        }
        alias 'bool', 'true?', '?'
        negated 'bool', 'false?'
        define 'not', func(BOOLEAN_TYPE, BOOLEAN_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(!((KismetBoolean) args[0]).inner)
            }
        }
    }
}
