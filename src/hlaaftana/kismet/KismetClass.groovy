package hlaaftana.kismet

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.transform.Memoized

@CompileStatic
class KismetClass<T> {
	static final Set<String> DEFAULT_FORBIDDEN = (['class', 'metaClass', 'properties',
			'metaPropertyValues'] as Set<String>).asImmutable()
	static List<KismetClass> instances = []

	Class<T> orig
	boolean allowConstructor = true
	String name = 'anonymous_'.concat(instances.size().toString())
	Function getter = new Function() {
		@CompileStatic
		KismetObject call(KismetObject... args) {
			final name = (String) args[1].inner()
			if (forbidden.contains(name))
				throw new ForbiddenAccessException("Forbidden property $name for $this")
			Kismet.model(args[0].inner()[name])
		}
	}
	Function setter = new Function() {
		@CompileStatic
		KismetObject call(KismetObject... args) {
			final name = (String) args[1].inner()
			if (forbidden.contains(name))
				throw new ForbiddenAccessException("Forbidden property $name for $this")
			Kismet.model(args[0].inner()[name] = args[2].inner())
		}
	}
	Function subscriptGet = new Function() {
		@CompileStatic
		KismetObject call(KismetObject... args) {
			Kismet.model(args[0].invokeMethod('getAt', args.tail()))
		}
	}
	Function subscriptSet = new Function() {
		@CompileStatic
		KismetObject call(KismetObject... args) {
			Kismet.model(args[0].invokeMethod('putAt', args.tail()))
		}
	}
	Function caller = new Function() {
		@CompileStatic
		KismetObject call(KismetObject... args) {
			Kismet.model(args[0].inner().invokeMethod('call', args.tail()))
		}
	}
	Function constructor = Function.NOP
	Set<String> forbidden = DEFAULT_FORBIDDEN
	List<KismetClass> parents = []
	Map<KismetClass, Function> converters = [:]

	@Memoized
	static KismetClass from(Class c) {
		int bestScore = -1
		KismetClass winner = null
		for (k in instances) {
			if (!k.orig?.isAssignableFrom(c)) continue
			int ls = k.relationScore(c)
			if (bestScore < 0 || ls < bestScore) {
				bestScore = ls
				winner = k
			}
		}
		winner
	}

	KismetClass() {
		instances.add this
	}

	KismetClass(Class orig = KismetObject, String name, boolean allowConstructor = true) {
		this()
		this.orig = orig
		this.name = name
		this.allowConstructor = allowConstructor
	}

	boolean isChild(KismetClass kclass) {
		for (p in kclass.parents)
			if (p == this) return true
			else for (x in p.parents)
				if (this == x || isChild(x))
					return true
		false
	}

	int relationScore(Class c) {
		if (!orig.isAssignableFrom(c)) return -1
		c == orig ? 0 : relationScore(c.superclass) + 1
	}

	void setName(String n){
		if (n in instances*.name) throw new IllegalArgumentException("Class with name $n already exists")
		this.@name = n
	}

	@Memoized
	KismetObject<KismetClass> getObject() {
		new KismetObject<KismetClass>(this, META.object)
	}

	static final MetaKismetClass META = new MetaKismetClass()

	static KismetClass fromName(String name) {
		int hash = name.hashCode()
		for (x in instances) if (x.name.hashCode() == hash && x.name == name) return x
		null
	}

	KismetObject call(KismetObject... args){
		if (orig == KismetObject) {
			KismetObject[] arr = new KismetObject[args.length + 1]
			arr[0] = new KismetObject(new Expando(), this.object)
			System.arraycopy(args, 0, arr, 1, args.length)
			constructor.call(arr)
		} else if (null == orig) null else if (allowConstructor) {
			Object[] a = new Object[args.length]
			for (int i = 0; i < a.length; ++i) a[i] = args[i].inner()
			new KismetObject(orig.newInstance(a), this.object)
		} else throw new ForbiddenAccessException(
				"Forbidden constructor for original class $orig with kismet class $this")
	}

	boolean isInstance(KismetObject x) {
		if (orig == KismetObject) x.kclass.inner() == this || parents.any { it.isInstance(x) }
		else if (null == orig) null == x.inner()
		else orig.isInstance(x.inner())
	}

	String toString(){ "class($name)" }
}

@CompileStatic
class MetaKismetClass extends KismetClass {
	{
		name = 'Class'
		orig = KismetClass
		constructor = new Function() {
			@CompileStatic
			KismetObject call(KismetObject... args) {
				final x = new KismetClass()
				args[0].@inner = x
				x.object
			}
		}
	}

	KismetObject<KismetClass> getObject() {
		def x = new KismetObject<KismetClass>(this)
		x.@kclass = x
		x
	}
}

@CompileStatic @InheritConstructors class ForbiddenAccessException extends KismetException {}
