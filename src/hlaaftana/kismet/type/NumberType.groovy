package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.*

@CompileStatic
enum NumberType implements WeakableType {
	Number {
		KNonPrimitiveNum instantiate(Number num) {
			new KNonPrimitiveNum(num)
		}
	}, Int8 {
		KInt8 instantiate(Number num) {
			new KInt8(num.byteValue())
		}
	}, Int16 {
		KInt16 instantiate(Number num) {
			new KInt16(num.shortValue())
		}
	}, Int32 {
		KInt32 instantiate(Number num) {
			new KInt32(num.intValue())
		}
	}, Int64 {
		KInt64 instantiate(Number num) {
			new KInt64(num.longValue())
		}
	}, Int {
		KInt instantiate(Number num) {
			new KInt(KInt.toBigInt(num.longValue()))
		}
	}, Float32 {
		KFloat32 instantiate(Number num) {
			new KFloat32(num.floatValue())
		}
	}, Float64 {
		KFloat64 instantiate(Number num) {
			new KFloat64(num.doubleValue())
		}
	}, Float {
		KFloat instantiate(Number num) {
			new KFloat(KFloat.toBigDec(num))
		}
	}, Char {
		KChar instantiate(Number num) {
			new KChar((char) num.intValue())
		}
	}, Rune {
		KRune instantiate(Number num) {
			new KRune(num.intValue())
		}
	}

	static NumberType from(Number val) {
		if (val instanceof BigInteger) Int
		else if (val instanceof BigDecimal) Float
		else if (val instanceof Integer) Int32
		else if (val instanceof Double) Float64
		else if (val instanceof Float) Float32
		else if (val instanceof Long) Int64
		else if (val instanceof Short) Int16
		else if (val instanceof Byte) Int8
		else Number
	}

	Type inner() { this }

	TypeRelation relation(Type other) {
		def rel = weakRelation(other)
		if (!rel.none) return rel
		other instanceof WeakableType ? ~other.weakRelation(this) : rel
	}

	TypeRelation weakRelation(Type other) {
		if (other instanceof NumberType && !(character ^ other.character)) {
			final k = ordinal() - other.ordinal()
			TypeRelation.some(k)
		} else TypeRelation.none()
	}

	boolean losesAgainst(Type other) {
		final nt = (NumberType) other
		ordinal() >= nt.ordinal()
	}

	/// temporary until i get overloads
	abstract KismetNumber instantiate(Number num)

	boolean isCharacter() { ordinal() > Float.ordinal() }
	boolean isInteger() { ordinal() > Number.ordinal() && ordinal() < Float32.ordinal() }
	boolean isFloat() { ordinal() > Int.ordinal() && ordinal() < Char.ordinal() }
}
