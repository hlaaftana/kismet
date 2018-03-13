package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.BlockExpression
import hlaaftana.kismet.parser.Expression

@CompileStatic
class Context {
	Context parent
	List<Variable> variables

	Context(Context parent = null, Map<String, KismetObject> variables) {
		this.parent = parent
		setVariables variables
	}

	Context(Context parent = null, List<Variable> variables = []) {
		this.parent = parent
		setVariables variables
	}

	boolean add(String name, KismetObject value) {
		variables.add(new NamedVariable(name, value))
	}

	KismetObject addAndReturn(String name, KismetObject value) {
		add(name, value)
		value
	}

	void setVariables(Map<String, KismetObject> data) {
		variables = new ArrayList<>(data.size())
		for (e in data) add(e.key, e.value)
	}

	void setVariables(List<Variable> data) {
		this.@variables = data
	}

	KismetObject getProperty(String name) {
		get(name)
	}

	Variable getVariable(String name) {
		final hash = name.hashCode()
		for (v in variables) {
			if (v.name.hashCode() == hash && v.name == name) {
				return v
			}
		}
		(Variable) null
	}

	KismetObject get(String name) {
		final v = getVariable(name)
		if (null != v) v.value
		else if (null != parent) parent.get(name)
		else throw new UndefinedVariableException(name)
	}

	KismetObject set(String name, KismetObject value) {
		final v = getVariable(name)
		if (null != v) { v.value = value; value }
		else addAndReturn(name, value)
	}

	KismetObject define(String name, KismetObject value) {
		if (null != getVariable(name)) throw new VariableExistsException("Variable $name already exists")
		addAndReturn(name, value)
	}

	KismetObject assign(Context original = this, String name, KismetObject value) {
		final v = getVariable(name)
		if (null != v) { v.value = value; value }
		else if (null != parent)
			parent.assign(original, name, value)
		else original.addAndReturn(name, value)
	}

	KismetObject change(String name, KismetObject value) {
		final v = getVariable(name)
		if (null != v) { v.value = value; value }
		else if (null != parent)
			parent.change(name, value)
		else throw new UndefinedVariableException(name)
	}

	Block child(Expression expr) {
		new Block(expr, this)
	}

	Block child(Expression[] expr) {
		new Block(new BlockExpression(expr.toList()), this)
	}

	Block child(List<Expression> expr) {
		new Block(new BlockExpression(expr), this)
	}

	Context child() {
		new Context(this)
	}

	KismetObject childEval(Expression expr) {
		child(expr).evaluate()
	}

	KismetObject childEval(Expression[] expr) {
		child(expr).evaluate()
	}

	KismetObject childEval(List<Expression> expr) {
		child(expr).evaluate()
	}

	KismetObject eval(Expression expr) {
		expr.evaluate this
	}

	KismetObject eval(Expression[] expr) {
		def last = Kismet.NULL
		for (e in expr) last = eval(e)
		last
	}

	KismetObject eval(List<Expression> expr) {
		def last = Kismet.NULL
		for (e in expr) last = eval(e)
		last
	}

	def clone() {
		new Context(parent, getVariables())
	}

	static class NamedVariable implements Variable {
		String name
		KismetObject value

		NamedVariable(String name, KismetObject value) {
			this.name = name
			this.value = value
		}
	}
}