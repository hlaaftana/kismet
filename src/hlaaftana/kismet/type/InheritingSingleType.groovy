package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class InheritingSingleType extends SingleType {
	SingleType parent

	InheritingSingleType(SingleType parent, String name) {
		super(name, null)
		this.parent = parent
	}

	TypeRelation weakRelation(Type other) {
		if (other == this) return TypeRelation.equal()
		def rel = null == parent ? TypeRelation.none() : other.relation(parent)
		rel.none ? rel : TypeRelation.some(rel.toSome() + 1)
	}

	String toString() { name }
}
