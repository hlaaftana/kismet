package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.UnexpectedTypeException
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
class ParameterType extends AbstractType {
    Type inner

    ParameterType(Type inner = Type.ANY) {
        this.inner = inner
    }

    void trySet(Type t) {
        if (t.relation(inner).assignableTo) inner = t
        else throw new UnexpectedTypeException('generic match failed for ' + this + ', tried to get ' + inner + ' but got ' + t)
    }

    String toString() {
        "Parameter[" + inner + "]"
    }

    @Override
    TypeRelation weakRelation(Type other) {
        inner.relation(other)
    }

    boolean check(IKismetObject obj) { inner.check(obj) }
}
