package hlaaftana.kismet.vm

interface Variable {
	String getName()
	IKismetObject getValue()
	void setValue(IKismetObject newValue)
}
