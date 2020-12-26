package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.Block
import hlaaftana.kismet.call.BlockExpression
import hlaaftana.kismet.call.ExprBuilder
import hlaaftana.kismet.call.Expression
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
class Context extends Memory {
	Memory parent
	List<Address> variables

	Context(Memory parent = null, Map<String, IKismetObject> variables) {
		this.parent = parent
		setVariables variables
	}

	Context(Memory parent = null, List<Address> variables = []) {
		this.parent = parent
		setVariables variables
	}

	boolean add(String name, IKismetObject value) {
		variables.add(new NamedVariable(name, value))
	}

	void setVariables(Map<String, IKismetObject> data) {
		variables = new ArrayList<>(data.size())
		for (e in data) add(e.key, e.value)
	}

	void setVariables(List<Address> data) {
		this.@variables = data
	}

	IKismetObject getProperty(String name) {
		get(name)
	}

	Address getVariable(String name) {
		final hash = name.hashCode()
		for (v in variables) {
			if (v.name.hashCode() == hash && v.name == name) {
				return v
			}
		}
		(Address) null
	}

	IKismetObject get(String name) throws UndefinedVariableException {
		final v = getSafe(name)
		if (null != v) v.value else throw new UndefinedVariableException(name)
	}

	Address getSafe(String name) {
		final v = getVariable(name)
		if (null != v) v
		else if (null != parent && parent instanceof Context) parent.getSafe(name)
		else null
	}

	void set(String name, IKismetObject value) {
		final v = getVariable(name)
		if (null != v) {
			v.value = value
		} else add(name, value)
	}

	IKismetObject get(int id) {
		final v = variables.get(id)
		null == v ? null : v.value
	}

	void set(int id, IKismetObject value) {
		final v = variables.get(id)
		if (null == v) throw new UndefinedVariableException("Cannot set variable with id $id on dynamic context")
		v.value = value
	}

	Memory relative(int id) {
		id == 0 ? parent : null
	}

	Block child(Expression expr) {
		new Block(expr, child())
	}

	Block childBlock(Expression[] expr) {
		new Block(ExprBuilder.block(expr), child())
	}

	Block child(List<Expression> expr) {
		new Block(new BlockExpression(expr), child())
	}

	Context child() {
		new Context(this)
	}

	IKismetObject childEval(Expression expr) {
		expr.evaluate(child())
	}

	IKismetObject childEval(Expression[] expr) {
		ExprBuilder.block(expr).evaluate(child())
	}

	IKismetObject childEval(List<Expression> expr) {
		new BlockExpression(expr).evaluate(child())
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

	static class NamedVariable implements Address {
		String name
		IKismetObject value

		NamedVariable(String name, IKismetObject value) {
			this.name = name
			this.value = value
		}

		String toString() { "variable $name" }
	}
}



