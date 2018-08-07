package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
interface Type {
	final AnyType ANY = AnyType.INSTANCE
	final NoType NONE = NoType.INSTANCE
	TypeRelation relation(Type other)
	/**
	 * @param other a relative type
	 * @return true if other is more specialized (more of a subtype)
	 */
	boolean losesAgainst(Type other)
}
