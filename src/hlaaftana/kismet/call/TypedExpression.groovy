package hlaaftana.kismet.call

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.*

import java.nio.ByteBuffer

@CompileStatic
// abstraction for now
abstract class Instruction {
	abstract IKismetObject evaluate(RuntimeMemory context)

	byte[] getBytes() { throw new UnsupportedOperationException('Cant turn ' + this + ' to bytes, roer.') }
}

@CompileStatic
abstract class TypedExpression {
	abstract Type getType()
	abstract Instruction getInstruction()
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
	IKismetObject evaluate(RuntimeMemory context) {
		Kismet.NULL
	}

	byte[] getBytes() { [(byte) 0] as byte[] }
}

@CompileStatic
@Singleton(property = 'INSTANCE')
class TypedNoExpression extends TypedExpression {
	Type getType() { Type.NONE }
	Instruction getInstruction() { NoInstruction.INSTANCE }
}

@CompileStatic
class VariableInstruction extends Instruction {
	int id
	int parent

	VariableInstruction(int id, int parent) {
		this.id = id
		this.parent = parent
	}

	IKismetObject evaluate(RuntimeMemory context) {
		context.get(id, parent)
	}

	byte[] getBytes() {
		ByteBuffer.allocate(9).put((byte) 1).putInt(id).putInt(parent).array()
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
			$instruction = new VariableInstruction(reference.variable.id, reference.parentPath)
		}
		$instruction
	}

	Type getType() { reference.variable.type }
}

@CompileStatic
class VariableSetInstruction extends Instruction {
	int id
	int parent
	Instruction value

	VariableSetInstruction(int id, int parent, Instruction value) {
		this.id = id
		this.parent = parent
		this.value = value
	}

	IKismetObject evaluate(RuntimeMemory context) {
		final val = value.evaluate(context)
		context.set(id, parent, val)
		val
	}

	byte[] getBytes() {
		def valueBytes = value.bytes
		ByteBuffer.allocate(9 + valueBytes.length).put((byte) 2).putInt(id).putInt(parent).put(valueBytes).array()
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

	VariableInstruction $instruction

	Instruction getInstruction() {
		if (null == $instruction) {
			$instruction = new VariableInstruction(reference.variable.id, reference.parentPath)
		}
		$instruction
	}

	Type getType() { reference.variable.type }
}

@CompileStatic
class DiveInstruction extends Instruction {
	int stackSize
	Instruction other

	DiveInstruction(int stackSize, Instruction other) {
		this.stackSize = stackSize
		this.other = other
	}

	IKismetObject evaluate(RuntimeMemory context) {
		other.evaluate(new RuntimeMemory(context, stackSize))
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
			$instruction = new DiveInstruction(context.variables.size(), inner.instruction)
		}
		$instruction
	}
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
	IKismetObject evaluate(RuntimeMemory context) {
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
}

@CompileStatic
class IdentityInstruction<T extends IKismetObject> extends Instruction {
	T value
	
	IdentityInstruction(T value) {
		this.value = value
	}
	
	T evaluate(RuntimeMemory context) { value }
}

import hlaaftana.kismet.type.NumberType

@CompileStatic
class TypedNumberExpression extends TypedExpression {
	Type type
	Instruction instruction
	Number number

	TypedNumberExpression(Number num) {
		this.number = num
	}

	void setNumber(Number num) {
		this.@number = num
		if (num instanceof Integer) {
			type = NumberType.Int32
			instruction = new IdentityInstruction<>(new KInt32(num.intValue()))
		} else if (num instanceof BigInteger) {
			type = NumberType.Int
			instruction = new IdentityInstruction<>(new KInt((BigInteger) num))
		} else if (num instanceof BigDecimal) {
			type = NumberType.Float
			instruction = new IdentityInstruction<>(new KFloat((BigDecimal) num))
		} else if (num instanceof Byte) {
			type = NumberType.Int8
			instruction = new IdentityInstruction<>(new KInt8(num.byteValue()))
		} else if (num instanceof Double) {
			type = NumberType.Float64
			instruction = new IdentityInstruction<>(new KFloat64(num.doubleValue()))
		} else if (num instanceof Long) {
			type = NumberType.Int64
			instruction = new IdentityInstruction<>(new KInt64(num.longValue()))
		} else if (num instanceof Float) {
			type = NumberType.Float32
			instruction = new IdentityInstruction<>(new KFloat32(num.floatValue()))
		} else if (num instanceof Short) {
			type = NumberType.Int16
			instruction = new IdentityInstruction<>(new KInt16(num.shortValue()))
		} else throw new UnexpectedValueException("Unknown number $num with class ${num.class} for typed expression")
	}
}

import hlaaftana.kismet.type.StringType

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

	Type getType() { StringType.INSTANCE }
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
		this(new VariableInstruction(var.variable.id, var.parentPath), zro)
	}

	CallInstruction(TypedExpression value, TypedExpression[] zro) {
		this(value.instruction, zro)
	}

	@Override
	IKismetObject evaluate(RuntimeMemory context) {
		def arr = new IKismetObject[arguments.length]
		for (int i = 0; i < arguments.length; ++i) arr[i] = arguments[i].evaluate(context)
		def val = value.evaluate(context)
		// will change to call symbol lookup
		val.kismetClass().call(val, arr)
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

// will be entirely replaced by DeclarationCallExpression
@CompileStatic
abstract class TypedCallExpression extends TypedExpression {
	TypedExpression[] arguments

	TypedCallExpression(TypedExpression[] arguments) {
		this.arguments = arguments
	}

	abstract Instruction getValueInstruction()
	Instruction getInstruction() { new CallInstruction(valueInstruction, arguments) }
}

@CompileStatic
// based!
class DeclarationCallExpression extends TypedCallExpression {
	TypedContext.DeclarationReference value

	DeclarationCallExpression(TypedContext.DeclarationReference value, TypedExpression[] arguments) {
		super(arguments)
		this.value = value
	}

	Type getType() {
		value.declaration.returnType
	}

	Instruction getValueInstruction() {
		new VariableInstruction(value.declaration.variable.id, value.parentPath)
	}
}

@CompileStatic
// cringe!
class ValueCallExpression extends TypedCallExpression {
	TypedExpression value

	ValueCallExpression(TypedExpression value, TypedExpression[] arguments) {
		super(arguments)
		this.value = value
	}

	Type getType() { Type.ANY }

	Instruction getValueInstruction() { value.instruction }
}