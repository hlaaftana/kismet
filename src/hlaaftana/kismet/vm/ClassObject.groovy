package hlaaftana.kismet.vm

import groovy.transform.CompileStatic

@CompileStatic
class ClassObject<T extends IKismetClass> implements IKismetObject<T> {
	T it

	ClassObject(T it) { this.it = it }

	MetaKismetClass kismetClass() { MetaKismetClass.INSTANCE }

	T inner() { it }
}