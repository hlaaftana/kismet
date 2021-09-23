package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
@Singleton(property = 'INSTANCE')
class NoType extends AbstractType {
	TypeRelation weakRelation(Type other) {
		other == this ? TypeRelation.equal() : TypeRelation.subtype(Integer.MAX_VALUE)
	}

	String toString() { 'None' }

	boolean check(IKismetObject obj) { null == obj || null == obj.inner() }
}
