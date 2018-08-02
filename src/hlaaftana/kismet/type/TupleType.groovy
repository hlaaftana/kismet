package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class TupleType implements Type {
	static final TupleType ANY = new TupleType(null)
	Type[] elements

	TupleType(Type[] elements) {
		this.elements = elements
	}

	String toString() { "Tuple[${elements.join(', ')}]" }

	TypeRelation relation(Type other) {
		if (other instanceof TupleType && elements.length == other.elements.length) {
			TypeRelation max = elements[0].relation(other.elements[0])
			if (max.none) return TypeRelation.none()
			for (int i = 1; i < elements.length; ++i) {
				def rel = elements[i].relation(other.elements[i])
				if (rel.none) return TypeRelation.none()
				if ((rel.super ^ max.super) || (rel.sub ^ max.sub))
					return TypeRelation.none()
				if (rel.value > max.value) max = rel
			}
			max
		} else TypeRelation.none()
	}

	boolean equals(obj) { obj instanceof TupleType && Arrays.equals(elements, obj.elements) }
}
