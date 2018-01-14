package hlaaftana.kismet

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

@CompileStatic
class KismetObject<T> implements KismetCallable {
	static final List<String> DEFAULT_FORBIDDEN = ['class', 'metaClass', 'properties',
			'metaPropertyValues', 'forbidden'].asImmutable()
	KismetObject<KismetClass> kclass
	List<String> forbidden = DEFAULT_FORBIDDEN
	T inner

	T inner() { this.@inner }

	KismetObject(T i, KismetObject<KismetClass> c){ this(i); this.@kclass = c }
	KismetObject(T i){ this.@inner = i }

	def getProperty(String name){
		if (name in forbidden)
			throw new ForbiddenAccessException("Forbidden property $name for $kclass")
		else Kismet.model(kclass.inner().getter.call(this, Kismet.model(name)))
	}

	void setProperty(String name, value){
		if (name in forbidden)
			throw new ForbiddenAccessException("Forbidden property $name for $kclass")
		else Kismet.model(kclass.inner().setter.call(this, Kismet.model(name), Kismet.model(value)))
	}

	def methodMissing(String name, ...args){
		for (int i = 0; i < args.length; ++i)
			if (args[i] instanceof KismetObject)
				args[i] = ((KismetObject) args[i]).inner()
		Kismet.model(args ? inner.invokeMethod(name, args) : inner.invokeMethod(name, null))
	}

	def methodMissing(String name, Collection args){ methodMissing(name, args as Object[]) }
	def methodMissing(String name, args){ methodMissing(name, args instanceof Object[] ? (Object[]) args : [args] as Object[]) }
	def methodMissing(String name, KismetObject args){ methodMissing(name, args.inner()) }
	def methodMissing(String name, KismetObject... args){
		Object[] arr = new Object[args.length]
		for (int i = 0; i < args.length; ++i) arr[i] = args[i].inner()
		methodMissing(name, (Object[]) arr)
	}

	KismetObject call(...args){
		call(args.collect(Kismet.&model) as KismetObject[])
	}

	KismetObject call(KismetObject... args) {
		Kismet.model(kclass.inner().caller.call((([this] as KismetObject[]) + args) as KismetObject[]))
	}

	def "as"(Class c){
		KismetClass k = KismetClass.from(c)
		def p = null == k ? (KismetCallable) null : kclass.inner().converters[k]
		if (null != p) p(this)
		else try { inner.asType(c) }
		catch (ClassCastException ex) { if (c == Closure) this.&call else throw ex }
	}

	def "as"(KismetClass c){
		if (c && kclass.inner().converters.containsKey(c)) kclass.inner().converters[c](this)
		else throw new ClassCastException('Can\'t cast object with class ' +
			kclass + ' to class ' + c)
	}

	boolean equals(obj) {
		if (obj instanceof KismetObject) inner == obj.inner()
		else inner == obj
	}

	boolean asBoolean(){
		kclass.inner().orig != KismetObject ? inner as boolean : this.as(boolean)
	}

	int hashCode() {
		inner.hashCode()
	}

	String toString() {
		kclass.inner().orig != KismetObject ? inner.toString() : this.as(String)
	}

	Iterator iterator() {
		inner.iterator()
	}
}

@CompileStatic @InheritConstructors class ForbiddenAccessException extends KismetException {}
