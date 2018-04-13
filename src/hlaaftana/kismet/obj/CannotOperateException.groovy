package hlaaftana.kismet.obj

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

@CompileStatic
@InheritConstructors
class CannotOperateException extends Exception {
	CannotOperateException(String op, String on) {
		super("Cannot $op on $on")
	}
}
