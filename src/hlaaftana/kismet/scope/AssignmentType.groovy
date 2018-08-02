package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.UnexpectedTypeException
import hlaaftana.kismet.exceptions.VariableExistsException
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
enum AssignmentType {
	ASSIGN {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v) c.add(name, value)
			else v.value = value
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final v = tc.find(name)
			if (null == v) tc.addVariable(name, type).ref()
			else {
				if (!type.relation(v.variable.type).assignableTo)
					throw new UnexpectedTypeException("Could not assign variable $name with type $v.variable.type to type $type")
				v
			}
		}
	}, DEFINE {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v) c.add(name, value)
			else throw new VariableExistsException("Cannot define existing variable $name to $value")
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final v = tc.find(name)
			if (null == v) tc.addVariable(name, type).ref()
			else {
				throw new VariableExistsException("Cannot define existing variable $name (tried to assign type $type)")
			}
		}
	}, SET {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getVariable(name)
			if (null == v) c.add(name, value)
			else v.value = value
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final v = tc.getVariable(name)
			if (null == v) tc.addVariable(name, type).ref()
			else {
				if (!type.relation(v.type).assignableTo)
					throw new UnexpectedTypeException("Could not assign variable $name with type $v.type to type $type")
				v.ref()
			}
		}
	}, CHANGE {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v)
				throw new UndefinedVariableException("Can't change undefined variable $name to $value")
			else v.value = value
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final v = tc.find(name)
			if (null == v)
				throw new UndefinedVariableException("Can't change undefined variable $name to type $type")
			else {
				if (!type.relation(v.variable.type).assignableTo)
					throw new UnexpectedTypeException("Could not assign variable $name with type $v.variable.type to type $type")
				v
			}
		}
	}

	abstract void set(Context c, String name, IKismetObject value)
	abstract TypedContext.VariableReference set(TypedContext tc, String name, Type type)
}
