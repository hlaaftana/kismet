package hlaaftana.kismet.scope

import groovy.transform.CompileStatic

@CompileStatic
class Pair<A, B> implements List, Map.Entry<A, B> {
	A first
	B second

	Pair(A first, B second) {
		this.first = first
		this.second = second
	}

	int size() { 2 }

	boolean isEmpty() { false }

	boolean contains(o) { first == o || second == o }

	Iterator iterator() {
		new Iterator() {
			int i = 0

			boolean hasNext() { i == 0 || i == 1 }

			def next() {
				i++ == 0 ? first : second
			}
		}
	}

	Object[] toArray() {
		final x = new Object[2]
		x[0] = first
		x[1] = second
		x
	}

	Object[] toArray(Object[] a) {
		a[0] = first
		a[1] = second
		a
	}

	boolean add(o) { false }

	boolean remove(o) { false }

	boolean containsAll(Collection c) {
		for (a in c) if (!contains(a)) return false
		true
	}

	boolean addAll(Collection c) { false }

	boolean addAll(int i, Collection c) { false }

	boolean removeAll(Collection c) { false }

	boolean retainAll(Collection c) { false }

	void clear() {}

	def get(int i) {
		i == 0 ? first : i == 1 ? second : null
	}

	def set(int i, e) {
		i == 0 ? (first = e) : i == 1 ? (second = e) : null
	}

	void add(int i, e) {}

	def remove(int i) { null }

	int indexOf(o) {
		first == o ? 0 : second == o ? 1 : -1
	}

	int lastIndexOf(o) {
		second == o ? 1 : first == o ? 0 : -1
	}

	ListIterator listIterator() { listIterator(0) }

	ListIterator listIterator(int i) {
		new ListIterator() {
			boolean state

			boolean hasNext() { i == 0 || i == 1 }
			boolean hasPrevious() { hasNext() }

			def next() { state = true; Pair.this.get(i++) }
			def previous() { state = false; Pair.this.get(--i) }

			int nextIndex() { i }
			int previousIndex() { i - 1 }
			int currentIndex() { state ? i - 1 : i }

			void remove() {}
			void add(o) {}

			void set(o) {
				Pair.this.set(currentIndex(), o)
			}
		}
	}

	List subList(int fromIndex, int toIndex) {
		[first, second].subList(fromIndex, toIndex)
	}

	A getKey() { first }

	B getValue() { second }

	B setValue(B value) { second = value }
}
