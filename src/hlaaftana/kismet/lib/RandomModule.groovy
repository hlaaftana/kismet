package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KismetBoolean
import hlaaftana.kismet.vm.KismetNumber

import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.lib.Functions.funcc

@CompileStatic
class RandomModule extends NativeModule {
    RandomModule() {
        super("random")
        define 'new_rng', funcc { ... args -> args.length > 0 ? new Random(args[0] as long) : new Random() }
        define 'random_int8_list_from_reference', funcc { ... args ->
            byte[] bytes = args[1] as byte[]
            (args[0] as Random).nextBytes(bytes)
            bytes as List<Byte>
        }
        define 'random_int32', funcc { ... args ->
            if (args.length == 0) return (args[0] as Random).nextInt()
            int max = (args.length > 2 ? args[2] as int : args[1] as int) + 1
            int min = args.length > 2 ? args[1] as int : 0
            (args[0] as Random).nextInt(max) + min
        }
        define 'random_int64_of_all', funcc { ... args -> (args[0] as Random).nextLong() }
        define 'random_float32_between_0_and_1', funcc { ... args -> (args[0] as Random).nextFloat() }
        define 'random_float64_between_0_and_1', funcc { ... args -> (args[0] as Random).nextDouble() }
        define 'random_bool', funcc { ... args -> (args[0] as Random).nextBoolean() }
        define 'next_gaussian', funcc { ... args -> (args[0] as Random).nextGaussian() }
        define 'random_int', funcc { ... args ->
            BigInteger lower = args.length > 2 ? args[1] as BigInteger : 0g
            BigInteger higher = args.length > 2 ? args[2] as BigInteger : args[1] as BigInteger
            double x = (args[0] as Random).nextDouble()
            lower + (((higher - lower) * (x as BigDecimal)) as BigInteger)
        }
        define 'random_float', funcc { ... args ->
            BigDecimal lower = args.length > 2 ? args[1] as BigDecimal : 0g
            BigDecimal higher = args.length > 2 ? args[2] as BigDecimal : args[1] as BigDecimal
            double x = (args[0] as Random).nextDouble()
            lower + (higher - lower) * (x as BigDecimal)
        }
        define 'probability', func(Logic.BOOLEAN_TYPE, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... a) {
                Number x = (KismetNumber) a[0]
                KismetBoolean.from(new Random().nextDouble() < x)
            }
        }
    }
}
