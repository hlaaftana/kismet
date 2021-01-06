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
    List<Module> dependencies
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
            def mod = new KismetModule<File>(space, f, f.name.substring(0, f.name.indexOf((int) ((char) '.'))), f.text)
            mod.dependencies = new ArrayList<>(space.defaultDependencies)
            space.modules.put(f, mod)
        }
        space.modules.get(f)
    }

    Expression parse(Parser parser = new Parser(memory: Kismet.DEFAULT_CONTEXT)) {
        if (null == expression) {
            expression = parser.parse(source)
        }
        expression
    }

    TypedContext typeContext() {
        if (null == typedContext) {
            def ds = new ArrayList<TypedContext>(dependencies.size())
            for (d in dependencies) {
                ds.add(d.typeContext())
            }
            typedContext = new TypedContext(name)
            typedContext.module = this
            typedContext.heritage = new ArrayList<>(ds)
        }
        typedContext
    }

    TypedExpression type() {
        if (null == typed) {
            typed = parse().type(typeContext())
        }
        typed
    }

    Memory run() {
        if (null == memory) {
            def herit = new Memory[dependencies.size()]
            for (int i = 0; i < herit.length; ++i) {
                herit[i] = dependencies.get(i).run()
            }
            def instr = type().instruction
            memory = new RuntimeMemory(typedContext)
            System.arraycopy(herit, 0, ((RuntimeMemory) memory).heritage, 0, herit.length)
            instr.evaluate(memory)
        }
        memory
    }
}
