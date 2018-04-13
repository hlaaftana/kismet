package hlaaftana.kismet;

public interface Variable {
	default String getName() { return null; }
	IKismetObject getValue();
	void setValue(IKismetObject newValue);
}
