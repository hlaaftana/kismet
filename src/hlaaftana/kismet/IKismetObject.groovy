package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
interface IKismetObject<T> {
	IKismetClass kismetClass()
	T inner()
	IKismetObject propertyGet(String name)
	IKismetObject propertySet(String name, IKismetObject value)
	IKismetObject getAt(IKismetObject obj)
	IKismetObject putAt(IKismetObject obj, IKismetObject value)
	IKismetObject call(IKismetObject[] args)
}