package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.UnexpectedTypeException

@CompileStatic
class ListType implements Type {
	Type bound

	ListType(Type bound = Type.NONE) {
		this.bound = bound
	}

	void feed(Type type) {
		def rel = bound.relation(type)
		if (rel.sub) bound = type
		else if (rel.none) throw new UnexpectedTypeException('Type ' + type + ' is incompatible with list with bound ' + bound)
	}

	TypeRelation relation(Type other) {
		if (other instanceof ListType) {
			bound.relation(other.bound)
		} else TypeRelation.none()
	}

	String toString() { "List[$bound]" }
}
