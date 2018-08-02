package hlaaftana.kismet.vm

abstract class Memory {
	abstract Memory relative(int id)

	IKismetObject get(int id, int[] path) {
		def mem = this
		for (final p : path) mem = mem.relative(p)
		mem.get(id)
	}

	abstract IKismetObject get(int id)

	IKismetObject get(String name) {
		throw new UnsupportedOperationException("getting name on class " + this.class + " is unsupported (name used was " + name + ")")
	}

	void set(int id, int[] path, IKismetObject value) {
		def mem = this
		for (final p : path) mem = mem.relative(p)
		mem.set(id, value)
	}

	abstract void set(int id, IKismetObject value)

	void set(String name, IKismetObject value) {
		throw new UnsupportedOperationException("setting name on class " + this.class + " is unsupported (name used was " + name + ", value was " + value + ")")
	}
}
