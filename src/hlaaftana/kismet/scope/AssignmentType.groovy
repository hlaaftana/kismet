package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.VariableExistsException
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
enum AssignmentType {
	ASSIGN {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v) c.add(name, value)
			else v.value = value
		}
	}, DEFINE {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v) c.add(name, value)
			else throw new VariableExistsException("Cannot define existing variable $name to $value")
		}
	}, SET {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getVariable(name)
			if (null == v) c.add(name, value)
			else v.value = value
		}
	}, CHANGE {
		void set(Context c, String name, IKismetObject value) {
			final v = c.getSafe(name)
			if (null == v)
				throw new UndefinedVariableException("Can't change undefined variable $name to $value")
			else v.value = value
		}
	}

	abstract void set(Context c, String name, IKismetObject value)
}
