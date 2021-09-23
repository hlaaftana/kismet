package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetDistinct

@CompileStatic
class DistinctType extends AbstractType {
    Type inner = null

    DistinctType(Type inner = null) {
        this.inner = null
    }

    String toString() {
        "Distinct[" + inner + "]"
    }

    @Override
    TypeRelation weakRelation(Type other) {
        if (this == other) TypeRelation.equal() else TypeRelation.none()
    }

    boolean check(IKismetObject obj) {
        obj instanceof KismetDistinct && obj.type == this && inner.check(obj.inner)
    }
}
