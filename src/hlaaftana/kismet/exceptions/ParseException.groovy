package hlaaftana.kismet.exceptions

import groovy.transform.CompileStatic

@CompileStatic
class ParseException extends KismetException {
	int ln
	int cl

	ParseException(Throwable cause, int ln, int cl) {
		super("At line $ln column $cl: $cause", cause)
		this.ln = ln
		this.cl = cl
	}
}