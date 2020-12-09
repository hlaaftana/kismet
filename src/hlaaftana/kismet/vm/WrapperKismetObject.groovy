package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.Function

@CompileStatic
class WrapperKismetObject<T> implements IKismetObject<T> {
	T inner

	T inner() { this.@inner }

	WrapperKismetObject(T i) { this.@inner = i }

	IKismetObject propertyGet(String name) {
		Kismet.model(inner().invokeMethod('getProperty', [name] as Object[]))
	}

	void setProperty(String name, value) {
		inner().invokeMethod('setProperty', [name, value] as Object[])
	}

	IKismetObject propertySet(String name, IKismetObject value) {
		Kismet.model(inner().invokeMethod('setProperty', [name, value.inner()] as Object[]))
	}

	/*IKismetObject getAt(IKismetObject obj) { getAt(obj.inner()) }

	IKismetObject putAt(IKismetObject obj, IKismetObject val) { putAt(obj.inner(), val.inner()) }
*/
	IKismetObject getAt(obj) {
		Kismet.model(inner().invokeMethod('getAt', [obj] as Object[]))
	}

	IKismetObject putAt(obj, val) {
		Kismet.model(inner().invokeMethod('putAt', [obj, val] as Object[]))
	}

	def methodMissing(String name, ... args) {
		for (int i = 0; i < args.length; ++i)
			if (args[i] instanceof IKismetObject)
				args[i] = ((IKismetObject) args[i]).inner()
		Kismet.model(args ? inner.invokeMethod(name, args) : inner.invokeMethod(name, null))
	}

	def methodMissing(String name, Collection args) { methodMissing(name, args as Object[]) }

	def methodMissing(String name, args) {
		methodMissing(name, args instanceof Object[] ? (Object[]) args : [args] as Object[])
	}

	def methodMissing(String name, IKismetObject args) { methodMissing(name, args.inner()) }

	def methodMissing(String name, IKismetObject... args) {
		Object[] arr = new Object[args.length]
		for (int i = 0; i < args.length; ++i) arr[i] = args[i].inner()
		methodMissing(name, (Object[]) arr)
	}

	IKismetObject call(... args) {
		call(args.collect(Kismet.&model) as IKismetObject[])
	}

	IKismetObject call(IKismetObject... args) {
		if (inner() instanceof Function) ((Function) inner()).call(args)
		else {
			def x = new Object[args.length]
			for (int i = 0; i < x.length; ++i) x[i] = args[i].inner()
			Kismet.model(inner().invokeMethod('call', x))
		}
	}

	boolean equals(obj) {
		obj instanceof IKismetObject ? inner == ((IKismetObject) obj).inner() : inner == obj
	}

	boolean asBoolean() {
		inner as boolean
	}

	int hashCode() {
		null == inner ? 0 : inner.hashCode()
	}

	String toString() {
		inner.toString()
	}

	Iterator iterator() {
		inner.iterator()
	}
}

