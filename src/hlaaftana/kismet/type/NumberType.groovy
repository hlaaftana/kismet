package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.*

@CompileStatic
enum NumberType implements Type {
	Int8 {
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
			new KInt(num instanceof BigInteger ?
					num : BigInteger.valueOf(num.longValue()))
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
			new KFloat(num instanceof BigDecimal ?
					num : BigDecimal.valueOf(
						num instanceof Float ||
						num instanceof Double ?
								num.doubleValue() :
								num.longValue()))
		}
	}

	TypeRelation relation(Type other) {
		if (other instanceof NumberType) {
			final k = ordinal() - other.ordinal()
			TypeRelation.some(k)
		} else TypeRelation.none()
	}

	/// temporary until i get overloads
	abstract KismetNumber instantiate(Number num)

	boolean isInteger() { ordinal() < Float32.ordinal() }
	boolean isFloat() { ordinal() > Int.ordinal() }
}
