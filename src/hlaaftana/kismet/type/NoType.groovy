package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
@Singleton(property = 'INSTANCE')
class NoType extends AbstractType {
	TypeRelation weakRelation(Type other) {
		other == this ? TypeRelation.equal() : TypeRelation.subtype(Integer.MAX_VALUE)
	}

	boolean losesAgainst(Type other) { true }

	String toString() { 'None' }
}
