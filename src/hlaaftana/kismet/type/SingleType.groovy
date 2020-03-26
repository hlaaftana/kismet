package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class SingleType extends AbstractType {
	String name
	TypeBound[] bounds

	SingleType(String name, TypeBound[] bounds = null) {
		this.name = name
		this.bounds = bounds
	}

	Type generic(Type... genericArgs) {
		if (!boundsMatch(genericArgs)) null
		else if (null == bounds) this
		else new GenericType(this, genericArgs)
	}

	TypeRelation weakRelation(Type other) {
		TypeRelation.some(other == this)
	}

	String toString() { name }

	boolean boundsMatch(Type[] arr) {
		if (null == bounds) return true
		if (arr.length != bounds.length) return false
		for (int i = 0; i < bounds.length; ++i) {
			if (!bounds[i].assignableFrom(arr[i])) return false
		}
		true
	}
}
