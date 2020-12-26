package hlaaftana.kismet.lib

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import static hlaaftana.kismet.lib.Functions.funcc

@CompileStatic
class Json extends NativeModule {
    Json() {
        super("json")
        define 'new_json_parser', funcc { ... args -> new JsonSlurper() }
        define 'parse_json', funcc { ... args ->
            String text = args.length > 1 ? args[1].toString() : args[0].toString()
            JsonSlurper sl = args.length > 1 ? args[0] as JsonSlurper : new JsonSlurper()
            sl.parseText(text)
        }
        define 'to_json', funcc { ... args -> ((Object) JsonOutput).invokeMethod('toJson', [args[0]] as Object[]) }
        define 'pretty_print_json', funcc { ... args -> JsonOutput.prettyPrint(args[0].toString()) }
    }
}
