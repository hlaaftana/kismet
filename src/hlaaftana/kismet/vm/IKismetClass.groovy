package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.type.Type

@CompileStatic
interface IKismetClass<T extends IKismetObject> extends Type {
	boolean isInstance(IKismetObject object)

	String getName()

	T cast(IKismetObject object)

	IKismetObject propertyGet(T obj, String name)

	IKismetObject propertySet(T obj, String name, IKismetObject value)

	IKismetObject subscriptGet(T obj, IKismetObject key)

	IKismetObject subscriptSet(T obj, IKismetObject key, IKismetObject value)

	IKismetObject call(T obj, IKismetObject[] args)

	T construct(IKismetObject[] args)
}