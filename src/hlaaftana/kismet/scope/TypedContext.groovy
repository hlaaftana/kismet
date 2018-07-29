package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.TypedExpression
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeRelation
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.RuntimeMemory

@CompileStatic
class TypedContext {
	String label
	TypedContext parent
	List<Variable> variables = new ArrayList<>()
	List<CallDeclaration> declarations = new ArrayList<>()

	TypedContext child() {
		def result = new TypedContext()
		result.parent = this
		result
	}

	void addVariable(Variable variable) {
		variables.add(variable)
	}

	Variable addVariable(String name, Type type = Type.ANY) {
		def var = new Variable(name, variables.size())
		var.type = type
		variables.add(var)
		var
	}

	Variable getVariable(String name) {
		final h = name.hashCode()
		for (var in variables) if (h == var.hash && name == var.name) return var
		null
	}

	List<CallDeclaration> getCallDeclarations(String name) {
		final h = name.hashCode()
		def result = new ArrayList<CallDeclaration>()
		for (call in declarations) if (call.nameHash == h && call.name == name) result.add(call)
		result
	}

	List<DeclarationReference> calls(String name) {
		final h = name.hashCode()
		def result = new ArrayList<DeclarationReference>()
		for (call in declarations)
			if (call.nameHash == h && call.name == name)
				result.add(new DeclarationReference(call, 0))
		def p = parent.calls(name)
		for (c in p) result.add(c.parent())
		result
	}

	DeclarationReference findCall(String name, TypedExpression[] args, Type returnType = Type.ANY) {
		def match = new ArrayList<DeclarationReference>()
		def matchRels = new ArrayList<List<TypeRelation>>()
		big: for (d in calls(name)) {
			if (d.declaration.argumentLength == args.length &&
					returnType.relation(d.declaration.returnType).assignableTo) {
				def rels = new ArrayList<TypeRelation>(args.length)
				for (int i = 0; i < args.length; ++i) {
					final rel = args[i].type.relation(d.declaration.getArgumentType(i))
					if (!rel.assignableTo) continue big
					rels.add(rel)
				}
				match.add(d)
				matchRels.add(rels)
			}
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

	VariableReference get(String name) {
		final var = getVariable(name)
		if (null != var) return var.ref()
		else {
			final v = parent.get(name)
			if (null != v) return v.parent()
		}
		null
	}

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

	VariableReference getAny(String name, Type preferred = Type.ANY, List<String> failed = null) {
		final var = getVariable(name)
		if (null != var)
			if (preferred.relation(var.type).assignableTo) return var.ref()
			else if (null != failed) failed.add(str(var))
		else for (d in getCallDeclarations(name))
			if (preferred.relation(d.variable.type).assignableTo) return var.ref()
			else if (null != failed) failed.add(str(var))
		else {
			final v = parent.getAny(name, preferred, failed)
			if (null != v) return v.parent()
		}
		null
	}

	static class Variable {
		Type type
		String name
		int hash, id

		Variable(String name, int id) {
			this.name = name
			hash = name.hashCode()
			this.id = id
		}

		VariableReference ref(int parent = 0) {
			new VariableReference(this, parent)
		}
	}

	static class VariableReference {
		Variable variable
		int parentPath

		VariableReference(Variable variable, int parentPath) {
			this.variable = variable
			this.parentPath = parentPath
		}

		VariableReference parent() {
			++parentPath
			this
		}

		IKismetObject get(RuntimeMemory context) {
			context.get(variable.id, parentPath)
		}

		IKismetObject set(RuntimeMemory context, IKismetObject value) {
			context.set(variable.id, parentPath, value)
			value
		}
	}

	static class DeclarationReference {
		CallDeclaration declaration
		int parentPath

		DeclarationReference(CallDeclaration declaration, int parentPath) {
			this.declaration = declaration
			this.parentPath = parentPath
		}

		DeclarationReference parent() {
			++parentPath
			this
		}

		VariableReference getVariable() {
			new VariableReference(declaration.variable, parentPath)
		}
	}
}
