package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
interface Type extends IKismetObject<Type> {
	static final AnyType ANY = AnyType.INSTANCE
	static final NoType NONE = NoType.INSTANCE
	TypeRelation relation(Type other)
}

@CompileStatic
interface WeakableType extends Type {
	abstract TypeRelation weakRelation(Type other)
}

@CompileStatic
abstract class AbstractType implements WeakableType {
	Type inner() { this }

	TypeRelation relation(Type other) {
		def rel = weakRelation(other)
		if (!rel.none) return rel
		other instanceof WeakableType ? ~other.weakRelation(this) : rel
	}

	TypeBound positive() { TypeBound.co(this) }
	TypeBound negative() { TypeBound.contra(this) }
}

