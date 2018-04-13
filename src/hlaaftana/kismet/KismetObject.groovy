package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class KismetObject<T> implements IKismetObject<T> {
	IKismetObject<KismetClass> kclass
	T inner

	KismetClass kismetClass() { kclass.inner() }
	T inner() { this.@inner }

	KismetObject(T i, IKismetObject<KismetClass> c) { this(i); this.@kclass = c }
	KismetObject(T i) { this.@inner = i }

	IKismetObject getProperty(String name) {
		kclass.inner().getter.call(this, Kismet.model(name))
	}

	void setProperty(String name, value) {
		kclass.inner().setter.call(this, Kismet.model(name), Kismet.model(value))
	}

	IKismetObject setProperty(String name, IKismetObject value) {
		kclass.inner().setter.call(this, Kismet.model(name), value)
	}

	IKismetObject getAt(IKismetObject obj) {
		kclass.inner().subscriptGet.call(this, obj)
	}

	IKismetObject putAt(IKismetObject obj, IKismetObject val) {
		kclass.inner().subscriptSet.call(this, obj, val)
	}

	IKismetObject getAt(obj) {
		kclass.inner().subscriptGet.call(this, Kismet.model(obj))
	}

	IKismetObject putAt(obj, val) {
		kclass.inner().subscriptSet.call(this, Kismet.model(obj), Kismet.model(val))
	}

	def methodMissing(String name, ...args) {
		for (int i = 0; i < args.length; ++i)
			if (args[i] instanceof IKismetObject)
				args[i] = ((IKismetObject) args[i]).inner()
		Kismet.model(args ? inner.invokeMethod(name, args) : inner.invokeMethod(name, null))
	}

	def methodMissing(String name, Collection args) { methodMissing(name, args as Object[]) }
	def methodMissing(String name, args) { methodMissing(name, args instanceof Object[] ? (Object[]) args : [args] as Object[]) }
	def methodMissing(String name, IKismetObject args) { methodMissing(name, args.inner()) }
	def methodMissing(String name, IKismetObject... args) {
		Object[] arr = new Object[args.length]
		for (int i = 0; i < args.length; ++i) arr[i] = args[i].inner()
		methodMissing(name, (Object[]) arr)
	}

	IKismetObject call(...args) {
		call(args.collect(Kismet.&model) as IKismetObject[])
	}

	IKismetObject call(IKismetObject... args) {
		final l = args.length
		def x = new IKismetObject[l + 1]
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

	IKismetObject convert(IKismetClass c) {
		if (kclass.inner().converters.containsKey(c))
			kclass.inner().converters[c](this)
		else throw new ClassCastException('Can\'t cast object with class ' +
				kclass + ' to class ' + c)
	}

	boolean equals(obj) {
		obj instanceof IKismetObject ? inner == ((IKismetObject) obj).inner() : inner == obj
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

