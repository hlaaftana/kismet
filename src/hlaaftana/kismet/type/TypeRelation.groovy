package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class TypeRelation {
	private static final int NONE = 0, SUB = 1, SUPER = 2, EQUAL = 4
	private static final TypeRelation EQ = new TypeRelation(EQUAL, 0), NO = new TypeRelation(NONE, 0)
	int kind, value

	private TypeRelation(int kind, int value) {
		this.kind = kind
		this.value = value
	}

	static TypeRelation subtype(int val) { new TypeRelation(SUB, val) }
	static TypeRelation supertype(int val) { new TypeRelation(SUPER, val) }
	static TypeRelation equal() { EQ }
	static TypeRelation none() { NO }

	static TypeRelation some(int val) {
		if (val == 0) EQ
		else if (val < 0) supertype(val)
		else subtype(val)
	}

	static TypeRelation some(boolean val) {
		val ? EQ : NO
	}

	boolean worse(TypeRelation o) {
		(none && kind != o.kind) ||
				((isSuper() || sub) && o.kind == kind && value > o.value)
	}

	boolean better(TypeRelation o) {
		(equal && kind != o.kind) ||
				((isSuper() || sub) && o.kind == kind && value < o.value)
	}

	Boolean subber() {
		if (sub) true
		else if (isSuper()) false
		else null
	}

	boolean equals(o) {
		o instanceof TypeRelation && o.kind == kind && o.value == value
	}

	TypeRelation bitwiseNegate() {
		sub ? supertype(value) : isSuper() ? subtype(value) : this
	}

	boolean isSub() { kind == SUB }
	boolean isSuper() { kind == SUPER }
	boolean isEqual() { kind == EQUAL }
	boolean isNone() { kind == NONE }
	boolean isAssignableFrom() { isSuper() || equal }
	boolean isAssignableTo() { sub || equal }
}

