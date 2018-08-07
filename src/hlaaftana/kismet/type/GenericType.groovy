package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class GenericType implements Type {
	SingleType base
	Type[] bounds

	GenericType(SingleType base, Type[] bounds) {
		this.base = base
		this.bounds = bounds
	}

	String toString() {
		def res = new StringBuilder(base.toString()).append((char) '[')
		for (int i = 0; i < bounds.length; ++i) {
			if (i != 0) res.append(', ')
			res.append(bounds[i].toString())
		}
		res.append((char) ']').toString()
	}

	TypeRelation relation(Type other) {
		if (other instanceof GenericType && base == other.base) {
			if (null == bounds) {
				if (null == other.bounds) TypeRelation.none()
				else TypeRelation.supertype(other.bounds.length)
			} else if (bounds.length == other.bounds.length) {
				TypeRelation max = bounds[0].relation(other.bounds[0])
				if (max.none) return TypeRelation.none()
				for (int i = 1; i < bounds.length; ++i) {
					def rel = bounds[i].relation(other.bounds[i])
					if (rel.none) return TypeRelation.none()
					if ((rel.super ^ max.super) || (rel.sub ^ max.sub))
						return TypeRelation.none()
					if (rel.value > max.value) max = rel
				}
				max
			} else TypeRelation.none() //TypeRelation.some(bounds.length - other.bounds.length)
		} else if (base == other) {
			if (null == bounds) TypeRelation.equal()
			else TypeRelation.subtype(1)
		} else TypeRelation.none()
	}

	boolean equals(other) { other instanceof GenericType && base == other.base && Arrays.equals(bounds, other.bounds) }

	boolean losesAgainst(Type other) {
		def t = (GenericType) other
		if (base.losesAgainst(t.base)) return true
		//if (bounds.length < t.bounds.length) return true
		for (int i = 0; i < bounds.length; ++i)
			if (bounds[i].losesAgainst(t.bounds[i])) return true
		false
	}

	Type getAt(int i) {
		i >= 0 && i < bounds.length ? bounds[i] : null
	}
}
