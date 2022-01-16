package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.UnexpectedSyntaxException
import hlaaftana.kismet.exceptions.UnexpectedTypeException
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.scope.AssignmentType
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.GenericType
import hlaaftana.kismet.type.TupleType
import hlaaftana.kismet.type.Type
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.Memory
import hlaaftana.kismet.vm.WrapperKismetObject

import static hlaaftana.kismet.lib.Logic.BOOLEAN_TYPE
import static Functions.*
import static hlaaftana.kismet.call.ExprBuilder.*

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Syntax extends NativeModule {
    static String toAtom(Expression expression) {
        if (expression instanceof StringExpression) {
            return ((StringExpression) expression).value
        } else if (expression instanceof NameExpression) {
            return ((NameExpression) expression).text
        }
        null
    }

    static CallExpression pipeForwardExpr(Expression base, Collection<Expression> args) {
        if (args.empty) throw new UnexpectedSyntaxException('no |> for epic!')
        for (exp in args) {
            if (exp instanceof CallExpression) {
                Collection<Expression> exprs = new ArrayList<>()
                exprs.add(((CallExpression) exp).callValue)
                exprs.add(base)
                exprs.addAll(((CallExpression) exp).arguments)
                def ex = call(exprs)
                base = ex
            } else if (exp instanceof BlockExpression) {
                base = pipeForwardExpr(base, ((BlockExpression) exp).members)
            } else if (exp instanceof NameExpression) {
                base = call([exp, base])
            } else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in |>')
        }
        (CallExpression) base
    }

    static CallExpression pipeBackwardExpr(Expression base, Collection<Expression> args) {
        if (args.empty) throw new UnexpectedSyntaxException('no <| for epic!')
        for (exp in args) {
            if (exp instanceof CallExpression) {
                Collection<Expression> exprs = new ArrayList<>()
                exprs.add(((CallExpression) exp).callValue)
                exprs.addAll(((CallExpression) exp).arguments)
                exprs.add(base)
                def ex = call(exprs)
                base = ex
            } else if (exp instanceof BlockExpression) {
                base = pipeBackwardExpr(base, ((BlockExpression) exp).members)
            } else if (exp instanceof NameExpression) {
                base = call([exp, base])
            } else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in |>')
        }
        (CallExpression) base
    }

    static Expression infixLTR(Expression expr) {
        if (expr instanceof CallExpression) infixLTR(((CallExpression) expr).members)
        else if (expr instanceof BlockExpression) {
            final mems = ((BlockExpression) expr).members
            def result = new ArrayList<Expression>(mems.size())
            for (x in ((BlockExpression) expr).members) result.add(infixLTR(x))
            block(result)
        } else expr
    }

    private static final NameExpression INFIX_CALLS_LTR_PATH = name('infix')

    static Expression infixLTR(Collection<Expression> args) {
        if (args.empty) return NoExpression.INSTANCE
        else if (args.size() == 1) return infixLTR(args[0])
        else if (args.size() == 2) {
            if (INFIX_CALLS_LTR_PATH == args[0]) return args[1]
            final val = infixLTR(args[0])
            def result = call((Expression[]) null)
            result.callValue = val
            result.arguments = Arrays.asList(val, infixLTR(args[1]))
            result
        } else if (args.size() % 2 == 0)
            throw new UnexpectedSyntaxException('Even number of arguments for LTR infix function calls')
        def calls = new ArrayList<List<Expression>>()
        for (int i = 3; i < args.size(); ++i) {
            Expression ex = infixLTR args[i]
            def last = calls.last()
            if (i % 2 == 0) last.add(ex)
            else if (ex != last[0]) calls.add([ex])
        }
        CallExpression result = call(
                infixLTR(args[1]),
                infixLTR(args[0]),
                infixLTR(args[2]))
        for (b in calls) {
            def exprs = new ArrayList<Expression>(b.size() + 1)
            int i = 0
            exprs.add(b.get(i++))
            exprs.add(result)
            while (i < b.size()) exprs.add(b.get(i++))
            result = call(exprs)
        }
        result
    }

    static class AssignTemplate extends Template {
        AssignmentType type

        AssignTemplate(AssignmentType type) {
            this.type = type
        }

        Expression transform(Parser parser, Expression... args) {
            final size = args.length
            def last = args[size - 1]
            for (int i = size - 2; i >= 0; --i) {
                final name = args[i]
                final atom = toAtom(name)
                if (null != atom)
                    last = var(type, atom, last)
                else if (name instanceof CallExpression)
                    last = new FunctionDefineExpression(args)
                else if (name instanceof PathStepExpression)
                    last = new PathStepSetExpression(name, last)
                else throw new UnexpectedSyntaxException("Cannot perform assignment $type $name, value expression is $last")
            }
            if (last instanceof NameExpression)
                last = var(AssignmentType.SET, ((NameExpression) last).text, NoExpression.INSTANCE)
            last
        }
    }

    Syntax() {
        super("syntax")
        /*define '.property', func(Type.ANY, Type.ANY, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(args[0].getAt(((CharSequence) args[1]).toString()))
            }
        }
        define '.property', func(Type.ANY, CollectionsIterators.MAP_TYPE, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((Map) args[0].inner()).get(((CharSequence) args[1]).toString()))
            }
        }
        define '.[]', func(Type.ANY, Type.ANY, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(args[0].invokeMethod('getAt', [args[1].inner()] as Object[]))
            }
        }
        define '.[]=', func(Type.ANY, Type.ANY, Strings.STRING_TYPE), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(args[0].invokeMethod('putAt', [args[1].inner(), args[2].inner()] as Object[]))
            }
        }*/
        define ':::=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.CHANGE)
        define '::=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.SET)
        define ':=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.DEFINE)
        define '=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.ASSIGN)
        define 'shadow', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.SHADOW)
        alias '=', 'assign'
        alias ':=', 'define'
        alias '::=', 'set_to'
        alias ':::=', 'change'
        define 'declare', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                def type = Type.ANY
                if (args.length > 1) {
                    def ex = args[args.length - 1]
                    def t = ex.type(context).instruction.evaluate(context)
                    if (t !instanceof Type)
                        throw new UnexpectedTypeException('value of ' + ex + ' not a type, instead got ' + t)
                    type = (Type) t
                }
                for (final name : args) {
                    if (name !instanceof NameExpression)
                        throw new UnexpectedSyntaxException('decl must take name argument')
                    context.addVariable(((NameExpression) name).text, type)
                }
                TypedNoExpression.INSTANCE
            }
        }
        define '+=', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                call([name('='), args[0],
                      call([name('+'), args[0], args[1]])])
            }
        }
        define 'def', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                if (args.length == 0) throw new UnexpectedSyntaxException('Cannot def without any arguments')
                if (args[0] instanceof NameExpression) {
                    var(AssignmentType.DEFINE, ((NameExpression) args[0]).text, args[1])
                } else {
                    new FunctionDefineExpression(args)
                }
            }
        }
        define 'instructor', new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                def c = context.child()
                c.label = "anonymous instructor"
                c.addVariable('instructions', CollectionsIterators.LIST_TYPE.generic(Reflection.INSTRUCTION_TYPE))
                def typ = args[0].type(c)
                new TypedConstantExpression(new GenericType(INSTRUCTOR_TYPE, TupleType.BASE, typ.type), new Instructor() {
                    IKismetObject call(Memory m, Instruction... a) {
                        m.set(0, new WrapperKismetObject(Arrays.asList(a)))
                        typ.instruction.evaluate(m)
                    }
                })
            }
        }
        define 'fn', new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                new FunctionExpression(false, args)
            }
        }
        define 'defn', new Template() {
            boolean isOptimized() { true }

            @CompileStatic
            Expression transform(Parser parser, Expression... a) {
                String name
                Arguments args

                def f = a[0]
                if (f instanceof NameExpression) {
                    name = ((NameExpression) f).text
                    args = new Arguments(null)
                } else if (f instanceof CallExpression && f[0] instanceof NameExpression) {
                    name = ((NameExpression) ((CallExpression) f).callValue).text
                    args = new Arguments(((CallExpression) f).arguments)
                } else {
                    throw new UnexpectedSyntaxException("Can't define function with declaration " + f)
                }
                def exprs = new ArrayList<Expression>(a.length - 1)
                for (int i = 1; i < a.length; ++i) exprs.add(null == parser ? a[i] : parser.optimizer.optimize(a[i]))
                new FunctionDefineExpression(name, args, exprs.size() == 1 ? exprs[0] : block(exprs))
            }
        }
        // TODO: deftmpl
        define 'template', TYPE_CHECKER_TYPE, new TypeChecker() {
            TypedExpression transform(TypedContext context, Expression... args) {
                def c = context.child()
                c.label = "anonymous template"
                c.addVariable('exprs', CollectionsIterators.LIST_TYPE.generic(Reflection.EXPRESSION_TYPE))
                def typ = args[0].type(c)
                //if (typ.type != Reflection.EXPRESSION_TYPE) throw new UnexpectedTypeException('Expected type of template to be expression but was ' + typ.type)
                if (typ.runtimeOnly) throw new UnexpectedSyntaxException('Template must be able to run at compile time')
                new TypedConstantExpression(TEMPLATE_TYPE, new Template() {
                    @Override
                    Expression transform(Parser parser, Expression... a) {
                        c.set('exprs', new WrapperKismetObject(Arrays.asList(a)))
                        (Expression) typ.instruction.evaluate(c)
                    }
                })
            }
        }
        alias 'template', 'tmpl'
        define 'fn*', new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                def kill = new ArrayList<Expression>(args.length + 1)
                kill.add(name('fn'))
                kill.addAll(args)
                def res = call(name('fn'), call(
                        name('call'), call(kill),
                        subscript(name('_all'), number(0))))
                res
            }
        }
        define 'incr', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                def val = args.length > 1 ? call(name('+'), args[0], args[1]) :
                        call(name('next'), args[0])
                colon(args[0], val)
            }
        }
        define 'decr', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                def val = args.length > 1 ? call(name('-'), args[0], args[1]) :
                        call(name('prev'), args[0])
                colon(args[0], val)
            }
        }
        define '|>=', TEMPLATE_TYPE, new Template() {
            @CompileStatic
            Expression transform(Parser parser, Expression... args) {
                def val = pipeForwardExpr(args[0], args.tail().toList())
                colon(args[0], val)
            }
        }
        define '<|=', TEMPLATE_TYPE, new Template() {
            @CompileStatic
            Expression transform(Parser parser, Expression... args) {
                def val = pipeBackwardExpr(args[0], args.tail().toList())
                colon(args[0], val)
            }
        }
        define 'dive', TEMPLATE_TYPE, new Template() {
            @CompileStatic
            Expression transform(Parser parser, Expression... args) {
                new DiveExpression(args.length == 1 ? args[0] : block(args.toList()))
            }
        }
        define 'static', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                def typ = args[0].type(context)
                if (typ.runtimeOnly) throw new UnexpectedSyntaxException("Cannot make static a runtime only expression")
                new TypedConstantExpression(typ.type, typ.instruction.evaluate(context))
            }
        }
        define 'let', TEMPLATE_TYPE, new Template() {
            static boolean assignmentExpression(Expression expr) {
                expr instanceof ColonExpression || (
                    expr instanceof CallExpression &&
                    expr.size() == 3 &&
                    expr[0] instanceof NameExpression &&
                    ['=', ':=', '::=', ':::=', 'in'].contains(expr[0].toString()))
            }

            @CompileStatic
            Expression transform(Parser parser, Expression... args) {
                if (args.length == 0) throw new UnexpectedSyntaxException('Empty let expression not allowed')
                def mems = new ArrayList<Expression>()
                for (int i = 0; i < Math.max(args.length - 1, 1); ++i) {
                    def a = args[i]
                    if (a instanceof CallExpression || a instanceof ColonExpression)
                        mems.add((Expression) a)
                    else
                        mems.addAll(a.members)
                }
                Expression resultVar = null
                def result = new ArrayList<Expression>(mems.size())
                def eaches = new ArrayList<Tuple2<Expression, Expression>>()
                for (set in mems) {
                    if (assignmentExpression(set)) {
                        result.add(set)
                    } else if (set instanceof CallExpression) {
                        if (set.size() == 2 && assignmentExpression(set[1])) {
                            def n = toAtom(set[0])
                            final c = set[1]
                            if ('result' == n) {
                                result.add(c)
                                resultVar = c instanceof ColonExpression ? c.left : c[1]
                                continue
                            } else if ('each' == n) {
                                eaches.add(c instanceof ColonExpression ?
                                    new Tuple2(c.left, c.right) :
                                    new Tuple2(c[1], c[2]))
                                continue
                            }
                        } else if (set.size() == 3) {
                            def n = toAtom(set[0])
                            if ('result' == n) {
                                result.add(new ColonExpression(set[1], set[2]))
                                resultVar = set[1]
                                continue
                            } else if ('each' == n) {
                                eaches.add(new Tuple2(set[1], set[2]))
                                continue
                            }
                        }
                        def lem = set.members
                        def val = lem.pop()
                        for (em in lem) {
                            def atom = toAtom(em)
                            String atom0 = null
                            if (null != atom)
                                result.add(var(AssignmentType.SET, atom, val))
                            else if (em instanceof CallExpression && em.size() == 2 &&
                                    'result' == (atom0 = toAtom(em[0]))) {
                                def name = toAtom(em[1])
                                if (null == name)
                                    throw new UnexpectedSyntaxException("Non-name let results aren't supported")
                                result.add(var(AssignmentType.SET, name, val))
                                resultVar = ExprBuilder.name(name)
                            } else if ('each' == atom0) {
                                eaches.add(new Tuple2(em[1], val))
                            }
                        }
                    } else throw new UnexpectedSyntaxException("Unsupported let parameter $set. If you think it makes sense that iw as supported tell me")
                }
                if (!eaches.empty) {
                    int lastAdded = 0
                    for (int i = eaches.size() - 1; i >= 0; i--) {
                        final vari = eaches[i].v1
                        final val = eaches[i].v2
                        final popped = lastAdded == 0 ? Arrays.asList(args[args.length - 1]) : result[-lastAdded..-1]
                        for (int la = 0; la < lastAdded; ++la) {
                            result.remove(result.size() - 1)
                        }
                        String atom1 = null
                        if (val instanceof CallExpression &&
                            ('range' == (atom1 = toAtom(val[0])) || '..' == atom1)) {
                            result.add(colon(vari, val[1]))
                            def b = new ArrayList<Expression>(lastAdded + 1)
                            b.addAll(popped)
                            b.add(call(name('incr'), vari))
                            result.add(call(name('while'),
                                    call(name('<='), vari, val[2]),
                                    block(b)))
                            lastAdded = 2
                        } else if (val instanceof CallExpression &&
                            ('range<' == atom1 || '..<' == atom1)) {
                            result.add(colon(vari, val[1]))
                            def b = new ArrayList<Expression>(lastAdded + 1)
                            b.addAll(popped)
                            b.add(call(name('incr'), vari))
                            result.add(call(name('while'),
                                    call(name('<'), vari, val[2]),
                                    block(b)))
                            lastAdded = 2
                        } else {
                            final iterName = name('_iter'.concat((eaches.size() - i).toString()))
                            result.add(colon(iterName,
                                    call(name('to_iterator'), val)))
                            def b = new ArrayList<Expression>(lastAdded + 1)
                            b.add(colon(vari, call(
                                    name('next'), iterName)))
                            b.addAll(popped)
                            result.add(call(name('while'),
                                    call(name('has_next?'), iterName),
                                    block(b)))
                            lastAdded = 2
                        }
                    }
                } else result.addAll(args[args.length - 1])
                if (null != resultVar) result.add(resultVar)
                final r = block(result)
                args.length == 1 ? r : new DiveExpression(r)
            }
        }
        alias 'let', 'for'
        define 'get_or_set', TEMPLATE_TYPE, new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                def onc = name('internal`get_or_set')
                def res = block(
                        var(AssignmentType.SHADOW, 'internal`get_or_set', args[0]),
                        call(name('if'),
                                call(name('null?'), onc),
                                colon(args[0], args[1]),
                                onc))
                res
            }
        }
        define 'if', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                new IfElseExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context),
                        args.length > 2 ?
                            skipElseColon(args[2]).type(context) :
                            TypedNoExpression.INSTANCE)
            }
        }
        define 'unless', TEMPLATE_TYPE, new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                call(name('if'), call(name('not'), args[0]), args[1])
            }
        }
        define 'case', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                def up = args.length - 1
                if (up >= 0 && args[0] instanceof TupleExpression) {
                    def res = args[up] instanceof TupleExpression ?
                        TypedNoExpression.INSTANCE :
                        skipElseColon(args[up--]).type(context)
                    for (int i = up; i >= 0; --i) {
                        if (args[i] !instanceof TupleExpression)
                            throw new UnexpectedSyntaxException('Non-tuple expression passed to case with tuple cases')
                        def a = (TupleExpression) args[i]
                        TypedExpression cond, body
                        if (a.size() == 0) cond = body = TypedNoExpression.INSTANCE
                        else if (a.size() == 1) {
                            cond = TypedNoExpression.INSTANCE
                            body = a[0].type(context)
                        } else if (a.size() == 2) {
                            cond = a[0].type(context, +BOOLEAN_TYPE)
                            body = a[1].type(context)
                        } else {
                            def size = a.size()
                            def orArgs = new TypedExpression[size - 1]
                            def boolTypes = new Type[size - 1]
                            for (int j = 1; j < size; ++j) {
                                orArgs[j - 1] = a[j].type(context, +BOOLEAN_TYPE)
                                boolTypes[j - 1] = BOOLEAN_TYPE
                            }
                            cond = new InstructorCallExpression(
                                name('or').type(context, -instr(BOOLEAN_TYPE, boolTypes)),
                                orArgs, BOOLEAN_TYPE)
                            body = a[size - 1].type(context)
                        }
                        res = new IfElseExpression(cond, body, res)
                    }
                    res
                } else {
                    def res = up % 2 == 0 ?
                        skipElseColon(args[up--]).type(context) :
                        TypedNoExpression.INSTANCE
                    for (int i = up; i >= 0; i -= 2) {
                        res = new IfElseExpression(
                            args[i - 1].type(context, +BOOLEAN_TYPE),
                            args[i].type(context),
                            res)
                    }
                    res
                }
            }
        }
        /*define 'if', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                new IfElseExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context), args[2].type(context))
            }

            @Override
            IKismetObject call(Context c, Expression... args) {
                args[0].evaluate(c) ? args[1].evaluate(c) : args[2].evaluate(c)
            }
        }
        define 'not_if', TEMPLATE_TYPE, new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                call(name('if'), call(name('not'), args[0]), args[1], args[2])
            }
        }*/
        define 'while', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                new WhileExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context))
            }

            @Override
            IKismetObject call(Memory c, Expression... args) {
                while (args[0].evaluate(c)) {
                    args[1].evaluate(c)
                }
                Kismet.NULL
            }
        }
        define 'until', TEMPLATE_TYPE, new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                call(name('while'), call(name('not'), args[0]), args[1])
            }
        }
        define 'do_until', TYPE_CHECKER_TYPE, new TypeChecker() {
            @Override
            TypedExpression transform(TypedContext context, Expression... args) {
                new DoUntilExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context))
            }

            @Override
            IKismetObject call(Memory c, Expression... args) {
                while (true) {
                    args[1].evaluate(c)
                    if (args[0].evaluate(c)) break
                }
                Kismet.NULL
            }
        }
        define 'do_while', TEMPLATE_TYPE, new Template() {
            @Override
            Expression transform(Parser parser, Expression... args) {
                call(name('do_until'), call(name('not'), args[0]), args[1])
            }
        }
        define 'do', func(Type.NONE), Function.NOP
        define 'discard', TEMPLATE_TYPE, new Template() {
            @CompileStatic
            Expression transform(Parser parser, Expression... args) {
                NoExpression.INSTANCE
            }
        }
        define 'pick', INSTRUCTOR_TYPE, new Instructor() {
            @Override
            IKismetObject call(Memory m, Instruction... args) {
                IKismetObject last = Kismet.NULL
                for (it in args) if ((last = it.evaluate(m))) return last
                last
            }
        }
        define '|>', TEMPLATE_TYPE, new Template() {
            @CompileStatic
            Expression transform(Parser parser, Expression... args) {
                pipeForwardExpr(args[0], args.tail().toList())
            }
        }
        define '<|', TEMPLATE_TYPE, new Template() {
            @CompileStatic
            Expression transform(Parser parser, Expression... args) {
                pipeBackwardExpr(args[0], args.tail().toList())
            }
        }
        alias '|>', 'pipe'
        alias '<|', 'rpipe'
        define 'infix', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                infixLTR(args.toList())
            }
        }
        define '??', TEMPLATE_TYPE, new Template() {
            Expression transform(Parser parser, Expression... args) {
                if (args[0] instanceof PathStepExpression) {
                    def last = (Expression) args[0]
                    def temp = name('_temp_??')
                    def exprs = new ArrayList<Expression>()
                    while (last instanceof PathStepExpression) {
                        def p = (PathStepExpression) last
                        exprs.add(p.join([temp, p.right]))
                        last = p.root
                    }
                    exprs.add(last)
                    def exprsSize = exprs.size()
                    def newExprs = new ArrayList<Expression>(exprsSize + 1)
                    newExprs.add(name('or'))
                    for (int i = 0; i < exprsSize; ++i) {
                        newExprs.add(call(name('null?'),
                            colon(temp, exprs.get(exprsSize - 1 - i))))
                    }
                    call(name('if'), call(newExprs), name('null'), temp)
                } else {
                    args[0]
                }
            }
        }
    }

    static Expression skipElseColon(Expression expr) {
        expr instanceof ColonExpression && expr.left instanceof NameExpression &&
            expr.left.toString() == 'else' ? expr.right : expr
    }

    // unused:

    /*static void putPathExpression(Memory c, Map map, PathExpression path, value) {
        final exprs = path.steps
        final key = path.root instanceof NameExpression ? ((NameExpression) path.root).text : path.root.evaluate(c)
        for (ps in exprs.reverse()) {
            if (ps instanceof PathExpression.SubscriptStep) {
                def k = ((PathExpression.SubscriptStep) ps).expression.evaluate(c).inner()
                if (k instanceof Number) {
                    final list = new ArrayList()
                    list.set(k.intValue(), value)
                    value = list
                } else {
                    final hash = new HashMap()
                    hash.put(k, value)
                    value = hash
                }
            } else if (ps instanceof PathExpression.PropertyStep) {
                final hash = new HashMap()
                hash.put(((PathExpression.PropertyStep) ps).name, value)
                value = hash
            } else throw new UnexpectedSyntaxException("Tried to use path step $ps as key")
        }
        map.put(key, value)
    }

    static void expressiveMap(Map map, Memory c, Expression expr) {
        if (expr instanceof NameExpression) map.put(((NameExpression) expr).text, expr.evaluate(c))
        else if (expr instanceof PathExpression)
            putPathExpression(c, map, (PathExpression) expr, expr.evaluate(c))
        else if (expr instanceof CallExpression) {
            final exprs = ((CallExpression) expr).members
            final value = exprs.last().evaluate(c)
            for (x in exprs.init())
                if (x instanceof NameExpression)
                    map.put(((NameExpression) x).text, value)
                else if (x instanceof PathExpression)
                    putPathExpression(c, map, (PathExpression) x, value)
                else map.put(x.evaluate(c), value)
        } else if (expr instanceof BlockExpression) {
            final exprs = ((BlockExpression) expr).members
            for (x in exprs) expressiveMap(map, c, x)
        } else {
            final value = expr.evaluate(c)
            map.put(value, value)
        }
    }

    static boolean check(Memory c, IKismetObject val, Expression exp) {
        if (exp instanceof CallExpression) {
            def exprs = new ArrayList<Expression>()
            def valu = exp.callValue
            if (valu instanceof NameExpression) {
                def t = ((NameExpression) valu).text
                exprs.add(name(Strings.isAlphaNum(t) ? t + '?' : t))
            } else exprs.add(valu)
            c.set('it', val)
            exprs.add(name('it'))
            exprs.addAll(exp.arguments)
            def x = call(exprs)
            x.evaluate(c)
        } else if (exp instanceof BlockExpression) {
            boolean result = true
            for (x in exp.members) result = check(c, val, x)
            result
        } else if (exp instanceof NameExpression) {
            c.set('it', val)
            def t = exp.text
            call(name(Strings.isAlphaNum(t) ? t + '?' : t),
                    name('it')).evaluate(c)
        } else if (exp instanceof StringExpression) {
            val.inner() == exp.value.inner()
        } else if (exp instanceof NumberExpression) {
            val.inner() == exp.value.inner()
        } else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in check')
    }*/
}
