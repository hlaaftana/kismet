package hlaaftana.kismet.scope

import hlaaftana.kismet.vm.IKismetObject

interface Variable {
	String getName()
	IKismetObject getValue()
	void setValue(IKismetObject newValue)
}
