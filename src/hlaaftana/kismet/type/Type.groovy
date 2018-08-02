package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
interface Type {
	final AnyType ANY = AnyType.INSTANCE
	final NoType NONE = NoType.INSTANCE
	TypeRelation relation(Type other)
}
