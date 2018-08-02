package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.type.Type

@CompileStatic
interface IKismetObject<T> {
	IKismetClass kismetClass()

	Type getType()

	T inner()
}