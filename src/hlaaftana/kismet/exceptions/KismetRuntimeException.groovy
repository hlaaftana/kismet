package hlaaftana.kismet.exceptions
import groovy.transform.CompileStatic
import hlaaftana.kismet.vm.IKismetObject

@CompileStatic
class KismetRuntimeException extends Exception {
    IKismetObject obj
    KismetRuntimeException(IKismetObject obj) {
        this.obj = obj
    }
    KismetRuntimeException() {}

    String getMessage() {
        obj?.toString()
    }
}
