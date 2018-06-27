package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.CannotOperateException
import org.codehaus.groovy.runtime.DefaultGroovyMethods

@CompileStatic
class BasicClass<T extends IKismetObject> implements IKismetClass<T>, IKismetObject<IKismetClass<T>> {
	String name

	BasicClass(String name) { this.name = name }

	IKismetClass kismetClass() { MetaKismetClass.INSTANCE }

	IKismetClass<T> inner() { this }

	boolean isInstance(IKismetObject object) {
		object.kismetClass() == this
	}

	T cast(IKismetObject object) {
		if (!isInstance(object))
			throw new CannotOperateException("cast to ${object.kismetClass()}", "class $name")
		(T) object
	}

	IKismetObject propertyGet(T obj, String name) {
		Kismet.model(DefaultGroovyMethods.getAt(obj, name))
	}

	IKismetObject propertySet(T obj, String name, IKismetObject value) {
		DefaultGroovyMethods.putAt(obj, name, value.inner())
		Kismet.NULL
	}

	IKismetObject subscriptGet(T obj, IKismetObject key) {
		Kismet.model DefaultGroovyMethods.invokeMethod(obj, 'getAt', [key.inner()] as Object[])
	}

	IKismetObject subscriptSet(T obj, IKismetObject key, IKismetObject value) {
		Kismet.model DefaultGroovyMethods.invokeMethod(obj, 'putAt', [key.inner(), value.inner()] as Object[])
	}

	IKismetObject call(T obj, IKismetObject[] args) {
		Kismet.model DefaultGroovyMethods.invokeMethod(obj, 'call', args*.inner())
	}

	T construct(IKismetObject[] args) {
		throw new CannotOperateException('construct', "class $name")
	}
}
