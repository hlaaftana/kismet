package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
interface IKismetObject<T> {
	IKismetClass kismetClass()
	T inner()
	IKismetObject getProperty(String name)
	IKismetObject setProperty(String name, IKismetObject value)
	IKismetObject getAt(IKismetObject obj)
	IKismetObject putAt(IKismetObject obj, IKismetObject value)
	IKismetObject call(IKismetObject[] args)
}