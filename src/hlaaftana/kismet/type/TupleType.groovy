package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.TypedExpression

@CompileStatic
class TupleType extends GenericType {
	static final SingleType BASE = new SingleType('Tuple')

	TupleType(Type[] bounds) {
		super(BASE, bounds)
	}

	TupleType(TypedExpression[] zro) {
		super(BASE, new Type[zro.length])
		for (int i = 0; i < zro.length; ++i) bounds[i] = zro[i].type
	}

	String toString() { "Tuple[${bounds.join(', ')}]" }

	/*TypeRelation relation(Type other) {
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
	}*/

	boolean losesAgainst(Type other) {
		def t = (TupleType) other
		for (int i = 0; i < bounds.length; ++i) if (bounds[i].losesAgainst(t.bounds[i])) return true
		false
	}

	boolean equals(obj) { obj instanceof TupleType && Arrays.equals(bounds, obj.bounds) }
}
