package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.ForbiddenAccessException
import hlaaftana.kismet.exceptions.UndefinedSymbolException
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeBound
import hlaaftana.kismet.type.TypeRelation
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory

@CompileStatic
class TypedContext extends Memory {
	String label
	Module module
	List<TypedContext> heritage = new ArrayList<>()
	List<Variable> variables = new ArrayList<>()

	TypedContext() {}
	TypedContext(String name) {
		label = name
	}

	TypedContext child(Module module = this.module) {
		def result = new TypedContext()
		result.heritage.add(this)
		result.module = module
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

	VariableReference find(int h, String name, boolean doParents = true) {
		for (var in variables)
			if (var.hash == h && var.name == name)
				return var.ref()
		if (doParents) for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).find(h, name)
			if (null != v) return v.relative(i)
		}
		null
	}

	VariableReference find(String name, boolean doParents = true) { find(name.hashCode(), name, doParents) }

	List<VariableReference> getAll(int h, String name, boolean doParents = true) {
		def result = new ArrayList<VariableReference>()
		for (var in variables)
			if (var.hash == h && var.name == name)
				result.add(var.ref())
		if (doParents)
			for (int i = 0; i < heritage.size(); ++i) {
				final v = heritage.get(i).getAll(h, name)
				for (r in v) result.add(r.relative(i))
			}
		result
	}

	List<VariableReference> getAll(String name, boolean doParents = true) { getAll(name.hashCode(), name, doParents) }

	VariableReference find(String name, TypeBound expected, boolean doParents = true) {
		def match = new ArrayList<Tuple2<VariableReference, TypeRelation>>()
		for (d in getAll(name, doParents)) {
			def rel = expected.relation(d.variable.type)
			if (rel.assignableFrom) match.add(new Tuple2<>(d, rel))
		}
		if (match.empty) return null
		def winner = match.get(0)
		for (int i = 1; i < match.size(); ++i) {
			final e = match.get(i)
			if (winner.v2.toSome() < e.v2.toSome()) winner = e
		}
		winner.v1
	}

	List<VariableReference> getAll(Set<String> names, boolean doParents = true) {
		def result = new ArrayList<VariableReference>()
		for (var in variables)
			if (names.contains(var.name))
				result.add(var.ref())
		if (doParents) for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).getAll(names)
			for (r in v) result.add(r.relative(i))
		}
		result
	}

	VariableReference find(Set<String> names, TypeBound expected, boolean doParents = true) {
		def match = new ArrayList<Tuple2<VariableReference, TypeRelation>>()
		for (d in getAll(names, doParents)) {
			def rel = expected.relation(d.variable.type)
			if (rel.assignableFrom) match.add(new Tuple2<>(d, rel))
		}
		if (match.empty) return null
		def winner = match.get(0)
		for (int i = 1; i < match.size(); ++i) {
			final e = match.get(i)
			if (winner.v2.toSome() < e.v2.toSome()) winner = e
		}
		winner.v1
	}

	List<VariableReference> findOverloads(String name, TypeBound expected, boolean doParents = true) {
		def rels = new HashMap<VariableReference, TypeRelation>()
		def match = new ArrayList<VariableReference>()
		for (d in getAll(name, doParents)) {
			def rel = expected.relation(d.variable.type)
			if (rel.assignableFrom) {
				match.add(d)
				rels.put(d, rel)
			}
		}
		match.sort { -rels.get(it).toSome() }
	}

	VariableReference findThrow(String name, TypeBound expected, boolean doParents = true) {
		def var = find(name, expected, doParents)
		if (null == var) {
			throw new UndefinedSymbolException("Could not find variable with name $name and type $expected")
		}
		else var
	}

	List<VariableReference> getAllStatic(int h, String name, boolean doParents = true) {
		def result = new ArrayList<VariableReference>()
		for (var in variables) {
			if (null != var.value && var.hash == h && var.name == name)
				result.add(var.ref())
		}
		if (doParents) for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).getAllStatic(h, name)
			for (r in v) result.add(r.relative(i))
		}
		result
	}

	List<VariableReference> getAllStatic(String name, boolean doParents = true) { getAllStatic(name.hashCode(), name, doParents) }

	VariableReference findStatic(String name, Type expected, boolean doParents = true) {
		def match = new ArrayList<Tuple2<VariableReference, TypeRelation>>()
		for (d in getAllStatic(name, doParents)) {
			def rel = expected.relation(d.variable.type)
			if (rel.assignableFrom) match.add(new Tuple2<>(d, rel))
		}
		if (match.empty) return null
		def winner = match.get(0)
		for (int i = 1; i < match.size(); ++i) {
			final e = match.get(i)
			if (winner.v2.toSome() < e.v2.toSome()) winner = e
		}
		winner.v1
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

		String toString() {
			"$name: $type"
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

		String toString() {
			"$variable @ $heritagePath"
		}
	}
}
