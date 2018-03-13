package hlaaftana.kismet;

public interface Variable {
	default String getName() { return null; }
	KismetObject getValue();
	void setValue(KismetObject newValue);
}
