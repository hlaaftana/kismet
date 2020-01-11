package hlaaftana.kismet.vm

import groovy.transform.CompileStatic

@CompileStatic
class KismetBoolean implements IKismetObject<Boolean> {
	static KismetBoolean TRUE = new KismetBoolean(true), FALSE = new KismetBoolean(false)
	boolean inner

	KismetBoolean(boolean inner) {
		this.inner = inner
	}

	Boolean inner() { Boolean.valueOf(inner) }

	boolean asBoolean() { inner }

	int hashCode() { inner ? 1 : 0 }

	boolean equals(obj) { obj instanceof KismetBoolean && inner == obj.inner }

	String toString() { inner ? 'true' : 'false' }
}
