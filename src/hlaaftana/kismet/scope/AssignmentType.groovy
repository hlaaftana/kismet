package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.VariableExistsException
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeBound
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
			final v = tc.find(name, new TypeBound(type))
			if (null == v) tc.addVariable(name, type).ref()
			else v
		}
	}, DEFINE {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v) c.add(name, value)
			else throw new VariableExistsException("Cannot define existing variable $name to $value")
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final v = tc.find(name, new TypeBound(type), false)
			if (null == v) tc.addVariable(name, type).ref()
			else throw new VariableExistsException("Cannot define existing variable $name (tried to assign type $type)")
		}
	}, SHADOW {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v) c.add(name, value)
			else c.set(name, value)
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final v = tc.find(name, new TypeBound(type), false)
			if (null != v) {
				v.variable.name = null
				v.variable.hash = 0
			}
			tc.addVariable(name, type).ref()
		}
	}, SET {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getVariable(name)
			if (null == v) c.add(name, value)
			else v.value = value
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final winner = tc.find(name, false)
			if (null == winner) tc.addVariable(name, type).ref()
			else winner
		}
	}, CHANGE {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v)
				throw new UndefinedVariableException("Can't change undefined variable $name to $value")
			else v.value = value
		}

		TypedContext.VariableReference set(TypedContext tc, String name, Type type) {
			final v = tc.find(name, new TypeBound(type))
			if (null == v)
				throw new UndefinedVariableException("Can't change undefined variable $name to type $type")
			else v
		}
	}

	abstract void set(Context c, String name, IKismetObject value)
	abstract TypedContext.VariableReference set(TypedContext tc, String name, Type type)
}
