package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.scope.Address
import hlaaftana.kismet.scope.Prelude
import hlaaftana.kismet.scope.TypedContext
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
	boolean isRuntimeOnly() { true }
}

@CompileStatic
class BasicTypedExpression extends TypedExpression {
	Type type
	Instruction instruction

	BasicTypedExpression(Type type, Instruction instruction) {
		this.type = type
		this.instruction = instruction
	}
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
	boolean isRuntimeOnly() { !(reference.variable instanceof Address) }
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

	Type getType() { reference.variable.type }
	boolean isRuntimeOnly() { !(reference.variable instanceof Address) }
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
		other.evaluate(new RuntimeMemory([context] as Memory[], stackSize))
	}

	byte[] getBytes() {
		final o = other.bytes
		ByteBuffer.allocate(5 + o.length).put((byte) 5).putInt(stackSize).put(o).array()
	}
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
}

@CompileStatic
class IdentityInstruction<T extends IKismetObject> extends Instruction {
	T value
	
	IdentityInstruction(T value) {
		this.value = value
	}
	
	T evaluate(Memory context) { value }
}

import hlaaftana.kismet.type.NumberType

@CompileStatic
class TypedNumberExpression extends TypedExpression {
	NumberType type
	Instruction instruction
	Number number

	TypedNumberExpression(Number num) {
		this.number = num
	}

	void setNumber(Number num) {
		this.@number = num
		if (num instanceof Integer) {
			type = NumberType.Int32
		} else if (num instanceof BigInteger) {
			type = NumberType.Int
		} else if (num instanceof BigDecimal) {
			type = NumberType.Float
		} else if (num instanceof Byte) {
			type = NumberType.Int8
		} else if (num instanceof Double) {
			type = NumberType.Float64
		} else if (num instanceof Long) {
			type = NumberType.Int64
		} else if (num instanceof Float) {
			type = NumberType.Float32
		} else if (num instanceof Short) {
			type = NumberType.Int16
		} else throw new UnexpectedValueException("Unknown number $num with class ${num.class} for typed expression")
		instruction = new IdentityInstruction<>(type.instantiate(num))
	}

	boolean isRuntimeOnly() { false }
}

@CompileStatic
class TypedStringExpression extends TypedExpression {
	String string
	Instruction instruction

	TypedStringExpression(String str) {
		string = str
	}

	void setString(String str) {
		this.@string = str
		instruction = new IdentityInstruction<>(new KismetString(string))
	}

	Type getType() { Prelude.STRING_TYPE }
	boolean isRuntimeOnly() { false }
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
		def arr = new IKismetObject[arguments.length]
		for (int i = 0; i < arguments.length; ++i) arr[i] = arguments[i].evaluate(context)
		def val = value.evaluate(context)
		((Function) val).call(arr)
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
}

@CompileStatic
class TypedCallExpression extends BasicTypedExpression {
	TypedExpression value
	TypedExpression[] arguments

	TypedCallExpression(TypedExpression value, TypedExpression[] arguments, Type type) {
		super(type, new CallInstruction(value, arguments))
		this.value = value
		this.arguments = arguments
	}

	boolean isRuntimeOnly() {
		if (value.runtimeOnly) return true
		for (final arg : arguments) if (arg.runtimeOnly) return true
		false
	}
}