package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.parser.BlockExpression
import hlaaftana.kismet.parser.Expression

@CompileStatic
class Context {
	Context parent
	List<Variable> variables

	Context(Context parent = null, Map<String, IKismetObject> variables) {
		this.parent = parent
		setVariables variables
	}

	Context(Context parent = null, List<Variable> variables = []) {
		this.parent = parent
		setVariables variables
	}

	boolean add(String name, IKismetObject value) {
		variables.add(new NamedVariable(name, value))
	}

	IKismetObject addAndReturn(String name, IKismetObject value) {
		add(name, value)
		value
	}

	void setVariables(Map<String, IKismetObject> data) {
		variables = new ArrayList<>(data.size())
		for (e in data) add(e.key, e.value)
	}

	void setVariables(List<Variable> data) {
		this.@variables = data
	}

	IKismetObject getProperty(String name) {
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

	IKismetObject get(String name) {
		final v = getVariable(name)
		if (null != v) v.value
		else if (null != parent) parent.get(name)
		else throw new UndefinedVariableException(name)
	}

	IKismetObject set(String name, IKismetObject value) {
		final v = getVariable(name)
		if (null != v) { v.value = value; value }
		else addAndReturn(name, value)
	}

	IKismetObject define(String name, IKismetObject value) {
		if (null != getVariable(name)) throw new VariableExistsException("Variable $name already exists")
		addAndReturn(name, value)
	}

	IKismetObject assign(Context original = this, String name, IKismetObject value) {
		final v = getVariable(name)
		if (null != v) { v.value = value; value }
		else if (null != parent)
			parent.assign(original, name, value)
		else original.addAndReturn(name, value)
	}

	IKismetObject change(String name, IKismetObject value) {
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

	IKismetObject childEval(Expression expr) {
		child(expr).evaluate()
	}

	IKismetObject childEval(Expression[] expr) {
		child(expr).evaluate()
	}

	IKismetObject childEval(List<Expression> expr) {
		child(expr).evaluate()
	}

	IKismetObject eval(Expression expr) {
		expr.evaluate this
	}

	IKismetObject eval(Expression[] expr) {
		def last = Kismet.NULL
		for (e in expr) last = eval(e)
		last
	}

	IKismetObject eval(List<Expression> expr) {
		def last = Kismet.NULL
		for (e in expr) last = eval(e)
		last
	}

	def clone() {
		new Context(parent, getVariables())
	}

	static class NamedVariable implements Variable {
		String name
		IKismetObject value

		NamedVariable(String name, IKismetObject value) {
			this.name = name
			this.value = value
		}
	}
}