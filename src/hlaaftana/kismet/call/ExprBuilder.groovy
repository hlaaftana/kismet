package hlaaftana.kismet.call

import groovy.transform.CompileStatic

@CompileStatic
class ExprBuilder {
	static BlockExpression block(Expression... exprs) {
		new BlockExpression(Arrays.asList(exprs))
	}
	
	static CallExpression call(Expression... args) {
		new CallExpression(args)
	}

	static NameExpression name(String name) {
		new NameExpression(name)
	}

	static StringExpression string(String string) {
		new StringExpression(string)
	}

	static NumberExpression number(Number number) {
		new NumberExpression(number)
	}

	static ColonExpression colon(Expression left, Expression right) {
		new ColonExpression(left, right)
	}

	static ListExpression list(Expression... args) {
		new ListExpression(Arrays.asList(args))
	}

	static SetExpression set(Expression... args) {
		new SetExpression(Arrays.asList(args))
	}

	static TupleExpression tuple(Expression... args) {
		new TupleExpression(Arrays.asList(args))
	}
}
