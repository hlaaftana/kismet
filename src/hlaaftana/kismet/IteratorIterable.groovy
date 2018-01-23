package hlaaftana.kismet

import groovy.transform.CompileStatic

@CompileStatic
class IteratorIterable<T> implements Iterator<T>, Iterable<T> {
	Iterator<T> inside

	IteratorIterable(Iterator<T> inside) {
		this.inside = inside
	}

	@Override
	Iterator<T> iterator() {
		inside
	}

	@Override
	boolean hasNext() {
		inside.hasNext()
	}

	@Override
	T next() {
		inside.next()
	}

	@Override
	void remove() {
		inside.remove()
	}
}
