package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.lib.Strings
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.*

import java.nio.ByteBuffer

@CompileStatic
// abstraction for now
abstract class Instruction {
	abstract IKismetObject evaluate(Memory context)

	byte[] getBytes() { throw new UnsupportedOperationException('Cant turn ' + this + ' to bytes, roer.') }
}

@CompileStatic
abstract class TypedExpression {
	abstract Type getType()
	abstract Instruction getInstruction()
	boolean isRuntimeOnly() { false }
}

@CompileStatic
class BasicTypedExpression extends TypedExpression {
	boolean runtimeOnly
	Type type
	Instruction instruction

	BasicTypedExpression(Type type, Instruction instruction, boolean runtimeOnly = true) {
		this.type = type
		this.instruction = instruction
		this.runtimeOnly = runtimeOnly
	}

	String toString() { "lol($type):\n  $instruction" }
}

@CompileStatic
@Singleton(property = 'INSTANCE')
class NoInstruction extends Instruction {
	IKismetObject evaluate(Memory context) {
		Kismet.NULL
	}

	byte[] getBytes() { [(byte) 0] as byte[] }
}

@CompileStatic
@Singleton(property = 'INSTANCE')
class TypedNoExpression extends TypedExpression {
	Type getType() { Type.NONE }
	Instruction getInstruction() { NoInstruction.INSTANCE }
	boolean isRuntimeOnly() { false }
}

@CompileStatic
class VariableInstruction extends Instruction {
	int id
	int[] path

	VariableInstruction(int id, int[] path) {
		this.id = id
		this.path = path
	}

	IKismetObject evaluate(Memory context) {
		context.get(id, path)
	}

	byte[] getBytes() {
		def res = ByteBuffer.allocate(9 + path.length * 4).put((byte) 1).putInt(id).putInt(path.length)
		for (int i = 0; i < path.length; ++i) res.putInt(path[i])
		res.array()
	}

	String toString() { "${path ? path.join(':') + ' ' : ''}\$$id" }
}

@CompileStatic
class VariableExpression extends TypedExpression {
	TypedContext.VariableReference reference

	VariableExpression(TypedContext.VariableReference vr) {
		reference = vr
	}

	VariableInstruction $instruction

	Instruction getInstruction() {
		if (null == $instruction) {
			$instruction = new VariableInstruction(reference.variable.id, reference.pathArray)
		}
		$instruction
	}

	Type getType() { reference.variable.type }
	boolean isRuntimeOnly() { false }

	String toString() { "$reference.variable.name" }
}

@CompileStatic
class VariableSetInstruction extends Instruction {
	int id
	int[] path
	Instruction value

	VariableSetInstruction(int id, int[] path, Instruction value) {
		this.id = id
		this.path = path
		this.value = value
	}

	IKismetObject evaluate(Memory context) {
		final val = value.evaluate(context)
		context.set(id, path, val)
		val
	}

	byte[] getBytes() {
		def valueBytes = value.bytes
		def res = ByteBuffer.allocate(9 + 4 * path.length + valueBytes.length).put((byte) 2).putInt(id).putInt(path.length)
		for (final p : path) res = res.putInt(p)
		res.put(valueBytes).array()
	}

	String toString() { "${path ? path.join(':') + ' ' : ''}\$$id = $value" }
}

@CompileStatic
class VariableSetExpression extends TypedExpression {
	TypedContext.VariableReference reference
	TypedExpression value

	VariableSetExpression(TypedContext.VariableReference vr, TypedExpression v) {
		reference = vr
		value = v
	}

	VariableSetInstruction $instruction

	Instruction getInstruction() {
		if (null == $instruction) {
			$instruction = new VariableSetInstruction(reference.variable.id, reference.pathArray, value.instruction)
		}
		$instruction
	}

	Type getType() { value.type }
	boolean isRuntimeOnly() { value.runtimeOnly }

	String toString() { "$reference.variable.name = $value" }
}

@CompileStatic
class DiveInstruction extends Instruction {
	int stackSize
	Instruction other

	DiveInstruction(int stackSize, Instruction other) {
		this.stackSize = stackSize
		this.other = other
	}

	IKismetObject evaluate(Memory context) {
		def res = other.evaluate(new RuntimeMemory([context] as Memory[], stackSize))
		res
	}

	byte[] getBytes() {
		final o = other.bytes
		ByteBuffer.allocate(5 + o.length).put((byte) 5).putInt(stackSize).put(o).array()
	}

	String toString() { "dive $stackSize $other" }
}

@CompileStatic
class TypedDiveExpression extends TypedExpression {
	TypedContext context
	TypedExpression inner

	TypedDiveExpression(TypedContext context, TypedExpression inner) {
		this.context = context
		this.inner = inner
	}

	Type getType() { inner.type }

	DiveInstruction $instruction

	Instruction getInstruction() {
		if (null == $instruction) {
			$instruction = new DiveInstruction(context.size(), inner.instruction)
		}
		$instruction
	}
	boolean isRuntimeOnly() { inner.runtimeOnly }

	String toString() { "dive $inner" }
}

@CompileStatic
class SequentialInstruction extends Instruction {
	Instruction[] instructions

	SequentialInstruction(Instruction[] instructions) {
		this.instructions = instructions
	}

	SequentialInstruction(TypedExpression[] zro) {
		this.instructions = new Instruction[zro.length]
		for (int i = 0; i < zro.length; ++i) instructions[i] = zro[i].instruction
	}

	@Override
	IKismetObject evaluate(Memory context) {
		int i = 0
		for (; i < instructions.length - 1; ++i) instructions[i].evaluate(context)
		instructions[i].evaluate(context)
	}

	byte[] getBytes() {
		final L = instructions.length
		def b = new ByteArrayOutputStream(5)
		b.write([(byte) 3, (byte) (L >> 24), (byte) (L >> 16), (byte) (L >> 8), (byte) L] as byte[])
		for (instr in instructions) b.write(instr.bytes)
		b.toByteArray()
	}

	String toString() { "sequence:\n{\n${instructions.join('\n')}\n}" }
}

@CompileStatic
class SequentialExpression extends TypedExpression {
	TypedExpression[] members

	SequentialExpression(TypedExpression[] members) {
		this.members = members
	}

	Type getType() { members.length == 0 ? Type.NONE : members.last().type }
	Instruction getInstruction() { new SequentialInstruction(members) }
	boolean isRuntimeOnly() {
		for (final m : members) if (m.runtimeOnly) return true
		false
	}

	String toString() { "sequence:\n{\n${members.join('\n')}\n}" }
}

@CompileStatic
class ConstantInstruction<T extends IKismetObject> extends Instruction {
	T value

	ConstantInstruction(T value) {
		this.value = value
	}
	
	T evaluate(Memory context) { value }

	String toString() { "constant $value" }
}

@CompileStatic
class TypedConstantExpression<T extends IKismetObject> extends TypedExpression {
	Type type
	T value

	TypedConstantExpression(Type type, T value) {
		this.type = type
		this.value = value
	}

	boolean isRuntimeOnly() { false }

	ConstantInstruction<T> getInstruction() { new ConstantInstruction<T>(value) }

	String toString() { "constant $type $value" }
}

@CompileStatic
class TypedNumberExpression extends TypedExpression {
	NumberType type
	Instruction instruction
	Number number

	TypedNumberExpression(Number num) {
		setNumber(num)
	}

	void setNumber(Number num) {
		this.@number = num
		type = NumberType.from(num)
		if (null == type) throw new UnexpectedValueException("Dont know what type number $num with class ${num.class} is")
		instruction = new ConstantInstruction<>(type.instantiate(num))
	}

	boolean isRuntimeOnly() { false }

	String toString() { "number $type $number" }
}

@CompileStatic
class TypedStringExpression extends TypedExpression {
	String string
	Instruction instruction

	TypedStringExpression(String str) {
		setString(str)
	}

	void setString(String str) {
		this.@string = str
		instruction = new ConstantInstruction<>(new KismetString(string))
	}

	Type getType() { Strings.STRING_TYPE }
	boolean isRuntimeOnly() { false }

	String toString() { "string $string" }
}

@CompileStatic
class CallInstruction extends Instruction {
	Instruction value
	Instruction[] arguments

	CallInstruction(Instruction value, Instruction[] arguments) {
		this.value = value
		this.arguments = arguments
	}

	CallInstruction(Instruction value, TypedExpression[] zro) {
		this.value = value
		this.arguments = new Instruction[zro.length]
		for (int i = 0; i < zro.length; ++i) arguments[i] = zro[i].instruction
	}

	CallInstruction(TypedContext.VariableReference var, TypedExpression[] zro) {
		this(new VariableInstruction(var.variable.id, var.pathArray), zro)
	}

	CallInstruction(TypedExpression value, TypedExpression[] zro) {
		this(value.instruction, zro)
	}

	@Override
	IKismetObject evaluate(Memory context) {
		def val = value.evaluate(context)
		def arr = new IKismetObject[arguments.length]
		for (int i = 0; i < arguments.length; ++i) arr[i] = arguments[i].evaluate(context)
		def res = ((Function) val).call(arr)
		res
	}

	byte[] getBytes() {
		final argb = new byte[0][arguments.length]
		int argsum = 0
		for (int i = 0; i < argb.length; ++i) {
			argsum += (argb[i] = arguments[i].bytes).length
		}
		final argbt = new byte[argsum]
		int pos = 0
		for (final arg : argb) {
			System.arraycopy(arg, 0, argbt, pos, arg.length)
			pos += arg.length
		}
		final vb = value.bytes
		ByteBuffer.allocate(5 + vb.length + argsum).put((byte) 4)
			.put(vb).putInt(arguments.length).put(argbt).array()
	}

	String toString() { "call $value (${arguments.join(', ')})" }
}

@CompileStatic
class TypedCallExpression extends TypedExpression {
	Type type
	TypedExpression value
	TypedExpression[] arguments

	TypedCallExpression(TypedExpression value, TypedExpression[] arguments, Type type) {
		this.type = type
		this.value = value
		this.arguments = arguments
	}

	Instruction getInstruction() { new CallInstruction(value, arguments) }

	boolean isRuntimeOnly() {
		if (value.runtimeOnly) return true
		for (final arg : arguments) if (arg.runtimeOnly) return true
		false
	}

	String toString() { "call($type) $value(${arguments.join(', ')})" }
}

@CompileStatic
class InstructorCallInstruction extends Instruction {
	Instruction value
	Instruction[] arguments

	InstructorCallInstruction(Instruction value, Instruction[] arguments) {
		this.value = value
		this.arguments = arguments
	}

	InstructorCallInstruction(Instruction value, TypedExpression[] zro) {
		this.value = value
		this.arguments = new Instruction[zro.length]
		for (int i = 0; i < zro.length; ++i) arguments[i] = zro[i].instruction
	}

	InstructorCallInstruction(TypedContext.VariableReference var, TypedExpression[] zro) {
		this(new VariableInstruction(var.variable.id, var.pathArray), zro)
	}

	InstructorCallInstruction(TypedExpression value, TypedExpression[] zro) {
		this(value.instruction, zro)
	}

	@Override
	IKismetObject evaluate(Memory context) {
		def val = value.evaluate(context)
		def res = ((Instructor) val).call(context, arguments)
		res
	}

	byte[] getBytes() {
		final argb = new byte[0][arguments.length]
		int argsum = 0
		for (int i = 0; i < argb.length; ++i) {
			argsum += (argb[i] = arguments[i].bytes).length
		}
		final argbt = new byte[argsum]
		int pos = 0
		for (final arg : argb) {
			System.arraycopy(arg, 0, argbt, pos, arg.length)
			pos += arg.length
		}
		final vb = value.bytes
		ByteBuffer.allocate(5 + vb.length + argsum).put((byte) 6)
				.put(vb).putInt(arguments.length).put(argbt).array()
	}

	String toString() { "instructor call $value(${arguments.join(', ')})" }
}

@CompileStatic
class InstructorCallExpression extends TypedExpression {
	Type type
	TypedExpression value
	TypedExpression[] arguments

	InstructorCallExpression(TypedExpression value, TypedExpression[] arguments, Type type) {
		this.type = type
		this.value = value
		this.arguments = arguments
	}

	Instruction getInstruction() { new InstructorCallInstruction(value, arguments) }

	boolean isRuntimeOnly() {
		if (value.runtimeOnly) return true
		for (final arg : arguments) if (arg.runtimeOnly) return true
		false
	}

	String toString() { "instructor call($type) $value(${arguments.join(', ')})" }
}

@CompileStatic
class IfElseInstruction extends Instruction {
	Instruction condition, branch, elseBranch

	IfElseInstruction(Instruction condition, Instruction branch, Instruction elseBranch) {
		this.condition = condition
		this.branch = branch
		this.elseBranch = elseBranch
	}

	IKismetObject evaluate(Memory context) {
		if (((KismetBoolean) condition.evaluate(context)).inner) branch.evaluate(context)
		else elseBranch.evaluate(context)
	}

	String toString() { "if $condition:\n  ${branch.toString().readLines().join('\n  ')}\nelse:\n  ${elseBranch.toString().readLines().join('\n  ')}" }
}

@CompileStatic
class IfElseExpression extends TypedExpression {
	TypedExpression condition, branch, elseBranch

	IfElseExpression(TypedExpression condition, TypedExpression branch, TypedExpression elseBranch) {
		this.condition = condition
		this.branch = branch
		this.elseBranch = elseBranch
	}

	Type getType() {
		final b = branch.type, e = elseBranch.type
		def rel = b.relation(e)
		if (rel.none) Type.NONE
		else if (rel.sub) e
		else b
	}

	Instruction getInstruction() { new IfElseInstruction(condition.instruction, branch.instruction, elseBranch.instruction) }

	String toString() { "if $condition:\n  ${branch.toString().readLines().join('\\n  ')}\nelse:\n  ${elseBranch.toString().readLines().join('\n  ')}" }
}

@CompileStatic
class WhileInstruction extends Instruction {
	Instruction condition, branch

	WhileInstruction(Instruction condition, Instruction branch) {
		this.condition = condition
		this.branch = branch
	}

	IKismetObject evaluate(Memory context) {
		while (((KismetBoolean) condition.evaluate(context)).inner) branch.evaluate(context)
		Kismet.NULL
	}

	String toString() { "while $condition:\n  ${branch.toString().readLines().join('\n  ')}" }
}

@CompileStatic
class WhileExpression extends TypedExpression {
	TypedExpression condition, branch

	WhileExpression(TypedExpression condition, TypedExpression branch) {
		this.condition = condition
		this.branch = branch
	}

	Type getType() { Type.NONE }

	Instruction getInstruction() { new WhileInstruction(condition.instruction, branch.instruction) }

	String toString() { "while $condition:\n  ${branch.toString().readLines().join('\\n  ')}" }
}

@CompileStatic
class DoUntilInstruction extends Instruction {
	Instruction condition, branch

	DoUntilInstruction(Instruction condition, Instruction branch) {
		this.condition = condition
		this.branch = branch
	}

	IKismetObject evaluate(Memory context) {
		while (true) {
			branch.evaluate(context)
			if (((KismetBoolean) condition.evaluate(context)).inner) break
		}
		Kismet.NULL
	}

	String toString() { "do_until $condition:\n  ${branch.toString().readLines().join('\n  ')}" }
}

@CompileStatic
class DoUntilExpression extends TypedExpression {
	TypedExpression condition, branch

	DoUntilExpression(TypedExpression condition, TypedExpression branch) {
		this.condition = condition
		this.branch = branch
	}

	Type getType() { Type.NONE }

	Instruction getInstruction() { new DoUntilInstruction(condition.instruction, branch.instruction) }

	String toString() { "do_until $condition:\n  ${branch.toString().readLines().join('\\n  ')}" }
}

@CompileStatic
class OnceInstruction extends Instruction {
	Instruction inner
	IKismetObject stored

	OnceInstruction(Instruction inner) {
		this.inner = inner
	}

	IKismetObject evaluate(Memory context) {
		if (null == stored) stored = inner.evaluate(context)
		stored
	}

	String toString() { "once($stored) $inner" }
}

@CompileStatic
class TypedOnceExpression extends TypedExpression {
	TypedExpression inner

	TypedOnceExpression(TypedExpression inner) {
		this.inner = inner
	}

	Type getType() { inner.type }

	Instruction getInstruction() { new OnceInstruction(inner.instruction) }

	String toString() { "once $inner" }
}