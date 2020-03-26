package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import hlaaftana.kismet.call.TypedExpression

@CompileStatic
@EqualsAndHashCode
class TupleType extends GenericType {
	static final SingleType BASE = new SingleType('Tuple')
	Type varargs

	TupleType() { super(BASE) }

	TupleType(Type[] bounds) {
		super(BASE, bounds)
	}

	String toString() { "Tuple[${arguments.join(', ')}" + (null == varargs ? "]" : ", $varargs...]") }

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
}
