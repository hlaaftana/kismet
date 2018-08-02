package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class MetaType implements Type {
	static final MetaType ALL = new MetaType()
	Type bound

	MetaType(Type bound = Type.ANY) {
		this.bound = bound
	}

	TypeRelation relation(Type other) {
		other instanceof MetaType ? bound.relation(other.bound) : TypeRelation.none()
	}

	boolean equals(obj) { obj instanceof MetaType && bound == obj.bound }

	String toString() { "Meta[$bound]" }
}
