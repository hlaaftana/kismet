package hlaaftana.kismet

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Memoized

@CompileStatic
class KismetClass implements KismetCallable {
	static List<KismetClass> instances = []
	static KismetCallable defaultGetter, defaultSetter, defaultCaller,
	                      defaultConstructor = func { ...a -> }

	@CompileDynamic
	@SuppressWarnings('all')
	static void dynamicInstantiator() {
		defaultGetter = func { KismetObject... a -> ((KismetObject) a[0]).inner()[a[1].inner()] }
		defaultSetter = func { KismetObject... a -> a[0].inner()[a[1].inner()] = a[2] }
		defaultCaller = func { KismetObject... a ->
			a.size() > 1 ? a[0].inner()(a.drop(1) as KismetObject[]) : a[0].inner()()
		}
	}

	static { dynamicInstantiator() }

	Class orig
	String name = 'anonymous_'.concat(instances.size().toString())
	KismetCallable getter = defaultGetter, setter = defaultSetter,
	               caller = defaultCaller, constructor = defaultConstructor
	Map<KismetClass, KismetCallable> converters = [:]

	KismetClass() {
		instances.add this
	}

	KismetClass(Class orig, String name) {
		this()
		this.orig = orig
		this.name = name
	}

	void setName(String n){
		if (n in instances*.name) throw new IllegalArgumentException("Class with name $n already exists")
		this.@name = n
	}

	@Memoized
	KismetObject<KismetClass> getObject() {
		new KismetObject<KismetClass>(this, meta.object)
	}

	@Memoized
	static KismetClass getMeta() {
		new MetaKismetClass()
	}

	KismetObject call(KismetObject... args){
		def a = new KismetObject(new Expando(), this.object)
		constructor.call(([a] as KismetObject[]) + args)
		a
	}

	String toString(){ "class($name)" }

	protected static GroovyFunction func(Closure a){ new GroovyFunction(false, a) }
}

@CompileStatic
class MetaKismetClass extends KismetClass {
	{
		name = 'Class'
		constructor = func { KismetObject... a -> a[0].@inner = new KismetClass() }
	}

	KismetObject<KismetClass> getObject() {
		def x = new KismetObject<KismetClass>(this)
		x.@class_ = x
		x
	}
}