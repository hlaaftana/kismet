package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
@Singleton(property = 'INSTANCE')
class AnyType implements Type {
	TypeRelation relation(Type other) {
		TypeRelation.supertype(Integer.MAX_VALUE)
	}

	String toString() { 'Any' }
}
