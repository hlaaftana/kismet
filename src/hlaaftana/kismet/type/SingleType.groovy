package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class SingleType implements Type {
	String name
	Type[] bounds

	SingleType(String name, Type[] bounds = null) {
		this.name = name
		this.bounds = bounds
	}

	Type generic(Type... genericArgs) {
		if (!boundsMatch(bounds, genericArgs)) null
		else if (null == bounds) this
		else new GenericType(this, genericArgs)
	}

	TypeRelation relation(Type other) {
		TypeRelation.some(other == this)
	}

	String toString() { name }

	boolean losesAgainst(Type other) { false }

	static boolean boundsMatch(Type[] b1, Type[] b2) {
		int len, len2
		if ((null == b1 || (len = b1.length) == 0) && (null == b2 || (len2 = b2.length) == 0)) return true
		if (len != len2) return false
		TypeRelation max = b1[0].relation(b2[0])
		if (max.none) return false
		for (int i = 1; i < len; ++i) {
			def rel = b1[i].relation(b2[i])
			if (rel.none || rel.super ^ max.super || rel.sub ^ max.sub)
				return false
			if (rel.value > max.value) max = rel
		}
		true
	}
}
