package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class KismetObject<T> implements KismetCallable {
	KismetObject<KismetClass> class_
	public forbidden = ['class', 'metaClass', 'forbidden']
	public T inner

	KismetObject(T i, KismetObject<KismetClass> c){ this.@inner = i; this.@class_ = c }
	KismetObject(T i){ this.@inner = i }

	T inner(){ this.@inner }
	def forbidden(){ this.@forbidden }
	KismetObject<KismetClass> kclass(){ this.@class_ }

	def getProperty(String name){
		if (name in forbidden) throw new MissingPropertyException(name, inner().class)
		else Kismet.model(kclass().inner().getter.call(this, Kismet.model(name)))
	}

	void setProperty(String name, value){
		if (name in forbidden) throw new MissingPropertyException(name, inner().class)
		else Kismet.model(kclass().inner().setter.call(this, Kismet.model(name), Kismet.model(value)))
	}

	def methodMissing(String name, ...args){
		for (int i = 0; i < args.length; ++i)
			if (args[i] instanceof KismetObject)
				args[i] = ((KismetObject) args[i]).inner()
		Kismet.model(args ? inner().invokeMethod(name, args) : inner().invokeMethod(name, null))
	}

	def methodMissing(String name, Collection args){ methodMissing(name, args as Object[]) }
	def methodMissing(String name, args){ methodMissing(name, args instanceof Object[] ? args : [args] as Object[]) }
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
		Kismet.model(kclass().inner().caller.call((([this] as KismetObject[]) + args) as KismetObject[]))
	}

	@CompileStatic
	def "as"(Class c){
		KismetClass k = KismetModels.defaultConversions[c]?.inner()
		if (k && kclass().inner().converters.containsKey(k)) kclass().inner().converters[k](this)
		else inner().asType(c)
	}

	def "as"(KismetClass c){
		if (c && kclass().inner().converters.containsKey(c)) kclass().inner().converters[c](this)
		else throw new ClassCastException('Can\'t cast object with class ' +
			kclass() + ' to class ' + c)
	}

	boolean asBoolean(){
		inner() as boolean
	}

	String toString() {
		inner().toString()
	}
}
