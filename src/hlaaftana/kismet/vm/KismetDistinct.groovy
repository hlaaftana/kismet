package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.type.DistinctType

@CompileStatic
class KismetDistinct<T> implements IKismetObject<T> {
    IKismetObject<T> inner
    DistinctType type

    KismetDistinct(DistinctType type, IKismetObject inner) {
        this.type = type
        this.inner = inner
    }

    T inner() { inner.inner() }

    int hashCode() { inner.hashCode() }

    boolean equals(obj) { obj instanceof KismetDistinct && type == obj.type && inner == obj.inner }

    String toString() { inner.toString() }
}
