package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
@Singleton(property = 'INSTANCE')
class NoType implements Type {
	TypeRelation relation(Type other) {
		TypeRelation.subtype(Integer.MAX_VALUE)
	}

	boolean losesAgainst(Type other) { true }

	String toString() { 'None' }
}
