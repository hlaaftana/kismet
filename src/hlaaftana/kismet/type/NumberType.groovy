package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.*

@CompileStatic
enum NumberType implements Type {
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

	TypeRelation relation(Type other) {
		if (other instanceof NumberType && !(character ^ other.character)) {
			final k = ordinal() - other.ordinal()
			TypeRelation.some(k)
		} else TypeRelation.none()
	}

	boolean losesAgainst(Type other) { ordinal() >= ((NumberType) other).ordinal() }

	/// temporary until i get overloads
	abstract KismetNumber instantiate(Number num)

	boolean isCharacter() { ordinal() > Float.ordinal() }
	boolean isInteger() { ordinal() < Float32.ordinal() }
	boolean isFloat() { ordinal() > Int.ordinal() }
}
