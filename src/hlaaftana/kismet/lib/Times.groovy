package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.call.Instruction
import hlaaftana.kismet.call.Instructor
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.*

import static Functions.*

import java.text.SimpleDateFormat

@CompileStatic
class Times extends NativeModule {
    Times() {
        super("times")
        define 'parse_date_millis_from_format', func(NumberType.Int64, Strings.STRING_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                new KInt64(new SimpleDateFormat(args[1].toString()).parse(args[0].toString()).time)
            }
        }
        define 'sleep', funcc { ... args -> sleep args[0] as long }
        define 'average_time_nanos', instr(NumberType.Float, NumberType.Number, Type.ANY), new Instructor() {
            IKismetObject call(Memory c, Instruction... args) {
                int iterations = args[0].evaluate(c).inner() as int
                long sum = 0
                for (int i = 0; i < iterations; ++i) {
                    long a = System.nanoTime()
                    args[1].evaluate(c)
                    long b = System.nanoTime()
                    sum += b - a
                }
                new KFloat(sum / iterations)
            }
        }
        define 'average_time_millis', instr(NumberType.Float, NumberType.Number, Type.ANY), new Instructor() {
            IKismetObject call(Memory c, Instruction... args) {
                int iterations = args[0].evaluate(c).inner() as int
                long sum = 0
                for (int i = 0; i < iterations; ++i) {
                    long a = System.currentTimeMillis()
                    args[1].evaluate(c)
                    long b = System.currentTimeMillis()
                    sum += b - a
                }
                new KFloat(sum / iterations)
            }
        }
        define 'average_time_seconds', instr(NumberType.Float, NumberType.Number, Type.ANY), new Instructor() {
            IKismetObject call(Memory c, Instruction... args) {
                int iterations = args[0].evaluate(c).inner() as int
                long sum = 0
                for (int i = 0; i < iterations; ++i) {
                    long a = System.currentTimeSeconds()
                    args[1].evaluate(c)
                    long b = System.currentTimeSeconds()
                    sum += b - a
                }
                new KFloat(sum / iterations)
            }
        }
        define 'list_time_nanos', instr(new GenericType(CollectionsIterators.LIST_TYPE, NumberType.Int64),
                NumberType.Number, Type.ANY), new Instructor() {
            @Override
            IKismetObject call(Memory c, Instruction... args) {
                int iterations = ((KismetNumber) args[0].evaluate(c)).intValue()
                def times = new ArrayList<KInt64>(iterations)
                for (int i = 0; i < iterations; ++i) {
                    long a = System.nanoTime()
                    args[1].evaluate(c)
                    long b = System.nanoTime()
                    times.add(new KInt64(b - a))
                }
                new WrapperKismetObject(times)
            }
        }
        define 'list_time_millis', instr(new GenericType(CollectionsIterators.LIST_TYPE, NumberType.Int64),
                NumberType.Number, Type.ANY), new Instructor() {
            @Override
            IKismetObject call(Memory c, Instruction... args) {
                int iterations = args[0].evaluate(c).inner() as int
                def times = new ArrayList<KInt64>(iterations)
                for (int i = 0; i < iterations; ++i) {
                    long a = System.currentTimeMillis()
                    args[1].evaluate(c)
                    long b = System.currentTimeMillis()
                    times.add(new KInt64(b - a))
                }
                new WrapperKismetObject(times)
            }
        }
        define 'list_time_seconds', instr(new GenericType(CollectionsIterators.LIST_TYPE, NumberType.Int64),
                NumberType.Number, Type.ANY), new Instructor() {
            @Override
            IKismetObject call(Memory c, Instruction... args) {
                int iterations = args[0].evaluate(c).inner() as int
                def times = new ArrayList<KInt64>(iterations)
                for (int i = 0; i < iterations; ++i) {
                    long a = System.currentTimeSeconds()
                    args[1].evaluate(c)
                    long b = System.currentTimeSeconds()
                    times.add(new KInt64(b - a))
                }
                new WrapperKismetObject(times)
            }
        }
        define 'now_nanos', func(NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt64(System.nanoTime())
            }
        }
        define 'now_millis', func(NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt64(System.currentTimeMillis())
            }
        }
        define 'now_seconds', func(NumberType.Int64), new Function() {
            IKismetObject call(IKismetObject... args) {
                new KInt64(System.currentTimeSeconds())
            }
        }
    }
}
