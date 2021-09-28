package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.*

@CompileStatic
enum NumberType implements WeakableType, ConcreteType {
	Number {
		KNonPrimitiveNum instantiate(Number num) {
			new KNonPrimitiveNum(num)
		}

		boolean check(IKismetObject obj) { obj instanceof KismetNumber }
	}, Int8 {
		KInt8 instantiate(Number num) {
			new KInt8(num.byteValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KInt8 }
	}, Int16 {
		KInt16 instantiate(Number num) {
			new KInt16(num.shortValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KInt16 }
	}, Int32 {
		KInt32 instantiate(Number num) {
			new KInt32(num.intValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KInt32 }
	}, Int64 {
		KInt64 instantiate(Number num) {
			new KInt64(num.longValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KInt64 }
	}, Int {
		KInt instantiate(Number num) {
			new KInt(KInt.toBigInt(num))
		}

		boolean check(IKismetObject obj) { obj instanceof KInt }
	}, Float32 {
		KFloat32 instantiate(Number num) {
			new KFloat32(num.floatValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KFloat32 }
	}, Float64 {
		KFloat64 instantiate(Number num) {
			new KFloat64(num.doubleValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KFloat64 }
	}, Float {
		KFloat instantiate(Number num) {
			new KFloat(KFloat.toBigDec(num))
		}

		boolean check(IKismetObject obj) { obj instanceof KFloat }
	}, Char {
		KChar instantiate(Number num) {
			new KChar((char) num.intValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KChar }
	}, Rune {
		KRune instantiate(Number num) {
			new KRune(num.intValue())
		}

		boolean check(IKismetObject obj) { obj instanceof KRune }
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
		else if (val instanceof KismetNumber) val.type
		else Number
	}

	Type inner() { this }

	TypeRelation relation(Type other) {
		def rel = weakRelation(other)
		if (!rel.none) return rel
		other instanceof WeakableType ? ~other.weakRelation(this) : rel
	}

	TypeRelation weakRelation(Type other) {
		/*if (other instanceof NumberType && !(character ^ other.character)) {
			final k = ordinal() - other.ordinal()
			TypeRelation.some(k)
		} else TypeRelation.none()*/
		if (other instanceof NumberType) {
			if (this == other) TypeRelation.equal()
			else if (this == Number) TypeRelation.supertype(other.ordinal())
			else if (other == Number) TypeRelation.subtype(ordinal())
			else TypeRelation.none()
		} else TypeRelation.none()
	}

	/// temporary until i get overloads
	abstract KismetNumber instantiate(Number num)

	boolean isCharacter() { ordinal() > Float.ordinal() }
	boolean isInteger() { ordinal() > Number.ordinal() && ordinal() < Float32.ordinal() }
	boolean isFloat() { ordinal() > Int.ordinal() && ordinal() < Char.ordinal() }
}
