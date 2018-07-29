package hlaaftana.kismet.type

interface Type {
	final AnyType ANY = AnyType.INSTANCE
	final NoType NONE = NoType.INSTANCE
	TypeRelation relation(Type other)
}
