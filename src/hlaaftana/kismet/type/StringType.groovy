package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
@Singleton(property = 'INSTANCE')
class StringType implements Type {
	TypeRelation relation(Type other) {
		TypeRelation.some(other == this)
	}

	String toString() { 'String' }
}
