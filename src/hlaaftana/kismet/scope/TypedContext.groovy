package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.TypedExpression
import hlaaftana.kismet.exceptions.ForbiddenAccessException
import hlaaftana.kismet.exceptions.UnexpectedTypeException
import hlaaftana.kismet.type.FunctionType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeRelation
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

	StaticVariable getStatic(int i) {
		def var = getVariable(i)
		if (var instanceof StaticVariable) var
		else {
			def name = var.name
			def errmsg = new StringBuilder("Variable ")
			if (null != name) errmsg.append(name).append((char) ' ')
			errmsg.append("with index ").append(i).append(" was not static variable")
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

	StaticVariable addStaticVariable(String name = null, IKismetObject value, Type type = Type.ANY) {
		final var = new StaticVariable(name, variables.size(), value, type)
		variables.add(var)
		var
	}

	Variable getVariable(String name) {
		final h = name.hashCode()
		for (var in variables) if (h == var.hash && name == var.name) return var
		null
	}

	List<VariableReference> calls(String name) {
		final h = name.hashCode()
		def result = new ArrayList<VariableReference>()
		for (var in variables)
			if (var.hash == h && var.name == name && var.type instanceof FunctionType)
				result.add(new VariableReference(var))
		for (int i = 0; i < heritage.size(); ++i) {
			def p = heritage.get(i).calls(name)
			for (c in p) result.add(c.relative(i))
		}
		result
	}

	@SuppressWarnings("GroovyVariableNotAssigned")
	VariableReference findCall(String name, TypedExpression[] args, Type returnType = Type.ANY) {
		def match = new ArrayList<VariableReference>()
		def matchRels = new ArrayList<List<TypeRelation>>()
		big: for (d in calls(name)) {
			def typ = (FunctionType) d.variable.type
			if (typ.parameters.elements.length != args.length) continue
			def rels = new ArrayList<TypeRelation>(args.length)
			def rtrel = returnType.relation(typ.returnType)
			if (!rtrel.assignableTo) continue
			rels.add(rtrel)
			for (int i = 0; i < args.length; ++i) {
				final rel = args[i].type.relation(typ.parameters.elements[i])
				if (!rel.assignableTo) continue big
				rels.add(rel)
			}
			match.add(d)
			matchRels.add(rels)
		}
		if (match.empty) return null
		def winner = match.get(0)
		def winnerRels = matchRels.get(0)
		for (int i = 1; i < match.size(); ++i) {
			final rels = matchRels.get(i)
			for (int j = 0; j < rels.size(); ++j) {
				if (winnerRels.get(j).worse(rels.get(j))) {
					winner = match.get(i)
					winnerRels = rels
				}
			}
		}
		winner
	}

	VariableReference find(int h, String name) {
		for (var in variables)
			if (var.hash == h && var.name == name)
				return var.ref()
		else for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).find(h, name)
			if (null != v) return v.relative(i)
		}
		null
	}

	VariableReference find(String name) { find(name.hashCode(), name) }

	IKismetObject getStatic(int h, String name) {
		for (var in variables) if (var instanceof StaticVariable && var.hash == h && var.name == name) return var.value
		for (r in heritage) {
			def v = r.getStatic(h, name)
			if (null != v) return v
		}
		(IKismetObject) null
	}

	IKismetObject getStatic(String name) { getStatic(name.hashCode(), name) }

	private String str(Variable var) {
		final typestr = var.type.toString()
		final len = var.name.length() + typestr.length() + 2
		StringBuilder result
		if (null == label) result = new StringBuilder(len)
		else {
			result = new StringBuilder(len + label.length() + 1)
			result.append(label).append((char) '@')
		}
		result.append(var.name).append(': ').append(typestr).toString()
	}

	VariableReference findAny(String name, Type preferred = Type.ANY, List<String> failed = null) {
		final var = getVariable(name)
		if (null != var)
			if (preferred.relation(var.type).assignableTo) return var.ref()
			else if (null != failed) failed.add(str(var))
		else for (int i = 0; i < heritage.size(); ++i) {
			final v = heritage.get(i).findAny(name, preferred, failed)
			if (null != v) return v.relative(i)
		}
		null
	}

	IKismetObject get(int id) {
		final v = getVariable(id)
		if (v instanceof StaticVariable) ((StaticVariable) v).value
		else null
	}

	void set(int id, IKismetObject value) {
		final v = getVariable(id)
		if (v instanceof StaticVariable) ((StaticVariable) v).value = value
		else variables.set(id, new StaticVariable(null, id, value, value.type))
	}

	static class Variable {
		Type type
		String name
		int hash, id

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

	static class StaticVariable extends Variable implements Address {
		IKismetObject value

		StaticVariable(String name, int id, IKismetObject value, Type type = Type.ANY) {
			super(name, id, type)
			this.value = value
		}

		void setValue(IKismetObject val) {
			if (!type.relation(val.type).assignableFrom)
				throw new UnexpectedTypeException("Tried to set static variable $name with type $type to $val with type $val.type")
			this.@value = value
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
