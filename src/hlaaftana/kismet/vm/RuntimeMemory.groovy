package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.scope.TypedContext

@CompileStatic
class RuntimeMemory extends Memory {
	Memory[] heritage
	IKismetObject[] memory

	RuntimeMemory(TypedContext contxt) {
		heritage = new Memory[contxt.heritage.size()]
		memory = new IKismetObject[contxt.size()]
		for (int i = 0; i < memory.length; ++i) {
			memory[i] = contxt.get(i)
		}
	}

	RuntimeMemory(Memory[] heritage, int stackSize) {
		this.heritage = heritage
		memory = new IKismetObject[stackSize]
	}

	IKismetObject get(int id) { try { memory[id] } catch (ArrayIndexOutOfBoundsException ignored) { null } }

	void set(int id, IKismetObject obj) { memory[id] = obj }

	Memory relative(int id) { heritage[id] }

	int size() { memory.length }
}
