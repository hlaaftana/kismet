package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.type.MetaType
import hlaaftana.kismet.type.Type

@CompileStatic
class ClassObject<T extends IKismetClass> implements IKismetObject<T> {
	T it

	ClassObject(T it) { this.it = it }

	MetaKismetClass kismetClass() { MetaKismetClass.INSTANCE }

	Type getType() { new MetaType(it) }

	T inner() { it }
}