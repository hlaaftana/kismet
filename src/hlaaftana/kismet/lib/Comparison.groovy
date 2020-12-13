package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.call.CallExpression
import hlaaftana.kismet.call.Expression
import hlaaftana.kismet.call.NameExpression
import hlaaftana.kismet.call.Template
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.Type

import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.lib.Functions.funcc

@CompileStatic
class Comparison extends LibraryModule {
    TypedContext typed = new TypedContext("comparison")
    Context defaultContext = new Context()

    Comparison() {
        define 'is?', func(Logic.BOOLEAN_TYPE, Type.ANY, Type.ANY), funcc { ... args -> args.inject { a, b -> a == b } }
        negated 'is?', 'is_not?'
        alias 'is?', '=='
        negated '==', '!='
        define 'same?',  funcc { ... a -> a[0].is(a[1]) }
        negated 'same?', 'not_same?'
        define 'empty?',  funcc { ... a -> a[0].invokeMethod('isEmpty', null) }
        define 'hash',  funcc { ... a -> a[0].hashCode() }
        define 'max',  funcc { ... args -> args.max() }
        define 'min',  funcc { ... args -> args.min() }
        define 'cmp', func(NumberType.Int32, Type.ANY, Type.ANY), funcc { ...args -> args[0].invokeMethod('compareTo', args[1]) }
        define '<', func(Logic.BOOLEAN_TYPE, Type.ANY, Type.ANY), funcc { ...args -> args[0].invokeMethod('compareTo', args[1]) as int < 0 }
        define '>', func(Logic.BOOLEAN_TYPE, Type.ANY, Type.ANY), funcc { ...args -> args[0].invokeMethod('compareTo', args[1]) as int > 0 }
        define '<=', func(Logic.BOOLEAN_TYPE, Type.ANY, Type.ANY), funcc { ...args -> args[0].invokeMethod('compareTo', args[1]) as int <= 0 }
        define '>=', func(Logic.BOOLEAN_TYPE, Type.ANY, Type.ANY), funcc { ...args -> args[0].invokeMethod('compareTo', args[1]) as int >= 0 }
        define 'less?', Functions.TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                new CallExpression(new NameExpression('<'), args[0], args[1])
            }
        }
        define 'greater?', Functions.TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                new CallExpression(new NameExpression('>'), args[0], args[1])
            }
        }
        define 'less_equal?', Functions.TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                new CallExpression(new NameExpression('<='), args[0], args[1])
            }
        }
        define 'greater_equal?', Functions.TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                new CallExpression(new NameExpression('>='), args[0], args[1])
            }
        }
    }
}
