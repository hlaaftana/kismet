package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetDistinct

@CompileStatic
class DistinctType extends AbstractType {
    String name
    Type inner

    DistinctType(Type inner) {
        this.inner = inner
    }

    String toString() {
        name ?: "distinct " + inner
    }

    @Override
    TypeRelation weakRelation(Type other) {
        if (other instanceof AnyDistinctType) inner.relation(other.match)
        else if (this == other) TypeRelation.equal()
        else TypeRelation.none()
    }

    boolean check(IKismetObject obj) {
        obj instanceof KismetDistinct && obj.type == this && inner.check(obj.inner)
    }
}

@CompileStatic
class AnyDistinctType extends AbstractType {
    static final AnyDistinctType ANY = new AnyDistinctType(Type.ANY)
    Type match

    AnyDistinctType(Type match) {
        this.match = match
    }

    String toString() {
        "any distinct " + match
    }

    TypeRelation weakRelation(Type other) {
        if (other instanceof AnyDistinctType) match.relation(other.match)
        else if (other instanceof DistinctType) match.relation(other.inner)
        else TypeRelation.none()
    }

    boolean check(IKismetObject obj) {
        obj instanceof KismetDistinct && match.check(obj.inner)
    }
}
