package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.ForbiddenAccessException
import hlaaftana.kismet.exceptions.UndefinedSymbolException
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
class TypedContext extends Memory {
	String label
	List<TypedContext> heritage = new ArrayList<>()
	List<Variable> variables = new ArrayList<>()

	TypedContext child() {
		def result = new TypedContext()
		result.heritage.add(this)
		result
	}

	Memory relative(int id) {
		heritage.get(id)
	}

	Variable getVariable(int i) {
		variables.get(i)
	}

	Variable getStatic(int i) {
		def var = getVariable(i)
		if (null != var.value) var
		else {
			def name = var.name
			def errmsg = new StringBuilder("Variable ")
			if (null != name) errmsg.append(name).append((char) ' ')
			errmsg.append("with index ").append(i).append(" was not defined statically")
			throw new ForbiddenAccessException(errmsg.toString())
		}
	}

	int size() { variables.size() }

	void addVariable(Variable variable) {
		variables.add(variable)
	}

	Variable addVariable(String name = null, Type type = Type.ANY) {
		final var = new Variable(name, variables.size(), type)
		variables.add(var)
		var
	}

	Variable addVariable(String name = null, IKismetObject value, Type type = Type.ANY) {
		final var = new Variable(name, variables.size(), type)
		var.value = value
		variables.add(var)
		var
	}

	Variable getVariable(String name) {
		final h = name.hashCode()
		for (var in variables) if (h == var.hash && name == var.name) return var
		null
	}

	VariableReference find(int h, String name) {
		for (var in variables)
			if (var.hash == h && var.name == name)
				return var.ref()
		for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).find(h, name)
			if (null != v) return v.relative(i)
		}
		null
	}

	VariableReference find(String name) { find(name.hashCode(), name) }

	List<VariableReference> getAll(int h, String name) {
		def result = new ArrayList<VariableReference>()
		for (var in variables)
			if (var.hash == h && var.name == name)
				result.add(var.ref())
		for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).getAll(h, name)
			for (r in v) result.add(r.relative(i))
		}
		result
	}

	List<VariableReference> getAll(String name) { getAll(name.hashCode(), name) }

	VariableReference find(String name, Type expected) {
		def match = new ArrayList<VariableReference>()
		for (d in getAll(name)) {
			def typ = d.variable.type
			if (typ.relation(expected).assignableTo) match.add(d)
		}
		if (match.empty) return null
		def winner = match.get(0)
		for (int i = 1; i < match.size(); ++i) {
			final e = match.get(i)
			if (winner.variable.type.losesAgainst(e.variable.type)) winner = e
		}
		winner
	}

	List<VariableReference> getAll(Set<String> names) {
		def result = new ArrayList<VariableReference>()
		for (var in variables)
			if (names.contains(var.name))
				result.add(var.ref())
		for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).getAll(names)
			for (r in v) result.add(r.relative(i))
		}
		result
	}

	VariableReference find(Set<String> names, Type expected) {
		def match = new ArrayList<VariableReference>()
		for (d in getAll(names)) {
			def typ = d.variable.type
			if (typ.relation(expected).assignableTo) match.add(d)
		}
		if (match.empty) return null
		def winner = match.get(0)
		for (int i = 1; i < match.size(); ++i) {
			final e = match.get(i)
			if (winner.variable.type.losesAgainst(e.variable.type)) winner = e
		}
		winner
	}

	VariableReference findThrow(String name, Type expected) {
		def var = find(name, expected)
		if (null == var) throw new UndefinedSymbolException("Could not find variable with name $name and type $expected")
		else var
	}

	List<VariableReference> getAllStatic(int h, String name) {
		def result = new ArrayList<VariableReference>()
		for (var in variables)
			if (null != var.value && var.hash == h && var.name == name)
				result.add(var.ref())
		for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).getAllStatic(h, name)
			for (r in v) result.add(r.relative(i))
		}
		result
	}

	List<VariableReference> getAllStatic(String name) { getAllStatic(name.hashCode(), name) }

	VariableReference findStatic(String name, Type expected) {
		def match = new ArrayList<VariableReference>()
		for (d in getAllStatic(name)) {
			def typ = d.variable.type
			if (typ.relation(expected).assignableTo) match.add(d)
		}
		if (match.empty) return null
		def winner = match.get(0)
		for (int i = 1; i < match.size(); ++i) {
			final e = match.get(i)
			if (winner.variable.type.losesAgainst(e.variable.type)) winner = e
		}
		winner
	}

	IKismetObject get(String name) {
		getVariable(name).value
	}

	void set(String name, IKismetObject value) {
		getVariable(name).value = value
	}

	IKismetObject get(int id) {
		getVariable(id).value
	}

	void set(int id, IKismetObject value) {
		final v = getVariable(id)
		if (null != v) v.value = value
		else {
			def n = new Variable(null, id)
			n.value = value
			if (id >= variables.size()) variables.add(n)
			else variables.set(id, n)
		}
	}

	static class Variable implements Address {
		Type type
		String name
		int hash, id
		IKismetObject value

		Variable(String name, int id, Type type = Type.ANY) {
			this.name = name
			hash = name.hashCode()
			this.id = id
			this.type = type
		}

		VariableReference ref() {
			new VariableReference(this, new ArrayDeque<>())
		}
	}

	static class VariableReference {
		Variable variable
		Deque<Integer> heritagePath

		VariableReference(Variable variable, Deque<Integer> heritagePath = new ArrayDeque<>()) {
			this.variable = variable
			this.heritagePath = heritagePath
		}

		int[] getPathArray() {
			def arr = new int[heritagePath.size()]
			int i = 0
			for (h in heritagePath) arr[i++] = h
			arr
		}

		VariableReference relative(Integer id) {
			heritagePath.addFirst(id)
			this
		}

		IKismetObject get(Memory context) {
			def mem = context
			for (index in heritagePath) mem = mem.relative(index)
			mem.get(variable.id)
		}

		IKismetObject set(Memory context, IKismetObject value) {
			def mem = context
			for (index in heritagePath) mem = mem.relative(index)
			mem.set(variable.id, value)
			value
		}
	}
}
