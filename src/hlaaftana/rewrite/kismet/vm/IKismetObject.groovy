package hlaaftana.rewrite.kismet.vm

import groovy.transform.CompileStatic

@CompileStatic
interface IKismetObject<T> {
	IKismetClass kismetClass()

	T inner()
}