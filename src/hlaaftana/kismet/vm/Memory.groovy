package hlaaftana.kismet.vm;

public interface IRuntimeMemory {
	IRuntimeMemory relative(int id);

	default IKismetObject get(int id, int[] path) {
		IRuntimeMemory mem = this;
		for (final int p : path) mem = mem.relative(p);
		return mem.get(id);
	}

	IKismetObject get(int id);

	default IKismetObject get(String name) {
		throw new UnsupportedOperationException("getting name on class " + this.getClass() + " is unsupported (name used was " + name + ")");
	}

	default void set(int id, int[] path, IKismetObject value) {
		IRuntimeMemory mem = this;
		for (final int p : path) mem = mem.relative(p);
		mem.set(id, value);
	}

	void set(int id, IKismetObject value);

	default void set(String name, IKismetObject value) {
		throw new UnsupportedOperationException("setting name on class " + this.getClass() + " is unsupported (name used was " + name + ", value was " + value + ")");
	}
}
