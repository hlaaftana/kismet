package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Expression
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.call.GroovyFunction
import hlaaftana.kismet.call.Instruction
import hlaaftana.kismet.call.Instructor
import hlaaftana.kismet.call.Template
import hlaaftana.kismet.call.TypeChecker
import hlaaftana.kismet.call.TypedTemplate
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.lib.CollectionsIterators
import hlaaftana.kismet.lib.IteratorIterable
import hlaaftana.kismet.lib.Logic
import hlaaftana.kismet.lib.Reflection
import hlaaftana.kismet.lib.Strings
import hlaaftana.kismet.lib.Types
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeBound

import static hlaaftana.kismet.lib.Functions.*

@CompileStatic
class KismetValue implements IKismetObject {
    Type type
    Object inner

    Object inner() { inner }

    KismetValue(Object value, Type t = null) {
        inner = value
        if (null != t) type = t
        else if (value instanceof Number) type = NumberType.from(value)
        else if (value instanceof Character) type = NumberType.Char
        else if (value instanceof CharSequence) type = Strings.STRING_TYPE
        else if (value instanceof Boolean) type = Logic.BOOLEAN_TYPE
        else if (value.getClass().isArray() || value instanceof Tuple)
            type = TupleType.BASE
        else if (value instanceof List) type = CollectionsIterators.LIST_TYPE
        else if (value instanceof Map) type = CollectionsIterators.MAP_TYPE
        else if (value instanceof Set) type = CollectionsIterators.SET_TYPE
        else if (value instanceof Closure) {
            inner = new GroovyFunction((Closure) value)
            type = FUNCTION_TYPE
        } else if (value instanceof Iterator) {
            if (value !instanceof Iterable)
                value = new IteratorIterable((Iterator) value)
            type = CollectionsIterators.CLOSURE_ITERATOR_TYPE
        } else if (value instanceof Function) type = FUNCTION_TYPE//func(Type.NONE, new TupleType(new Type[0]).withVarargs(Type.ANY))
        else if (value instanceof Template) type = TEMPLATE_TYPE
        else if (value instanceof TypeChecker) type = TYPE_CHECKER_TYPE
        else if (value instanceof Instructor) type = INSTRUCTOR_TYPE
        else if (value instanceof TypedTemplate) type = TYPED_TEMPLATE_TYPE
        else if (value instanceof Type) type = new GenericType(Types.META_TYPE, value)
        else if (value instanceof Expression) type = Reflection.EXPRESSION_TYPE
        else if (value instanceof Instruction) type = Reflection.INSTRUCTION_TYPE
        else if (value instanceof Memory) type = Reflection.MEMORY_TYPE
        else if (value instanceof TypeBound) type = Types.TYPE_BOUND_TYPE
        else throw new UnexpectedValueException('give type for value ' + value + ' with class ' + value.getClass())
    }

    static KismetValue from(obj) {
        if (obj instanceof KismetValue) obj
        else new KismetValue(obj)
    }
}
