package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class KismetObject<T> {
	KismetObject<KismetClass> kclass
	T inner

	T inner() { this.@inner }

	KismetObject(T i, KismetObject<KismetClass> c) { this(i); this.@kclass = c }
	KismetObject(T i) { this.@inner = i }

	KismetObject getProperty(String name) {
		kclass.inner().getter.call(this, Kismet.model(name))
	}

	void setProperty(String name, value) {
		kclass.inner().setter.call(this, Kismet.model(name), Kismet.model(value))
	}

	def methodMissing(String name, ...args) {
		for (int i = 0; i < args.length; ++i)
			if (args[i] instanceof KismetObject)
				args[i] = ((KismetObject) args[i]).inner()
		Kismet.model(args ? inner.invokeMethod(name, args) : inner.invokeMethod(name, null))
	}

	def methodMissing(String name, Collection args) { methodMissing(name, args as Object[]) }
	def methodMissing(String name, args) { methodMissing(name, args instanceof Object[] ? (Object[]) args : [args] as Object[]) }
	def methodMissing(String name, KismetObject args) { methodMissing(name, args.inner()) }
	def methodMissing(String name, KismetObject... args) {
		Object[] arr = new Object[args.length]
		for (int i = 0; i < args.length; ++i) arr[i] = args[i].inner()
		methodMissing(name, (Object[]) arr)
	}

	KismetObject call(...args) {
		call(args.collect(Kismet.&model) as KismetObject[])
	}

	KismetObject call(KismetObject... args) {
		final l = args.length
		def x = new KismetObject[l + 1]
		x[0] = this
		System.arraycopy(args, 0, x, 1, l)
		kclass.inner().caller.call(x)
	}

	def "as"(Class c) {
		KismetClass k = KismetClass.from(c)
		def p = null == k ? (Function) null : kclass.inner().converters[k]
		if (null != p) p(this)
		else try { inner.asType(c) }
		catch (ClassCastException ex) { if (c == Closure) this.&call else throw ex }
	}

	def "as"(KismetClass c) {
		if (c && kclass.inner().converters.containsKey(c)) kclass.inner().converters[c](this)
		else throw new ClassCastException('Can\'t cast object with class ' +
			kclass + ' to class ' + c)
	}

	boolean equals(obj) {
		if (obj instanceof KismetObject) inner == obj.inner()
		else inner == obj
	}

	boolean asBoolean() {
		kclass.inner().orig != KismetObject ? inner as boolean : this.as(boolean)
	}

	int hashCode() {
		null == inner ? 0 : inner.hashCode()
	}

	String toString() {
		kclass.inner().orig != KismetObject ? inner.toString() : this.as(String)
	}

	Iterator iterator() {
		inner.iterator()
	}
}

