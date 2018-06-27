package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.exceptions.CannotOperateException
import hlaaftana.kismet.exceptions.ForbiddenAccessException

@CompileStatic
class WrapperKismetClass<T> implements IKismetClass<WrapperKismetObject> {
	static final Set<String> DEFAULT_FORBIDDEN = (['class', 'metaClass', 'properties',
			'metaPropertyValues'] as Set<String>).asImmutable()
	static List<WrapperKismetClass> instances = []

	Class<T> orig
	boolean allowConstructor = true
	String name = 'anonymous_'.concat(String.valueOf(instances.size()))
	Function getter = new Function() {
		@CompileStatic
		IKismetObject call(IKismetObject... args) {
			final name = (String) args[1].inner()
			if (forbidden.contains(name))
				throw new ForbiddenAccessException("Forbidden property $name for $this")
			Kismet.model(args[0].inner()[name])
		}
	}
	Function setter = new Function() {
		@CompileStatic
		IKismetObject call(IKismetObject... args) {
			final name = (String) args[1].inner()
			if (forbidden.contains(name))
				throw new ForbiddenAccessException("Forbidden property $name for $this")
			Kismet.model(args[0].inner()[name] = args[2].inner())
		}
	}
	Function subscriptGet = new Function() {
		@CompileStatic
		IKismetObject call(IKismetObject... args) {
			Kismet.model(((WrapperKismetObject) args[0]).methodMissing('getAt', args.tail()))
		}
	}
	Function subscriptSet = new Function() {
		@CompileStatic
		IKismetObject call(IKismetObject... args) {
			Kismet.model(((WrapperKismetObject) args[0]).methodMissing('putAt', args.tail()))
		}
	}
	Function caller = new Function() {
		@CompileStatic
		IKismetObject call(IKismetObject... args) {
			Kismet.model(args[0].inner().invokeMethod('call', args.tail()))
		}
	}
	Function constructor = Function.NOP
	Set<String> forbidden = DEFAULT_FORBIDDEN
	List<WrapperKismetClass> parents = []
	Map<IKismetClass, Function> converters = [:]

	@Memoized
	static WrapperKismetClass from(Class c) {
		int bestScore = -1
		WrapperKismetClass winner = null
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

	WrapperKismetClass() {
		instances.add this
	}

	WrapperKismetClass(Class orig = IKismetObject, String name, boolean allowConstructor = true) {
		this()
		this.orig = orig
		this.name = name
		this.allowConstructor = allowConstructor
	}

	boolean isChild(WrapperKismetClass kclass) {
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

	void setName(String n) {
		if (n in instances*.name) throw new IllegalArgumentException("Class with name $n already exists")
		this.@name = n
	}

	@Memoized
	IKismetObject<WrapperKismetClass> getObject() {
		new ClassObject(this)
	}

	WrapperKismetObject cast(IKismetObject object) {
		new WrapperKismetObject(object.inner().asType(orig), this.object)
	}

	static WrapperKismetClass fromName(String name) {
		int hash = name.hashCode()
		for (x in instances) if (x.name.hashCode() == hash && x.name == name) return x
		null
	}

	IKismetObject call(IKismetObject... args) {
		if (orig == WrapperKismetObject) {
			WrapperKismetObject[] arr = new WrapperKismetObject[args.length + 1]
			arr[0] = new WrapperKismetObject(new Expando(), this.object)
			System.arraycopy(args, 0, arr, 1, args.length)
			constructor.call(arr)
		} else if (null == orig) null else if (allowConstructor) {
			Object[] a = new Object[args.length]
			for (int i = 0; i < a.length; ++i) a[i] = args[i].inner()
			new WrapperKismetObject(orig.newInstance(a), this.object)
		} else throw new ForbiddenAccessException(
				"Forbidden constructor for original class $orig with kismet class $this")
	}

	boolean isInstance(IKismetObject x) {
		if (orig == WrapperKismetObject) x.kismetClass() == this || parents.any {
			it.isInstance(x)
		}
		else if (null == orig) null == x.inner()
		else orig.isInstance(x.inner())
	}

	String toString() { "class($name)" }

	IKismetObject propertyGet(WrapperKismetObject obj, String name) {
		Kismet.model obj.inner().getAt(name)
	}

	IKismetObject propertySet(WrapperKismetObject obj, String name, IKismetObject value) {
		obj.inner().putAt(name, value.inner())
		value
	}

	IKismetObject subscriptGet(WrapperKismetObject obj, IKismetObject key) {
		Kismet.model obj.inner().invokeMethod('getAt', [key.inner()] as Object[])
	}

	IKismetObject subscriptSet(WrapperKismetObject obj, IKismetObject key, IKismetObject value) {
		Kismet.model obj.inner().invokeMethod('putAt', [key.inner(), value.inner()] as Object[])
	}

	IKismetObject call(WrapperKismetObject obj, IKismetObject[] args) {
		if (obj.inner() instanceof Function)
			((Function) obj.inner()).call(args)
		else Kismet.model obj.inner().invokeMethod('call', args*.inner())
	}

	WrapperKismetObject construct(IKismetObject[] args) {
		throw new CannotOperateException('construct', "wrapper class $name")
	}
}