package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.AssignmentType

@CompileStatic
class ExprBuilder {
	static BlockExpression block(Expression... exprs) {
		new BlockExpression(Arrays.asList(exprs))
	}

	static BlockExpression block(List<Expression> exprs) {
		new BlockExpression(exprs)
	}
	
	static CallExpression call(Expression... args) {
		new CallExpression(args)
	}

	static CallExpression call(List<Expression> exprs) {
		new CallExpression(exprs)
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

	static PropertyExpression property(Expression left, String right) {
		new PropertyExpression(left, right)
	}

	static SubscriptExpression subscript(Expression left, Expression right) {
		new SubscriptExpression(left, right)
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

	static ListExpression list(List<Expression> args) {
		new ListExpression(args)
	}

	static SetExpression set(List<Expression> args) {
		new SetExpression(args)
	}

	static TupleExpression tuple(List<Expression> args) {
		new TupleExpression(args)
	}

	static VariableModifyExpression var(AssignmentType type, String name, Expression value) {
		new VariableModifyExpression(type, name, value)
	}
}
