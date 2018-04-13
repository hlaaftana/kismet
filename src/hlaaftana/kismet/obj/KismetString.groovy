package hlaaftana.kismet.obj

import groovy.transform.CompileStatic
import hlaaftana.kismet.IKismetClass
import hlaaftana.kismet.IKismetObject
import hlaaftana.kismet.Kismet

@CompileStatic
class StringClass implements IKismetClass {
	boolean isInstance(IKismetObject object) {
		return false
	}

	@Override
	String getName() {
		return null
	}

	@Override
	IKismetObject cast(IKismetObject object) {
		return null
	}
}

@CompileStatic
class KismetString implements IKismetObject<String>, CharSequence {
	static final BasicClass<KismetString> CLASS = new BasicClass<>('String')
	StringBuilder inner

	KismetString() { inner = new StringBuilder() }
	KismetString(char[] chars) { inner = new StringBuilder(String.valueOf(chars)) }
	KismetString(String string) { inner = new StringBuilder(string) }
	KismetString(CharSequence string) { inner = new StringBuilder(string) }
	KismetString(StringBuilder string) { inner = string }

	IKismetClass kismetClass() { CLASS }

	String inner() { toString() }

	IKismetObject getProperty(String name) {
		Kismet.model inner.invokeMethod('getProperty', [name])
	}

	IKismetObject setProperty(String name, IKismetObject value) {
		Kismet.model inner.invokeMethod('setProperty', [name, value.inner()])
	}

	IKismetObject getAt(IKismetObject obj) {
		Kismet.model inner.invokeMethod('getAt', [obj.inner()])
	}

	IKismetObject putAt(IKismetObject obj, IKismetObject value) {
		Kismet.model inner.invokeMethod('putAt', [obj.inner(), value.inner()])
	}

	IKismetObject call(IKismetObject[] args) {
		StringBuilder builder = new StringBuilder(inner)
		for (def c : args) builder.append(CLASS.cast(c).inner)
		new KismetString(builder)
	}

	static KismetString build(char[]... args) {
		StringBuilder builder = new StringBuilder()
		for (char[] c : args) builder.append(c)
		new KismetString(builder)
	}

	static KismetString build(CharSequence... args) {
		StringBuilder builder = new StringBuilder()
		for (CharSequence c : args) builder.append(c)
		new KismetString(builder)
	}

	int length() { chars.length }
	char charAt(int index) { chars[index] }

	CharSequence subSequence(int start, int end) {
		char[] arr = new char[end - start]
		inner.getChars(start, end, arr, 0)
		new KismetString(arr)
	}

	String toString() { String.valueOf(chars) }
}
