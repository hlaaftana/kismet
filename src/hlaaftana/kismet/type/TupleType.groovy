package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.TypedExpression

@CompileStatic
class TupleType extends GenericType {
	static final SingleType BASE = new SingleType('Tuple')
	Type varargs

	TupleType(Type[] bounds) {
		super(BASE, bounds)
	}

	TupleType(TypedExpression[] zro) {
		super(BASE, new Type[zro.length])
		for (int i = 0; i < zro.length; ++i) bounds[i] = zro[i].type
	}

	String toString() { "Tuple[${bounds.join(', ')}" + (null == varargs ? "]" : ", $varargs...]") }

	boolean losesAgainst(Type other) {
		def t = (TupleType) other
		for (int i = 0; i < bounds.length; ++i) if (bounds[i].losesAgainst(t.bounds[i])) return true
		false
	}

	boolean isIndefinite() { null != varargs }

	TypeBound.Variance varianceAt(int i) {
		TypeBound.Variance.COVARIANT
	}

	TupleType withVarargs(Type varargs) {
		this.varargs = varargs
		this
	}

	Type getAt(int i) {
		i >= bounds.length ? varargs : bounds[i]
	}

	boolean equals(obj) { obj instanceof TupleType && Arrays.equals(bounds, obj.bounds) && varargs == obj.varargs }
}
