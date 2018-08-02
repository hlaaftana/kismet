package hlaaftana.kismet.scope

import hlaaftana.kismet.vm.IKismetObject

interface Address {
	String getName()
	IKismetObject getValue()
	void setValue(IKismetObject newValue)
}
