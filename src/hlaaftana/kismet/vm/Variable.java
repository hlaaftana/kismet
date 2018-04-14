package hlaaftana.kismet.vm;

public interface Variable {
	default String getName() { return null; }
	IKismetObject getValue();
	void setValue(IKismetObject newValue);
}
