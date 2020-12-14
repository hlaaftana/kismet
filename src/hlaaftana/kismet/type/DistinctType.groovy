package hlaaftana.kismet.type

import groovy.transform.CompileStatic

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
}
