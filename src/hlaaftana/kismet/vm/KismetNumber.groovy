package hlaaftana.kismet.vm

import groovy.transform.CompileStatic
import hlaaftana.kismet.exceptions.CannotOperateException
import hlaaftana.kismet.type.NumberType

@CompileStatic
abstract class KismetNumber<T extends Number> extends Number implements IKismetObject<T>, Comparable<KismetNumber<T>> {
	abstract T inner()

	abstract void set(Number value)

	abstract KismetNumber plus(KismetNumber obj)

	abstract KismetNumber minus(KismetNumber obj)

	abstract KismetNumber multiply(KismetNumber obj)

	abstract KismetNumber div(KismetNumber obj)

	abstract KismetNumber intdiv(KismetNumber obj)

	abstract KismetNumber mod(KismetNumber obj)

	abstract KismetNumber unaryPlus()

	abstract KismetNumber unaryMinus()

	abstract int compareTo(KismetNumber obj)

	abstract KismetNumber leftShift(KismetNumber obj)

	abstract KismetNumber rightShift(KismetNumber obj)

	abstract KismetNumber rightShiftUnsigned(KismetNumber obj)

	abstract KismetNumber and(KismetNumber obj)

	abstract KismetNumber or(KismetNumber obj)

	abstract KismetNumber xor(KismetNumber obj)
	
	abstract NumberType getType()

	int hashCode() { inner().hashCode() }

	boolean equals(obj) { inner().equals(obj) }

	byte byteValue() { inner().byteValue() }

	short shortValue() { inner().shortValue() }

	int intValue() { inner().intValue() }

	long longValue() { inner().longValue() }

	float floatValue() { inner().floatValue() }

	double doubleValue() { inner().doubleValue() }

	static KismetNumber from(Number val) {
		if (val instanceof BigInteger) new KInt((BigInteger) val)
		else if (val instanceof BigDecimal) new KFloat((BigDecimal) val)
		else if (val instanceof Integer) new KInt32(val.intValue())
		else if (val instanceof Double) new KFloat64(val.doubleValue())
		else if (val instanceof Float) new KFloat32(val.floatValue())
		else if (val instanceof Long) new KInt64(val.longValue())
		else if (val instanceof Short) new KInt16(val.shortValue())
		else if (val instanceof Byte) new KInt8(val.byteValue())
		else new KNonPrimitiveNum(val)
	}

	String toString() { inner().toString() }

	def asType(Class type) { inner().asType(type) }
}


@CompileStatic
final class KNonPrimitiveNum extends KismetNumber {
	Number inner

	KNonPrimitiveNum(Number inner) {
		this.inner = inner
	}

	NumberType getType() { NumberType.Number }

	Number inner() { inner }

	void set(Number value) { inner = value }

	KNonPrimitiveNum plus(KismetNumber obj) {
		new KNonPrimitiveNum(inner.plus(obj.inner()))
	}

	KNonPrimitiveNum minus(KismetNumber obj) {
		new KNonPrimitiveNum(inner.minus(obj.inner()))
	}

	KNonPrimitiveNum multiply(KismetNumber obj) {
		new KNonPrimitiveNum(inner.multiply(obj.inner()))
	}

	KNonPrimitiveNum div(KismetNumber obj) {
		new KNonPrimitiveNum(inner / obj.inner())
	}

	KNonPrimitiveNum intdiv(KismetNumber obj) {
		new KNonPrimitiveNum(inner.intdiv(obj.inner()))
	}

	KNonPrimitiveNum mod(KismetNumber obj) {
		new KNonPrimitiveNum(inner % obj.inner())
	}

	KNonPrimitiveNum unaryPlus() { new KNonPrimitiveNum(inner) }

	KNonPrimitiveNum unaryMinus() {
		new KNonPrimitiveNum(inner.unaryMinus())
	}

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KNonPrimitiveNum leftShift(KismetNumber obj) {
		new KNonPrimitiveNum(inner << obj.inner())
	}

	KNonPrimitiveNum rightShift(KismetNumber obj) {
		new KNonPrimitiveNum(inner >> obj.inner())
	}

	KNonPrimitiveNum rightShiftUnsigned(KismetNumber obj) {
		new KNonPrimitiveNum(inner >>> obj.inner())
	}

	KNonPrimitiveNum and(KismetNumber obj) {
		new KNonPrimitiveNum(inner & obj.inner())
	}

	KNonPrimitiveNum or(KismetNumber obj) {
		new KNonPrimitiveNum(inner | obj.inner())
	}

	KNonPrimitiveNum xor(KismetNumber obj) {
		new KNonPrimitiveNum(inner ^ obj.inner())
	}
}


@CompileStatic
final class KInt extends KismetNumber<BigInteger> {
	BigInteger inner

	KInt(BigInteger inner) { this.inner = inner }

	KInt(Number inner) { set(inner) }

	static BigInteger toBigInt(Number num) {
		if (num instanceof BigInteger) (BigInteger) num
		else if (num instanceof BigDecimal) ((BigDecimal) num).toBigInteger()
		else BigInteger.valueOf(num.longValue())
	}

	NumberType getType() { NumberType.Int }

	BigInteger inner() { inner }

	void set(Number value) { inner = toBigInt(value) }

	KInt plus(KismetNumber obj) {
		new KInt(inner.add(toBigInt(obj.inner())))
	}

	KInt minus(KismetNumber obj) {
		new KInt(inner.subtract(toBigInt(obj.inner())))
	}

	KInt multiply(KismetNumber obj) {
		new KInt(inner.multiply(toBigInt(obj.inner())))
	}

	KInt div(KismetNumber obj) {
		new KInt(inner.divide(toBigInt(obj.inner())))
	}

	KInt intdiv(KismetNumber obj) {
		new KInt(inner.intdiv(toBigInt(obj.inner())))
	}

	KInt mod(KismetNumber obj) {
		new KInt(inner.mod(toBigInt(obj.inner())))
	}

	KInt unaryPlus() { new KInt(inner) }

	KInt unaryMinus() { new KInt(inner.negate()) }

	int compareTo(KismetNumber obj) {
		inner.compareTo(toBigInt(obj.inner()))
	}

	KInt leftShift(KismetNumber obj) {
		new KInt(inner.shiftLeft(obj.intValue()))
	}

	KInt rightShift(KismetNumber obj) {
		new KInt(inner.shiftRight(obj.intValue()))
	}

	KInt rightShiftUnsigned(KismetNumber obj) {
		throw new CannotOperateException("right shift unsigned", "big integer")
	}

	KInt and(KismetNumber obj) {
		new KInt(inner.and(toBigInt(obj.inner())))
	}

	KInt or(KismetNumber obj) {
		new KInt(inner.or(toBigInt(obj.inner())))
	}

	KInt xor(KismetNumber obj) {
		new KInt(inner.xor(toBigInt(obj.inner())))
	}
}


@CompileStatic
final class KFloat extends KismetNumber<BigDecimal> {
	BigDecimal inner

	KFloat(BigDecimal inner) { this.inner = inner }

	KFloat(Number inner) { set(inner) }

	static BigDecimal toBigDec(Number num) {
		if (num instanceof BigDecimal) (BigDecimal) num
		else if (num instanceof BigInteger) ((BigInteger) num).toBigDecimal()
		else BigDecimal.valueOf(num.doubleValue())
	}

	NumberType getType() { NumberType.Float }

	BigDecimal inner() { inner }

	void set(Number value) { inner = toBigDec(value) }

	KFloat plus(KismetNumber obj) {
		new KFloat(inner.add(toBigDec(obj.inner())))
	}

	KFloat minus(KismetNumber obj) {
		new KFloat(inner.subtract(toBigDec(obj.inner())))
	}

	KFloat multiply(KismetNumber obj) {
		new KFloat(inner.multiply(toBigDec(obj.inner())))
	}

	KFloat div(KismetNumber obj) {
		new KFloat(inner.divide(toBigDec(obj.inner())))
	}

	KFloat intdiv(KismetNumber obj) {
		new KFloat(inner.intdiv(toBigDec(obj.inner())))
	}

	KFloat mod(KismetNumber obj) {
		new KFloat(inner.mod(toBigDec(obj.inner())))
	}

	KFloat unaryPlus() { new KFloat(inner) }

	KFloat unaryMinus() { new KFloat(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KFloat leftShift(KismetNumber obj) {
		throw new CannotOperateException("bitwise (left shift)", "big float")
	}

	KFloat rightShift(KismetNumber obj) {
		throw new CannotOperateException("bitwise (right shift)", "big float")
	}

	KFloat rightShiftUnsigned(KismetNumber obj) {
		throw new CannotOperateException("bitwise (right shift unsigned)", "big float")
	}

	KFloat and(KismetNumber obj) {
		throw new CannotOperateException("bitwise (and)", "big float")
	}

	KFloat or(KismetNumber obj) {
		throw new CannotOperateException("bitwise (or)", "big float")
	}

	KFloat xor(KismetNumber obj) {
		throw new CannotOperateException("bitwise (xor)", "big float")
	}
}


@CompileStatic
final class KFloat64 extends KismetNumber<Double> {
	double inner

	KFloat64(double inner) { this.inner = inner }

	NumberType getType() { NumberType.Float64 }

	Double inner() { inner }

	void set(Number value) { inner = value.doubleValue() }

	KFloat64 plus(KismetNumber obj) {
		new KFloat64(inner + obj.doubleValue())
	}

	KFloat64 minus(KismetNumber obj) {
		new KFloat64(inner - obj.doubleValue())
	}

	KFloat64 multiply(KismetNumber obj) {
		new KFloat64(inner * obj.doubleValue())
	}

	KFloat64 div(KismetNumber obj) {
		new KFloat64(inner / obj.doubleValue())
	}

	KFloat64 intdiv(KismetNumber obj) {
		new KFloat64(inner.intdiv(obj.doubleValue()).doubleValue())
	}

	KFloat64 mod(KismetNumber obj) {
		new KFloat64(inner % obj.doubleValue())
	}

	KFloat64 unaryPlus() { new KFloat64(inner) }

	KFloat64 unaryMinus() { new KFloat64(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KFloat64 leftShift(KismetNumber obj) {
		throw new CannotOperateException("bitwise (left shift)", "float64")
	}

	KFloat64 rightShift(KismetNumber obj) {
		throw new CannotOperateException("bitwise (right shift)", "float64")
	}

	KFloat64 rightShiftUnsigned(KismetNumber obj) {
		throw new CannotOperateException("bitwise (right shift unsigned)", "float64")
	}

	KFloat64 and(KismetNumber obj) {
		throw new CannotOperateException("bitwise (and)", "float64")
	}

	KFloat64 or(KismetNumber obj) {
		throw new CannotOperateException("bitwise (or)", "float64")
	}

	KFloat64 xor(KismetNumber obj) {
		throw new CannotOperateException("bitwise (xor)", "float64")
	}
}


@CompileStatic
final class KFloat32 extends KismetNumber<Float> {
	float inner

	KFloat32(float inner) { this.inner = inner }

	NumberType getType() { NumberType.Float32 }

	Float inner() { inner }

	void set(Number value) { inner = value.floatValue() }

	KFloat32 plus(KismetNumber obj) {
		new KFloat32((float) (inner + obj.floatValue()))
	}

	KFloat32 minus(KismetNumber obj) {
		new KFloat32((float) (inner - obj.floatValue()))
	}

	KFloat32 multiply(KismetNumber obj) {
		new KFloat32((float) (inner * obj.floatValue()))
	}

	KFloat32 div(KismetNumber obj) {
		new KFloat32((float) (inner / obj.floatValue()))
	}

	KFloat32 intdiv(KismetNumber obj) {
		new KFloat32(inner.intdiv(obj.floatValue()).floatValue())
	}

	KFloat32 mod(KismetNumber obj) {
		new KFloat32(inner % obj.floatValue())
	}

	KFloat32 unaryPlus() { new KFloat32(inner) }

	KFloat32 unaryMinus() { new KFloat32(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KFloat32 leftShift(KismetNumber obj) {
		throw new CannotOperateException("bitwise (left shift)", "float32")
	}

	KFloat32 rightShift(KismetNumber obj) {
		throw new CannotOperateException("bitwise (right shift)", "float32")
	}

	KFloat32 rightShiftUnsigned(KismetNumber obj) {
		throw new CannotOperateException("bitwise (right shift unsigned)", "float32")
	}

	KFloat32 and(KismetNumber obj) {
		throw new CannotOperateException("bitwise (and)", "float32")
	}

	KFloat32 or(KismetNumber obj) {
		throw new CannotOperateException("bitwise (or)", "float32")
	}

	KFloat32 xor(KismetNumber obj) {
		throw new CannotOperateException("bitwise (xor)", "float32")
	}
}


@CompileStatic
final class KInt64 extends KismetNumber<Long> {
	long inner

	KInt64(long inner) { this.inner = inner }

	NumberType getType() { NumberType.Int64 }

	Long inner() { inner }

	void set(Number value) { inner = value.longValue() }

	KInt64 plus(KismetNumber obj) {
		new KInt64(inner + obj.longValue())
	}

	KInt64 minus(KismetNumber obj) {
		new KInt64(inner - obj.longValue())
	}

	KInt64 multiply(KismetNumber obj) {
		new KInt64(inner * obj.longValue())
	}

	KInt64 div(KismetNumber obj) {
		new KInt64((long) (inner / obj.longValue()))
	}

	KInt64 intdiv(KismetNumber obj) {
		new KInt64(inner.intdiv(obj.longValue()).longValue())
	}

	KInt64 mod(KismetNumber obj) {
		new KInt64(inner % obj.longValue())
	}

	KInt64 unaryPlus() { new KInt64(inner) }

	KInt64 unaryMinus() { new KInt64(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KInt64 leftShift(KismetNumber obj) {
		new KInt64(inner << obj.longValue())
	}

	KInt64 rightShift(KismetNumber obj) {
		new KInt64(inner >> obj.longValue())
	}

	KInt64 rightShiftUnsigned(KismetNumber obj) {
		new KInt64(inner >>> obj.longValue())
	}

	KInt64 and(KismetNumber obj) {
		new KInt64(inner & obj.longValue())
	}

	KInt64 or(KismetNumber obj) {
		new KInt64(inner | obj.longValue())
	}

	KInt64 xor(KismetNumber obj) {
		new KInt64(inner ^ obj.longValue())
	}
}


@CompileStatic
final class KInt32 extends KismetNumber<Integer> {
	int inner

	KInt32(int inner) { this.inner = inner }

	NumberType getType() { NumberType.Int32 }

	Integer inner() { inner }

	void set(Number value) { inner = value.intValue() }

	KInt32 plus(KismetNumber obj) {
		new KInt32(inner + obj.intValue())
	}

	KInt32 minus(KismetNumber obj) {
		new KInt32(inner - obj.intValue())
	}

	KInt32 multiply(KismetNumber obj) {
		new KInt32(inner * obj.intValue())
	}

	KInt32 div(KismetNumber obj) {
		new KInt32((int) (inner / obj.intValue()))
	}

	KInt32 intdiv(KismetNumber obj) {
		new KInt32(inner.intdiv(obj.intValue()).intValue())
	}

	KInt32 mod(KismetNumber obj) {
		new KInt32(inner % obj.intValue())
	}

	KInt32 unaryPlus() { new KInt32(inner) }

	KInt32 unaryMinus() { new KInt32(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KInt32 leftShift(KismetNumber obj) {
		new KInt32(inner << obj.intValue())
	}

	KInt32 rightShift(KismetNumber obj) {
		new KInt32(inner >> obj.intValue())
	}

	KInt32 rightShiftUnsigned(KismetNumber obj) {
		new KInt32(inner >>> obj.intValue())
	}

	KInt32 and(KismetNumber obj) {
		new KInt32(inner & obj.intValue())
	}

	KInt32 or(KismetNumber obj) {
		new KInt32(inner | obj.intValue())
	}

	KInt32 xor(KismetNumber obj) {
		new KInt32(inner ^ obj.intValue())
	}
}


@CompileStatic
final class KRune extends KismetNumber<Integer> {
	int inner

	KRune(int inner) { this.inner = inner }

	NumberType getType() { NumberType.Rune }

	Integer inner() { inner }

	void set(Number value) { inner = value.intValue() }

	KRune plus(KismetNumber obj) {
		new KRune(inner + obj.intValue())
	}

	KRune minus(KismetNumber obj) {
		new KRune(inner - obj.intValue())
	}

	KRune multiply(KismetNumber obj) {
		new KRune(inner * obj.intValue())
	}

	KRune div(KismetNumber obj) {
		new KRune((int) (inner / obj.intValue()))
	}

	KRune intdiv(KismetNumber obj) {
		new KRune(inner.intdiv(obj.intValue()).intValue())
	}

	KRune mod(KismetNumber obj) {
		new KRune(inner % obj.intValue())
	}

	KRune unaryPlus() { new KRune(inner) }

	KRune unaryMinus() { new KRune(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KRune leftShift(KismetNumber obj) {
		new KRune(inner << obj.intValue())
	}

	KRune rightShift(KismetNumber obj) {
		new KRune(inner >> obj.intValue())
	}

	KRune rightShiftUnsigned(KismetNumber obj) {
		new KRune(inner >>> obj.intValue())
	}

	KRune and(KismetNumber obj) {
		new KRune(inner & obj.intValue())
	}

	KRune or(KismetNumber obj) {
		new KRune(inner | obj.intValue())
	}

	KRune xor(KismetNumber obj) {
		new KRune(inner ^ obj.intValue())
	}
}


@CompileStatic
final class KInt16 extends KismetNumber<Short> {
	short inner

	KInt16(short inner) { this.inner = inner }

	NumberType getType() { NumberType.Int16 }

	Short inner() { inner }

	void set(Number value) { inner = value.shortValue() }

	KInt16 plus(KismetNumber obj) {
		new KInt16((short) (inner + obj.shortValue()))
	}

	KInt16 minus(KismetNumber obj) {
		new KInt16((short) (inner - obj.shortValue()))
	}

	KInt16 multiply(KismetNumber obj) {
		new KInt16((short) (inner * obj.shortValue()))
	}

	KInt16 div(KismetNumber obj) {
		new KInt16((short) (inner / obj.shortValue()))
	}

	KInt16 intdiv(KismetNumber obj) {
		new KInt16(inner.intdiv(obj.shortValue()).shortValue())
	}

	KInt16 mod(KismetNumber obj) {
		new KInt16((short) (inner % obj.shortValue()))
	}

	KInt16 unaryPlus() { new KInt16(inner) }

	KInt16 unaryMinus() { new KInt16(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KInt16 leftShift(KismetNumber obj) {
		new KInt16((short) (inner << obj.shortValue()))
	}

	KInt16 rightShift(KismetNumber obj) {
		new KInt16((short) (inner >> obj.shortValue()))
	}

	KInt16 rightShiftUnsigned(KismetNumber obj) {
		new KInt16((short) (inner >>> obj.shortValue()))
	}

	KInt16 and(KismetNumber obj) {
		new KInt16((short) (inner & obj.shortValue()))
	}

	KInt16 or(KismetNumber obj) {
		new KInt16((short) (inner | obj.shortValue()))
	}

	KInt16 xor(KismetNumber obj) {
		new KInt16((short) (inner ^ obj.shortValue()))
	}
}


@CompileStatic
final class KChar extends KismetNumber<Integer> {
	char inner

	KChar(char inner) { this.inner = inner }

	NumberType getType() { NumberType.Char }

	Integer inner() { Integer.valueOf((int) inner) }

	void set(Number value) { inner = (char) value.intValue() }

	KChar plus(KismetNumber obj) {
		new KChar((char) (((int) inner) + obj.intValue()).intValue())
	}

	KChar minus(KismetNumber obj) {
		new KChar((char) (((int) inner) - obj.intValue()).intValue())
	}

	KChar multiply(KismetNumber obj) {
		new KChar((char) (((int) inner) * obj.intValue()).intValue())
	}

	KChar div(KismetNumber obj) {
		new KChar((char) (((int) inner) / obj.intValue()).intValue())
	}

	KChar intdiv(KismetNumber obj) {
		new KChar((char) ((int) inner).intdiv(obj.intValue()).intValue())
	}

	KChar mod(KismetNumber obj) {
		new KChar((char) (((int) inner) % obj.intValue()).intValue())
	}

	KChar unaryPlus() { new KChar(inner) }

	KChar unaryMinus() { new KChar(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KChar leftShift(KismetNumber obj) {
		new KChar((char) (((int) inner) << obj.intValue()).intValue())
	}

	KChar rightShift(KismetNumber obj) {
		new KChar((char) (((int) inner) >> obj.intValue()).intValue())
	}

	KChar rightShiftUnsigned(KismetNumber obj) {
		new KChar((char) (((int) inner) >>> obj.intValue()).intValue())
	}

	KChar and(KismetNumber obj) {
		new KChar((char) (((int) inner) & obj.intValue()).intValue())
	}

	KChar or(KismetNumber obj) {
		new KChar((char) (((int) inner) | obj.intValue()).intValue())
	}

	KChar xor(KismetNumber obj) {
		new KChar((char) (((int) inner) ^ obj.intValue()).intValue())
	}
}


@CompileStatic
final class KInt8 extends KismetNumber<Byte> {
	byte inner

	KInt8(byte inner) { this.inner = inner }

	NumberType getType() { NumberType.Int8 }

	Byte inner() { inner }

	void set(Number value) { inner = value.byteValue() }

	KInt8 plus(KismetNumber obj) {
		new KInt8((byte) (inner + obj.byteValue()))
	}

	KInt8 minus(KismetNumber obj) {
		new KInt8((byte) (inner - obj.byteValue()))
	}

	KInt8 multiply(KismetNumber obj) {
		new KInt8((byte) (inner * obj.byteValue()))
	}

	KInt8 div(KismetNumber obj) {
		new KInt8((byte) (inner / obj.byteValue()))
	}

	KInt8 intdiv(KismetNumber obj) {
		new KInt8(inner.intdiv(obj.byteValue()).byteValue())
	}

	KInt8 mod(KismetNumber obj) {
		new KInt8((byte) (inner % obj.byteValue()))
	}

	KInt8 unaryPlus() { new KInt8(inner) }

	KInt8 unaryMinus() { new KInt8(-inner) }

	int compareTo(KismetNumber obj) { inner.compareTo(obj.inner()) }

	KInt8 leftShift(KismetNumber obj) {
		new KInt8((byte) (inner << obj.byteValue()))
	}

	KInt8 rightShift(KismetNumber obj) {
		new KInt8((byte) (inner >> obj.byteValue()))
	}

	KInt8 rightShiftUnsigned(KismetNumber obj) {
		new KInt8((byte) (inner >>> obj.byteValue()))
	}

	KInt8 and(KismetNumber obj) {
		new KInt8((byte) (inner & obj.byteValue()))
	}

	KInt8 or(KismetNumber obj) {
		new KInt8((byte) (inner | obj.byteValue()))
	}

	KInt8 xor(KismetNumber obj) {
		new KInt8((byte) (inner ^ obj.byteValue()))
	}
}