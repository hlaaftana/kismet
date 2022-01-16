package hlaaftana.kismet.vm

import groovy.transform.CompileStatic

@CompileStatic
interface IKismetObject<T> {
	T inner()
}