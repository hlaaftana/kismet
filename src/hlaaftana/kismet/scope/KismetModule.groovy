package hlaaftana.kismet.scope

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.Expression
import hlaaftana.kismet.call.TypedExpression
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.RuntimeMemory

@CompileStatic
class KismetModule<Handle> extends Module {
    String name, source
    Expression expression
    TypedContext typedContext
    TypedExpression typed
    Context defaultContext
    Memory memory
    KismetModuleSpace<Handle> space
    Handle handle

    KismetModule(KismetModuleSpace<Handle> space, Handle handle, String name, String source) {
        this.space = space
        this.handle = handle
        this.name = name
        this.source = source
    }

    static KismetModule<File> from(KismetModuleSpace<File> space, File f) {
        if (!space.modules.containsKey(f)) {
            space.modules.put(f, new KismetModule<File>(space, f, f.name.substring(0, f.name.indexOf((int) ((char) '.'))), f.text))
        }
        space.modules.get(f)
    }

    Expression parse(Parser parser = new Parser(memory: Kismet.DEFAULT_CONTEXT)) {
        if (null == expression) {
            expression = parser.parse(source)
        }
        expression
    }

    TypedExpression type(List<TypedContext> defaultHeritage = [Kismet.PRELUDE.typedContext]) {
        if (null == typedContext) {
            typedContext = new TypedContext(name)
            typedContext.module = this
            typedContext.heritage = defaultHeritage
        }
        if (null == typed) {
            typed = parse().type(typedContext)
        }
        typed
    }

    Memory run(Memory[] heritage = [Kismet.PRELUDE.typedContext]) {
        if (null == memory) {
            def instr = type().instruction
            memory = new RuntimeMemory(typedContext)
            instr.evaluate(memory)
        }
        memory
    }
}
