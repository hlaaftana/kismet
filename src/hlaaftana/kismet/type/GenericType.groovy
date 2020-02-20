package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.WrongGenericsException

@CompileStatic
class GenericType extends AbstractType {
	SingleType base
	Type[] bounds

	GenericType(SingleType base, Type[] bounds) {
		this.base = base
		this.bounds = bounds
		if (bounds != null && base.bounds != null) {
			if (bounds.length != base.bounds.length)
				throw new WrongGenericsException("Bounds length do not match")
			for (int i = 0; i < bounds.length; ++i)
				if (!base.bounds[i].assignableFrom(bounds[i]))
					throw new WrongGenericsException("Type ${bounds[i]} is not assignable to bound ${base.bounds[i]}")
		}
	}

	String toString() {
		def b = base.toString()
		def res = new StringBuilder(b).append((char) '[')
		for (int i = 0; i < bounds.length; ++i) {
			if (i != 0) res.append(', ')
			res.append(bounds[i].toString())
		}
		res.append((char) ']').toString()
	}

	TypeRelation weakRelation(Type other) {
		if (other instanceof GenericType && base == other.base) {
			if (null == bounds) {
				if (null == other.bounds) TypeRelation.equal()
				else TypeRelation.supertype(other.size())
			} else if (indefinite || size() == other.size()) {
				TypeRelation max
				TypeBound.Variance variance = varianceAt(0)
				TypeRelation rel = variance.apply(this[0].relation(((GenericType) other)[0]))
				if (rel.none) return TypeRelation.none()
				max = rel
				for (int i = 1; i < size(); ++i) {
					variance = varianceAt(1)
					rel = variance.apply(this[i].relation(((GenericType) other)[i]))
					if (rel.none) return TypeRelation.none()
					if (!max.equal && !rel.equal && ((rel.super ^ max.super) || (rel.sub ^ max.sub)))
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
		for (int i = 0; i < size(); ++i)
			if (this[i].losesAgainst(t[i])) return true
		false
	}

	int size() { bounds.length }

	boolean isIndefinite() { false }

	Type getAt(int i) {
		i >= 0 && i < bounds.length ? bounds[i] : null
	}

	TypeBound.Variance varianceAt(int i) {
		base.bounds[i].variance
	}
}
