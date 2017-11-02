package hlaaftana.kismet;

import java.util.Iterator;

public class IteratorIterable<T> implements Iterator<T>, Iterable<T> {
	private Iterator<T> inside;

	public IteratorIterable(Iterator<T> inside) {
		this.inside = inside;
	}

	@Override
	public Iterator<T> iterator() {
		return inside;
	}

	@Override
	public boolean hasNext() {
		return inside.hasNext();
	}

	@Override
	public T next() {
		return inside.next();
	}

	@Override
	public void remove() {
		inside.remove();
	}
}
