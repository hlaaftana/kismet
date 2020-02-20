package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class TypeBound {
	Type type
	Variance variance

	TypeBound(Type type, Variance variance = Variance.COVARIANT) {
		this.type = type
		this.variance = variance
	}

	static TypeBound co(Type typ) { new TypeBound(typ) }
	static TypeBound contra(Type typ) { new TypeBound(typ, Variance.CONTRAVARIANT) }
	static TypeBound invar(Type typ) { new TypeBound(typ, Variance.INVARIANT) }

	TypeBound positive() {
		new TypeBound(type)
	}

	TypeBound negative() {
		new TypeBound(type, Variance.CONTRAVARIANT)
	}

	boolean assignableFrom(Type other) {
		variance.apply(type.relation(other)).assignableFrom
	}

	String toString() {
		variance.name().toLowerCase() + ' ' + type.toString()
	}

	enum Variance {
		COVARIANT {
			TypeRelation apply(TypeRelation rel) {
				rel
			}
		}, CONTRAVARIANT {
			TypeRelation apply(TypeRelation rel) {
				~rel
			}
		}, INVARIANT {
			TypeRelation apply(TypeRelation rel) {
				rel.equal ? rel : TypeRelation.none()
			}
		}

		abstract TypeRelation apply(TypeRelation rel)
	}
}
