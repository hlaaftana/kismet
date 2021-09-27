package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.UndefinedVariableException
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.Address
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.NumberType
import hlaaftana.kismet.type.SingleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.type.TypeBound
import hlaaftana.kismet.vm.*

import static hlaaftana.kismet.call.ExprBuilder.*
import static hlaaftana.kismet.lib.Functions.*
import static hlaaftana.kismet.lib.Logic.BOOLEAN_TYPE

@CompileStatic
class Reflection extends NativeModule {
    static final SingleType INSTRUCTION_TYPE = new SingleType('Instruction') {
        boolean check(IKismetObject obj) { obj instanceof Instruction }
        boolean checkGenerics(IKismetObject obj, Type... args) { true }
    },
                            MEMORY_TYPE = new SingleType('Memory') {
                                boolean check(IKismetObject obj) { obj instanceof Memory }
                                boolean checkGenerics(IKismetObject obj, Type... args) { true }
                            },
                            EXPRESSION_TYPE = new SingleType('Expression')  {
                                boolean check(IKismetObject obj) { obj instanceof Expression }
                                boolean checkGenerics(IKismetObject obj, Type... args) { true }
                            }

    Reflection() {
        super("reflection")
        define EXPRESSION_TYPE
        define INSTRUCTION_TYPE
        define MEMORY_TYPE
        define 'name_expr', func(EXPRESSION_TYPE, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                name(((KismetString) args[0]).inner())
            }
        }
        define 'name_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof ConstantExpression)
            }
        }
        define 'expr_to_name', func(Strings.STRING_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                def i = args[0] instanceof Expression ? Syntax.toAtom((Expression) args[0]) : (String) null
                null == i ? Kismet.NULL : new KismetString(i)
            }
        }
        define 'constant_expr', func(EXPRESSION_TYPE, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                new StaticExpression(args[0])
            }
        }
        define 'constant_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof ConstantExpression)
            }
        }
        define 'number_expr', func(EXPRESSION_TYPE, NumberType.Number), new Function() {
            IKismetObject call(IKismetObject... args) {
                new NumberExpression((KismetNumber) args[0])
            }
        }
        define 'number_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof NumberExpression)
            }
        }
        define 'string_expr', func(EXPRESSION_TYPE, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                string(((KismetString) args[0]).inner())
            }
        }
        define 'string_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof StringExpression)
            }
        }
        define 'call_expr', func(EXPRESSION_TYPE, CollectionsIterators.LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
            IKismetObject call(IKismetObject... args) {
                call((List<Expression>) args[0].inner())
            }
        }
        define 'call_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof CallExpression)
            }
        }
        define 'block_expr', func(EXPRESSION_TYPE, CollectionsIterators.LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
            IKismetObject call(IKismetObject... args) {
                block((List<Expression>) args[0].inner())
            }
        }
        define 'block_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof BlockExpression)
            }
        }
        define 'dive_expr', func(EXPRESSION_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                new DiveExpression((Expression) args[0])
            }
        }
        define 'dive_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof DiveExpression)
            }
        }
        define 'colon_expr', func(EXPRESSION_TYPE, EXPRESSION_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                new ColonExpression((Expression) args[0], (Expression) args[1])
            }
        }
        define 'colon_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof ColonExpression)
            }
        }
        define 'property_expr', func(EXPRESSION_TYPE, EXPRESSION_TYPE, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                property((Expression) args[0], ((KismetString) args[1]).inner())
            }
        }
        define 'property_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof PropertyExpression)
            }
        }
        define 'subscript_expr', func(EXPRESSION_TYPE, EXPRESSION_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                subscript((Expression) args[0], (Expression) args[1])
            }
        }
        define 'subscript_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof SubscriptExpression)
            }
        }
        define 'set_expr', func(EXPRESSION_TYPE, CollectionsIterators.LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
            IKismetObject call(IKismetObject... args) {
                new SetExpression((List<Expression>) args[0].inner())
            }
        }
        define 'set_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof SetExpression)
            }
        }
        define 'list_expr', func(EXPRESSION_TYPE, CollectionsIterators.LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
            IKismetObject call(IKismetObject... args) {
                new ListExpression((List<Expression>) args[0].inner())
            }
        }
        define 'list_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof ListExpression)
            }
        }
        define 'tuple_expr', func(EXPRESSION_TYPE, CollectionsIterators.LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
            IKismetObject call(IKismetObject... args) {
                new TupleExpression((List<Expression>) args[0].inner())
            }
        }
        define 'tuple_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof TupleExpression)
            }
        }
        define 'map_expr', func(EXPRESSION_TYPE, CollectionsIterators.LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
            IKismetObject call(IKismetObject... args) {
                def list = (List<Expression>) args[0].inner()
                for (ex in list)
                    if (ex !instanceof ColonExpression)
                        throw new UnexpectedSyntaxException("map expression arguments must be colon expressions")
                new MapExpression((List<ColonExpression>) args[0].inner())
            }
        }
        define 'map_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                KismetBoolean.from(args[0] instanceof MapExpression)
            }
        }
        define '.members', func(CollectionsIterators.LIST_TYPE.generic(EXPRESSION_TYPE), EXPRESSION_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((Expression) args[0]).members)
            }
        }
        define '.[]', func(EXPRESSION_TYPE, EXPRESSION_TYPE, NumberType.Int32), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((Expression) args[0]).members.get(((KismetNumber) args[1]).intValue())
            }
        }
        define '.[]', func(EXPRESSION_TYPE, EXPRESSION_TYPE, NumberType.Int), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                ((Expression) args[0]).members.get(((KismetNumber) args[1]).intValue())
            }
        }
        define 'expr_type',  funcc { ... args ->
            args[0] instanceof Expression ?
                    (args[0].class.simpleName - 'Expression').uncapitalize() : null
        }
        define 'repr_expr',  funcc { ... args -> ((Expression) args[0]).repr() }
        define 'quote', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                new TypedConstantExpression(EXPRESSION_TYPE, args.length == 1 ? args[0] : block(args.toList()))
            }
        }
        define 'variable', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext tc, Expression... args) {
                new TypedConstantExpression(Type.ANY, new WrapperKismetObject(tc.find(Syntax.toAtom(args[0]))))
            }

            IKismetObject call(Memory c, Expression... args) {
                if (args.length == 0) throw new UnexpectedSyntaxException('No arguments for variable function')
                final first = args[0].evaluate(c)
                if (first.inner() instanceof String) {
                    final name = (String) first.inner()
                    if (args.length == 2) {
                        final a1 = args[1].evaluate(c)
                        c.set(name, a1)
                        a1
                    } else c.get(name)
                } else if (first.inner() instanceof Address && args.length == 2) {
                    final val = args[1].evaluate(c)
                    ((Address) first.inner()).value = val
                    val
                } else throw new UnexpectedSyntaxException("weird argument for variable: " + first)
            }
        }
        define 'variables', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                new TypedConstantExpression(Type.ANY, new WrapperKismetObject(context.variables))
            }

            IKismetObject call(Memory c, Expression... args) {
                new WrapperKismetObject(((Context) c).variables)
            }
        }
        define 'current_context', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                new TypedConstantExpression(Type.ANY, new WrapperKismetObject(context))
            }

            IKismetObject call(Memory c, Expression... args) {
                new WrapperKismetObject(c)
            }
        }
        define 'defined?', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                final type = new TypeBound(args.length > 1 ? (Type) args[1].type(context).instruction.evaluate(context) : Type.ANY)
                if (args[0] instanceof SetExpression) {
                    def names = new HashSet<String>(args[0].size())
                    for (n in args[0].members) {
                        def at = Syntax.toAtom(n)
                        if (null == at) throw new UnexpectedSyntaxException('Unknown symbol ' + n)
                        names.add(at)
                    }
                    new TypedConstantExpression(BOOLEAN_TYPE,
                            KismetBoolean.from(null != context.find(names, type)))
                } else {
                    def at = Syntax.toAtom(args[0])
                    if (null == at) throw new UnexpectedSyntaxException('Unknown symbol ' + args[0])
                    new TypedConstantExpression(BOOLEAN_TYPE,
                            KismetBoolean.from(null != context.find(at, type)))
                }
            }

            IKismetObject call(Memory c, Expression... exprs) {
                try {
                    c.get(resolveName(exprs[0], c, "defined?"))
                    KismetBoolean.TRUE
                } catch (UndefinedVariableException ignored) {
                    KismetBoolean.FALSE
                }
            }
        }
        negated 'defined?', 'undefined?'
        alias 'defined?', 'variable?'
        // TODO: change to submodule
        define 'parse_independent_kismet',  func { IKismetObject... args ->
            def parser = new Parser()
            parser.memory = new Context(Kismet.DEFAULT_CONTEXT)
            def p = parser.parse(args[0].toString())
            def tc = Kismet.PRELUDE.typedContext.child()
            def t = p.type(tc)
            def i = t.instruction
            def mem = new RuntimeMemory([Kismet.PRELUDE.typedContext] as Memory[], tc.size())
            i.evaluate(mem)
        }
    }

    static String resolveName(Expression n, Memory c, String op) {
        String name
        if (n instanceof NameExpression) name = ((NameExpression) n).text
        else if (n instanceof NumberExpression) throw new UnexpectedSyntaxException("Name in $op was a number, not allowed")
        else {
            IKismetObject val = n.evaluate(c)
            if (val.inner() instanceof String) name = val.inner()
            else throw new UnexpectedValueException("Name in $op wasnt a string")
        }
        name
    }
}
