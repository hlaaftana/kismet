package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.CannotOperateException

@CompileStatic
class BasicClass<T extends IKismetObject> implements IKismetClass<T> {
	String name

	BasicClass(String name) { this.name = name }

	boolean isInstance(IKismetObject object) {
		object.kismetClass() == this
	}

	T cast(IKismetObject object) {
		if (!isInstance(object))
			throw new CannotOperateException("cast to ${object.kismetClass()}", "class $name")
		(T) object
	}
}
