package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import hlaaftana.kismet.exceptions.WrongGenericsException

@CompileStatic
@EqualsAndHashCode
class GenericType extends AbstractType {
	SingleType base
	Type[] arguments

	GenericType(SingleType base, Type[] arguments) {
		this.base = base
		this.arguments = arguments
		if (arguments != null && base.bounds != null) {
			if (arguments.length != base.bounds.length)
				throw new WrongGenericsException("Bounds length do not match")
			for (int i = 0; i < arguments.length; ++i)
				if (!base.bounds[i].assignableFrom(arguments[i]))
					throw new WrongGenericsException("Type ${arguments[i]} is not assignable to bound ${base.bounds[i]}")
		}
	}

	String toString() {
		def b = base.toString()
		def res = new StringBuilder(b).append((char) '[')
		for (int i = 0; i < arguments.length; ++i) {
			if (i != 0) res.append(', ')
			res.append(arguments[i].toString())
		}
		res.append((char) ']').toString()
	}

	TypeRelation weakRelation(Type other) {
		if (other instanceof GenericType && base == other.base) {
			if (null == arguments) {
				if (null == other.arguments) TypeRelation.equal()
				else TypeRelation.supertype(other.size())
			} else if (null == other.arguments) {
				TypeRelation.subtype(size())
			} else if (indefinite || size() == other.size()) {
				if (size() == 0) return TypeRelation.equal()
				TypeRelation min
				def variance = varianceAt(0)
				def rel = variance.apply(this[0].relation(((GenericType) other)[0]))
				if (rel.none) return TypeRelation.none()
				min = rel
				for (int i = 1; i < size(); ++i) {
					variance = varianceAt(1)
					rel = variance.apply(this[i].relation(((GenericType) other)[i]))
					if (rel.none) return TypeRelation.none()
					if (rel.toSome() < min.toSome()) min = rel
				}
				min
			} else TypeRelation.none() //TypeRelation.some(bounds.length - other.bounds.length)
		} else if (base == other) {
			if (null == arguments) TypeRelation.equal()
			else TypeRelation.subtype(1)
		} else TypeRelation.none()
	}

	int size() { arguments.length }

	boolean isIndefinite() { false }

	Type getAt(int i) {
		i >= 0 && i < arguments.length ? arguments[i] : null
	}

	TypeBound.Variance varianceAt(int i) {
		null == base.bounds ? TypeBound.Variance.COVARIANT : base.bounds[i].variance
	}
}
