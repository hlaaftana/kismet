package hlaaftana.kismet

import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.KInt
import hlaaftana.kismet.vm.KismetTuple

@CompileStatic
class TupleTest {
    static void main(args) {
        def x = new HashMap<KismetTuple, String>()
        x.put(new KismetTuple(new KInt(1), new KInt(2)), "a")
        x.put(new KismetTuple(new KInt(1), new KInt(2)), "b")
        println x
    }
}
