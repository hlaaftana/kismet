package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.TypedContext

@CompileStatic
class RuntimeMemory {
	RuntimeMemory parent
	IKismetObject[] memory

	RuntimeMemory(TypedContext contxt) {
		parent = new RuntimeMemory(contxt.parent)
		memory = new IKismetObject[contxt.variables.size()]
	}

	RuntimeMemory(RuntimeMemory parent, int stackSize) {
		this.parent = parent
		memory = new IKismetObject[stackSize]
	}

	IKismetObject get(int id, int p) {
		def contx = this
		for (int i = 0; i < p; ++i) contx = contx.parent
		contx.memory[id]
	}

	void set(int id, int p, IKismetObject obj) {
		def contx = this
		for (int i = 0; i < p; ++i) contx = contx.parent
		contx.memory[id] = obj
	}
}
