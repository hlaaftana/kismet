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
		for (int i = 0; i < zro.length; ++i) arguments[i] = zro[i].type
	}

	String toString() { "Tuple[${arguments.join(', ')}" + (null == varargs ? "]" : ", $varargs...]") }

	boolean losesAgainst(Type other) {
		def t = (TupleType) other
		for (int i = 0; i < arguments.length; ++i) if (arguments[i].losesAgainst(t.arguments[i])) return true
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
		i >= arguments.length ? varargs : arguments[i]
	}

	boolean equals(obj) { obj instanceof TupleType && Arrays.equals(arguments, obj.arguments) && varargs == obj.varargs }
}
