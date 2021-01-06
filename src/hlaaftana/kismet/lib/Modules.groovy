package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.scope.KismetModule
import hlaaftana.kismet.scope.Module
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.RuntimeMemory

import static hlaaftana.kismet.lib.Functions.TYPED_TEMPLATE_TYPE
import static hlaaftana.kismet.lib.Functions.TYPE_CHECKER_TYPE

@CompileStatic
class Modules extends NativeModule {
    Modules() {
        super("modules")
        define 'current_module_path', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                new TypedStringExpression(context.module instanceof KismetModule &&
                    ((KismetModule) context.module).handle instanceof File ?
                    ((File) ((KismetModule) context.module).handle).path :
                    null)
            }
        }
        define 'current_module_name', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                new TypedStringExpression(context.module instanceof KismetModule ?
                    ((KismetModule) context.module).name :
                    null)
            }
        }
        define 'current_module_source', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                new TypedStringExpression(context.module instanceof KismetModule ?
                    ((KismetModule) context.module).source :
                    null)
            }
        }
        define 'module_by_name', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                new TypedStringExpression(context.module instanceof KismetModule ?
                    ((KismetModule) context.module).source :
                    null)
            }
        }
        define 'import', TYPED_TEMPLATE_TYPE.generic(new TupleType().withVarargs(Strings.STRING_TYPE), Type.NONE), new TypedTemplate() {
            @Override
            TypedExpression transform(TypedContext context, TypedExpression... args) {
                Module[] mods = new Module[args.length]
                int originalHeritageSize = context.heritage.size()
                for (int i = 0; i < args.length; ++i) {
                    def a = args[i]
                    def file = new File(a.instruction.evaluate(context).toString())
                    def mod = KismetModule.from(((KismetModule<File>) context.module).space, file)
                    mod.type()
                    context.heritage.add(mod.typedContext)
                    ((KismetModule<File>) context.module).dependencies.add(mod)
                    mods[i] = mod
                }
                new BasicTypedExpression(Type.NONE, new Instruction() {
                    IKismetObject evaluate(Memory m) {
                        for (int i = 0; i < mods.size(); ++i) {
                            def mod = mods[i]
                            ((RuntimeMemory) m).heritage[originalHeritageSize + i] = mod.run()
                        }
                        Kismet.NULL
                    }
                })
            }
        }
    }
}
