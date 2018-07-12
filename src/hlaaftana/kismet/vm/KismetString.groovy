package hlaaftana.kismet.vm

import groovy.transform.CompileStatic

@CompileStatic
class StringClass extends BasicClass<KismetString> {
	static final StringClass INSTANCE = new StringClass()

	private StringClass() {
		super('String')
	}

	IKismetObject call(KismetString obj, IKismetObject[] args) {
		StringBuilder builder = new StringBuilder(obj.inner)
		for (final c : args) builder.append(cast(c).inner)
		new KismetString(builder)
	}
}

@CompileStatic
@SuppressWarnings("GroovyUnusedDeclaration")
class KismetString implements IKismetObject<String>, CharSequence {
	StringBuilder inner

	KismetString() { inner = new StringBuilder() }

	KismetString(char[] chars) { inner = new StringBuilder(String.valueOf(chars)) }

	KismetString(String string) { inner = new StringBuilder(string) }

	KismetString(CharSequence string) { inner = new StringBuilder(string) }

	KismetString(StringBuilder string) { inner = string }

	IKismetClass kismetClass() { StringClass.INSTANCE }

	String inner() { toString() }

	int codePointAt(int i) { inner.codePointAt(i) }

	int codePointBefore(int i) { inner.codePointBefore(i) }

	int codePointCount(int b, int e) { inner.codePointCount(b, e) }

	int compareTo(CharSequence seq) { inner().compareTo(seq.toString()) }

	int compareToIgnoreCase(CharSequence seq) { inner().compareToIgnoreCase(seq.toString()) }

	KismetString concat(CharSequence seq) { build(this, seq) }

	boolean contains(CharSequence seq) { inner.contains(seq) }

	boolean contentEquals(CharSequence seq) { inner().contentEquals(seq.toString()) }

	boolean endsWith(CharSequence seq) { inner().endsWith(seq.toString()) }

	boolean startsWith(CharSequence seq) { inner().startsWith(seq.toString()) }

	boolean startsWith(CharSequence seq, int t) { inner().startsWith(seq.toString(), t) }

	boolean equals(seq) { seq instanceof CharSequence && inner() == ((CharSequence) seq).toString() }

	boolean equalsIgnoreCase(CharSequence seq) { inner().equalsIgnoreCase(seq.toString()) }

	byte[] getBytes() { inner.toString().bytes }

	boolean isEmpty() { inner.length() == 0 }

	int indexOf(CharSequence seq) { inner.indexOf(seq.toString()) }

	int indexOf(CharSequence seq, int from) { inner.indexOf(seq.toString(), from) }

	int lastIndexOf(CharSequence seq) { inner.lastIndexOf(seq.toString()) }

	int lastIndexOf(CharSequence seq, int from) { inner.lastIndexOf(seq.toString(), from) }

	boolean matches(CharSequence regex) { inner().contains(regex.toString()) }

	int offsetByCodePoints(int i, int o) { inner().offsetByCodePoints(i, o) }

	boolean regionMatches(boolean ic, int t, CharSequence s, int o, int l) {
		inner().regionMatches(ic, t, s.toString(), o, l)
	}

	boolean regionMatches(int t, CharSequence s, int o, int l) {
		inner().regionMatches(t, s.toString(), o, l)
	}

	KismetString replace(CharSequence t, CharSequence r) {
		new KismetString(inner().replace(t, r))
	}

	KismetString replaceAll(CharSequence t, CharSequence r) {
		new KismetString(inner().replaceAll(t.toString(), r.toString()))
	}

	KismetString replaceFirst(CharSequence t, CharSequence r) {
		new KismetString(inner().replaceFirst(t.toString(), r.toString()))
	}

	KismetString mutReplace(CharSequence t, CharSequence r) {
		inner = new StringBuilder(inner().replace(t, r))
		this
	}

	KismetString mutReplace(int s, int e, CharSequence r) {
		inner.replace(s, e, r.toString())
		this
	}

	KismetString mutReplaceAll(CharSequence t, CharSequence r) {
		inner = new StringBuilder(inner().replaceAll(t.toString(), r.toString()))
		this
	}

	KismetString mutReplaceFirst(CharSequence t, CharSequence r) {
		inner = new StringBuilder(inner().replaceFirst(t.toString(), r.toString()))
		this
	}

	List<KismetString> split(CharSequence s) {
		final sp = inner().split(s.toString())
		def res = new ArrayList<KismetString>(sp.length)
		for (final a : sp) {
			res.add(new KismetString(a))
		}
		res
	}

	List<KismetString> split(CharSequence s, int limit) {
		final sp = inner().split(s.toString(), limit)
		def res = new ArrayList<KismetString>(sp.length)
		for (final a : sp) {
			res.add(new KismetString(a))
		}
		res
	}

	KismetString toUpperCase() {
		new KismetString(inner().toUpperCase())
	}

	KismetString toLowerCase() {
		new KismetString(inner().toLowerCase())
	}

	KismetString trim() { new KismetString(inner().trim()) }

	KismetString add(CharSequence seq) { inner.append(seq.toString()); this }

	KismetString add(List<CharSequence> seq) {
		for (s in seq) add(s)
		this
	}

	static KismetString build(char[] ... args) {
		StringBuilder builder = new StringBuilder()
		for (char[] c : args) builder.append(c)
		new KismetString(builder)
	}

	static KismetString build(CharSequence... args) {
		StringBuilder builder = new StringBuilder()
		for (CharSequence c : args) builder.append(c)
		new KismetString(builder)
	}

	int length() { inner.length() }

	int getLength() { length() }

	int getCapacity() { inner.capacity() }

	KismetString delete(int s, int e) { inner.delete(s, e); this }

	KismetString deleteCharAt(int i) { inner.deleteCharAt(i); this }

	KismetString insert(int i, CharSequence seq) { inner.insert(i, seq); this }

	KismetString reverse() {
		new KismetString(inner().reverse())
	}

	KismetString mutReverse() { inner.reverse(); this }

	char charAt(int index) { inner.charAt(index) }

	KismetString setCharAt(int index, char ch) { inner.setCharAt(index, ch); this }

	void trimToSize() { inner.trimToSize() }

	KismetString subSequence(int start, int end) {
		if (end < 0) end += length()
		char[] arr = new char[end - start]
		inner.getChars(start, end, arr, 0)
		new KismetString(arr)
	}

	KismetString substring(int start, int end) {
		subSequence(start, end)
	}

	KismetString substring(int start) {
		subSequence(start, -1)
	}

	List<Character> getChars() { inner.chars as List<Character> }

	String toString() { inner.toString() }

	int hashCode() { inner.hashCode() }

	def asType(Class type) { inner().asType(type) }
}
