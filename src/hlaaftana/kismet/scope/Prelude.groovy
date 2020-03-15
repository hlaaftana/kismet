package hlaaftana.kismet.scope

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.*
import hlaaftana.kismet.exceptions.*
import hlaaftana.kismet.parser.Optimizer
import hlaaftana.kismet.parser.Parser
import hlaaftana.kismet.parser.StringEscaper
import hlaaftana.kismet.type.*
import static hlaaftana.kismet.type.TypeBound.*
import hlaaftana.kismet.vm.*

import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.regex.Pattern

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class Prelude {
	private static Map<String, RoundingMode> roundingModes = [
			'^': RoundingMode.CEILING, 'v': RoundingMode.FLOOR,
			'^0': RoundingMode.UP, 'v0': RoundingMode.DOWN,
			'/^': RoundingMode.HALF_UP, '/v': RoundingMode.HALF_DOWN,
			'/2': RoundingMode.HALF_EVEN, '!': RoundingMode.UNNECESSARY
	].asImmutable()
	static final SingleType STRING_TYPE = new SingleType('String'),
			TEMPLATE_TYPE = new SingleType('Template'),
			TYPE_CHECKER_TYPE = new SingleType('TypeChecker'),
			INSTRUCTOR_TYPE = new SingleType('Instructor', [+TupleType.BASE, -Type.NONE] as TypeBound[]),
			TYPED_TEMPLATE_TYPE = new SingleType('TypedTemplate', [+TupleType.BASE, -Type.NONE] as TypeBound[]),
			INSTRUCTION_TYPE = new SingleType('Instruction'),
			MEMORY_TYPE = new SingleType('Memory'),
			META_TYPE = new SingleType('Meta', [+Type.ANY] as TypeBound[]),
			LIST_TYPE = new SingleType('List', [+Type.ANY] as TypeBound[]),
			SET_TYPE = new SingleType('Set', [+Type.ANY] as TypeBound[]),
			MAP_TYPE = new SingleType('Map', [+Type.ANY, +Type.ANY] as TypeBound[]),
			FUNCTION_TYPE = new SingleType('Function', [+TupleType.BASE, -Type.NONE] as TypeBound[]),
			BOOLEAN_TYPE = new SingleType('Boolean'),
			EXPRESSION_TYPE = new SingleType('Expression'),
			CLOSURE_ITERATOR_TYPE = new SingleType('ClosureIterator')
	static TypedContext typed = new TypedContext()
	static Context defaultContext = new Context()
	static Parser parser = new Parser()

	static Type inferType(IKismetObject value) {
		if (value instanceof KismetNumber) value.type
		else if (value instanceof Function) FUNCTION_TYPE
		else if (value instanceof Template) TEMPLATE_TYPE
		else if (value instanceof TypeChecker) TYPE_CHECKER_TYPE
		else if (value instanceof Instructor) INSTRUCTOR_TYPE
		else if (value instanceof TypedTemplate) TYPED_TEMPLATE_TYPE
		else if (value instanceof Type) new GenericType(META_TYPE, value)
		else throw new UnsupportedOperationException("Cannot infer type for kismet object $value")
	}

	static void define(String name, Type type, IKismetObject object) {
		typed(name, type, object)
		defaultContext.add(name, object)
	}

	static void define(String name, IKismetObject object) {
		define(name, inferType(object), object)
	}

	static void typed(String name, Type type, IKismetObject object) {
		typed.addVariable(name, object, type)
	}

	static void alias(String old, String... news) {
		final dyn = defaultContext.get(old)
		final typ = typed.getAll(old)
		for (final n : news) {
			defaultContext.add(n, dyn)
			for (final t : typ) {
				typed.addVariable(n, t.variable.value, t.variable.type)
			}
		}
	}

	static void negated(final String old, String... news) {
		def temp = new Template() {
			@Override
			Expression transform(Parser parser, Expression... args) {
				def arr = new ArrayList<Expression>(args.length + 1)
				arr.add(new NameExpression(old))
				arr.addAll(args)
				new CallExpression(new NameExpression('not'), new CallExpression(arr))
			}
		}
		for (final n : news) {
			defaultContext.add(n, temp)
			typed.addVariable(n, temp, TEMPLATE_TYPE)
		}
	}

	static void parse(String kismet) {
		def p = parser.parse(kismet)
		p.type(typed).instruction.evaluate(typed)
		p.evaluate(defaultContext)
	}

	static {
		syntax: {
			define '.property', func(Type.ANY, Type.ANY, STRING_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					Kismet.model(args[0].getAt(((CharSequence) args[1]).toString()))
				}
			}
			define '.property', func(Type.ANY, MAP_TYPE, STRING_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					println args
					Kismet.model(((Map) args[0].inner()).get(((CharSequence) args[1]).toString()))
				}
			}
			define '.[]', func(Type.ANY, Type.ANY, STRING_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					Kismet.model(args[0].invokeMethod('getAt', [args[1].inner()] as Object[]))
				}
			}
			define '.[]=', func(Type.ANY, Type.ANY, STRING_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					Kismet.model(args[0].invokeMethod('putAt', [args[1].inner(), args[2].inner()] as Object[]))
				}
			}
			define ':::=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.CHANGE)
			define '::=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.SET)
			define ':=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.DEFINE)
			define '=', TEMPLATE_TYPE, new AssignTemplate(AssignmentType.ASSIGN)
			define 'name_expr', func(EXPRESSION_TYPE, STRING_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new NameExpression(((KismetString) args[0]).inner())
				}
			}
			define 'name_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0] instanceof ConstantExpression)
				}
			}
			define 'expr_to_name', func(STRING_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					def i = args[0] instanceof Expression ? toAtom((Expression) args[0]) : (String) null
					null == i ? Kismet.NULL : new KismetString(i)
				}
			}
			define 'static_expr', func(EXPRESSION_TYPE, Type.ANY), new Function() {
				IKismetObject call(IKismetObject... args) {
					new StaticExpression(args[0])
				}
			}
			define 'static_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0] instanceof ConstantExpression)
				}
			}
			define 'number_expr', func(EXPRESSION_TYPE, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					def result = new NumberExpression()
					result.@value = (KismetNumber) args[0]
					result
				}
			}
			define 'number_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0] instanceof NumberExpression)
				}
			}
			define 'string_expr', func(EXPRESSION_TYPE, STRING_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new StringExpression(((KismetString) args[0]).inner())
				}
			}
			define 'string_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0] instanceof StringExpression)
				}
			}
			define 'call_expr', func(EXPRESSION_TYPE, LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
				IKismetObject call(IKismetObject... args) {
					new CallExpression((List<Expression>) args[0].inner())
				}
			}
			define 'call_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0] instanceof CallExpression)
				}
			}
			define 'block_expr', func(EXPRESSION_TYPE, LIST_TYPE.generic(EXPRESSION_TYPE)), new Function() {
				IKismetObject call(IKismetObject... args) {
					new BlockExpression((List<Expression>) args[0].inner())
				}
			}
			define 'block_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0] instanceof BlockExpression)
				}
			}
			define 'dive_expr', func(EXPRESSION_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new DiveExpression((Expression) args[0])
				}
			}
			define 'dive_expr?', func(BOOLEAN_TYPE, EXPRESSION_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0] instanceof DiveExpression)
				}
			}
			define 'tmpl', TYPE_CHECKER_TYPE, new TypeChecker() {
				TypedExpression transform(TypedContext context, Expression... args) {
					def c = context.child()
					c.addVariable('exprs', LIST_TYPE.generic(EXPRESSION_TYPE))
					def typ = args[0].type(c)
					if (typ.type != EXPRESSION_TYPE) throw new UnexpectedTypeException('Expected type of template to be expression but was ' + typ.type)
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
			define 'cast', TYPE_CHECKER_TYPE, new TypeChecker() {
				@CompileStatic
				TypedExpression transform(TypedContext context, Expression... args) {
					final typ = ((GenericType) args[0].type(context, +META_TYPE).type).bounds[0]
					final expr = args[1].type(context)
					new TypedExpression() {
						Type getType() { typ }
						Instruction getInstruction() { expr.instruction }
					}
				}
			}
			define '+=', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					new CallExpression([new NameExpression('='), args[0],
							new CallExpression([new NameExpression('+'), args[0], args[1]])])
				}
			}
			define 'def', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					if (args.length == 0) throw new UnexpectedSyntaxException('Cannot def without any arguments')
					if (args[0] instanceof NameExpression) {
						new VariableModifyExpression(AssignmentType.DEFINE, ((NameExpression) args[0]).text, args[1])
					} else {
						new FunctionDefineExpression(args)
					}
				}
			}
			define 'instructor', new TypeChecker() {
				TypedExpression transform(TypedContext context, Expression... args) {
					def c = context.child()
					c.addVariable('instructions', LIST_TYPE.generic(INSTRUCTION_TYPE))
					def typ = args[0].type(c)
					new TypedConstantExpression(new GenericType(INSTRUCTOR_TYPE, TupleType.BASE, typ.type), new Instructor() {
						IKismetObject call(Memory m, Instruction... a) {
							m.set(0, new WrapperKismetObject(Arrays.asList(a)))
							typ.instruction.evaluate(m)
						}
					})
				}
			}
			define 'Function', new GenericType(META_TYPE, FUNCTION_TYPE), FUNCTION_TYPE
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
					final String name
					final Arguments args

					def f = a[0]
					if (f instanceof NameExpression) {
						name = ((NameExpression) f).text
						args = new Arguments(null)
					} else if (f instanceof CallExpression && f[0] instanceof NameExpression) {
						name = ((NameExpression) ((CallExpression) f).callValue).text
						args = new Arguments(((CallExpression) f).arguments)
					} else throw new UnexpectedSyntaxException("Can't define function with declaration " + f)
					def exprs = new ArrayList<Expression>(a.length - 1)
					for (int i = 1; i < a.length; ++i) exprs.add(null == parser ? a[i] : parser.optimizer.optimize(a[i]))
					new FunctionDefineExpression(name, args, exprs.size() == 1 ? exprs[0] : new BlockExpression(exprs))
				}
			}
			define 'defmcr', new Template() {
				@CompileStatic
				Expression transform(Parser parser, Expression... args) {
					def a = new ArrayList<Expression>(args.length + 1)
					a.add(new NameExpression('mcr'))
					for (int i = 1; i < args.length; ++i)
						a.add(args[i])
					new VariableModifyExpression(AssignmentType.DEFINE, toAtom(args[0]), new CallExpression(a))
				}
			}
			define 'fn*', new Template() {
				@Override
				Expression transform(Parser parser, Expression... args) {
					def kill = new ArrayList<Expression>(args.length + 1)
					kill.add(new NameExpression('fn'))
					kill.addAll(args)
					def res = new CallExpression(new NameExpression('fn'), new CallExpression(
							new NameExpression('call'), new CallExpression(kill),
							new PathExpression(new NameExpression('_all'), [
									(PathExpression.Step) new PathExpression.SubscriptStep(new NumberExpression(0))])))
					println res
					res
				}
			}
			define 'incr', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					def val = args.length > 1 ? new CallExpression(new NameExpression('+'), args[0], args[1]) :
							new CallExpression(new NameExpression('next'), args[0])
					new ColonExpression(args[0], val)
				}
			}
			define 'decr', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					def val = args.length > 1 ? new CallExpression(new NameExpression('-'), args[0], args[1]) :
							new CallExpression(new NameExpression('prev'), args[0])
					new ColonExpression(args[0], val)
				}
			}
			define '|>=', TEMPLATE_TYPE, new Template() {
				@CompileStatic
				Expression transform(Parser parser, Expression... args) {
					def val = pipeForwardExpr(args[0], args.tail().toList())
					new ColonExpression(args[0], val)
				}
			}
			define '<|=', TEMPLATE_TYPE, new Template() {
				@CompileStatic
				Expression transform(Parser parser, Expression... args) {
					def val = pipeBackwardExpr(args[0], args.tail().toList())
					new ColonExpression(args[0], val)
				}
			}
			define 'dive', TEMPLATE_TYPE, new Template() {
				@CompileStatic
				Expression transform(Parser parser, Expression... args) {
					new DiveExpression(args.length == 1 ? args[0] : new BlockExpression(args.toList()))
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
				@CompileStatic
				Expression transform(Parser parser, Expression... args) {
					if (args.length == 0) throw new UnexpectedSyntaxException('Empty let expression not allowed')
					final mems = args[0] instanceof CallExpression || args[0] instanceof ColonExpression ?
							Collections.singletonList(args[0]) : args[0].members
					Expression resultVar = null
					def result = new ArrayList<Expression>(mems.size())
					def eaches = new ArrayList<Tuple2<Expression, Expression>>()
					for (set in mems) {
						if (set instanceof ColonExpression) {
							result.add((Expression) set)
						} else if (set instanceof CallExpression) {
							if (set.size() == 2 && set[1] instanceof ColonExpression) {
								def n = toAtom(set[0])
								final c = (ColonExpression) set[1]
								if ('result' == n) {
									result.add(set[1])
									resultVar = c.left
									continue
								} else if ('each' == n) {
									eaches.add(new Tuple2(c.left, c.right))
									continue
								}
							}
							def lem = set.members
							def val = lem.pop()
							for (em in lem) {
								def atom = toAtom(em)
								String atom0 = null
								if (null != atom)
									result.add(new VariableModifyExpression(AssignmentType.SET, atom, val))
								else if (em instanceof CallExpression && em.size() == 2 &&
										'result' == (atom0 = toAtom(em[0]))) {
									def name = toAtom(em[1])
									if (null == name)
										throw new UnexpectedSyntaxException("Non-name let results aren't supported")
									result.add(new VariableModifyExpression(AssignmentType.SET, name, val))
									resultVar = new NameExpression(name)
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
							final popped = lastAdded == 0 ? Arrays.asList(args).tail() : result[-lastAdded..-1]
							for (int la = result.size() - 1; la >= result.size() - lastAdded; --la) {
								result.remove(la)
							}
							String atom1 = null
							if (val instanceof CallExpression && 'range' == (atom1 = toAtom(val[0]))) {
								result.add(new ColonExpression(vari, val[1]))
								def b = new ArrayList<Expression>(lastAdded + 1)
								b.addAll(popped)
								b.add(new CallExpression(new NameExpression('incr'), vari))
								result.add(new CallExpression(new NameExpression('while'),
									new CallExpression(new NameExpression('<='), vari, val[2]),
									new BlockExpression(b)))
								lastAdded = 2
							} else if ('range<' == atom1) {
								result.add(new ColonExpression(vari, val[1]))
								def b = new ArrayList<Expression>(lastAdded + 1)
								b.addAll(popped)
								b.add(new CallExpression(new NameExpression('incr'), vari))
								result.add(new CallExpression(new NameExpression('while'),
										new CallExpression(new NameExpression('<'), vari, val[2]),
										new BlockExpression(b)))
								lastAdded = 2
							} else {
								final iterName = new NameExpression('_iter'.concat((eaches.size() - i).toString()))
								result.add(new ColonExpression(iterName,
									new CallExpression(new NameExpression('to_iterator'), val)))
								def b = new ArrayList<Expression>(lastAdded + 1)
								b.add(new ColonExpression(vari, new CallExpression(
										new NameExpression('next'), iterName)))
								b.addAll(popped)
								result.add(new CallExpression(new NameExpression('while'),
										new CallExpression(new NameExpression('has_next?'), iterName),
										new BlockExpression(b)))
								lastAdded = 2
							}
						}
					}
					result.addAll(args.tail())
					if (null != resultVar) result.add(resultVar)
					final r = new BlockExpression(result)
					args.length == 1 ? r : new DiveExpression(r)
				}
			}
			define 'quote', TEMPLATE_TYPE, new Template() {
				@CompileStatic
				Expression transform(Parser parser, Expression... args) {
					def slowdown = new ArrayList<Expression>(args.length + 1)
					slowdown.add(new NameExpression('quote'))
					slowdown.addAll(args)
					new StaticExpression(new CallExpression(slowdown),
							args.length == 1 ? args[0] :
									new BlockExpression(args.toList()))
				}
			}
			define 'get_or_set', TEMPLATE_TYPE, new Template() {
				@Override
				Expression transform(Parser parser, Expression... args) {
					def onc = new OnceExpression(args[0])
					new CallExpression(new NameExpression('or?'),
							new CallExpression(new NameExpression('null?'), onc),
							onc,
							new ColonExpression(args[0], args[1]))
				}
			}
			define 'if', TYPE_CHECKER_TYPE, new TypeChecker() {
				@Override
				TypedExpression transform(TypedContext context, Expression... args) {
					new IfElseExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context), TypedNoExpression.INSTANCE)
				}

				@Override
				IKismetObject call(Context c, Expression... args) {
					if (args[0].evaluate(c)) {
						args[1].evaluate(c)
					} else Kismet.NULL
				}
			}
			define 'unless', TEMPLATE_TYPE, new Template() {
				@Override
				Expression transform(Parser parser, Expression... args) {
					new CallExpression(new NameExpression('if'), new CallExpression(new NameExpression('not'), args[0]), args[1])
				}
			}
			define 'or?', TYPE_CHECKER_TYPE, new TypeChecker() {
				@Override
				TypedExpression transform(TypedContext context, Expression... args) {
					new IfElseExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context), args[2].type(context))
				}

				@Override
				IKismetObject call(Context c, Expression... args) {
					args[0].evaluate(c) ? args[1].evaluate(c) : args[2].evaluate(c)
				}
			}
			define 'not_or?', TEMPLATE_TYPE, new Template() {
				@Override
				Expression transform(Parser parser, Expression... args) {
					new CallExpression(new NameExpression('or?'), new CallExpression(new NameExpression('not'), args[0]), args[1], args[2])
				}
			}
			define 'while', TYPE_CHECKER_TYPE, new TypeChecker() {
				@Override
				TypedExpression transform(TypedContext context, Expression... args) {
					new WhileExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context))
				}

				@Override
				IKismetObject call(Context c, Expression... args) {
					while (args[0].evaluate(c)) {
						args[1].evaluate(c)
					}
					Kismet.NULL
				}
			}
			define 'until', TEMPLATE_TYPE, new Template() {
				@Override
				Expression transform(Parser parser, Expression... args) {
					new CallExpression(new NameExpression('while'), new CallExpression(new NameExpression('not'), args[0]), args[1])
				}
			}
			define 'do_until', TYPE_CHECKER_TYPE, new TypeChecker() {
				@Override
				TypedExpression transform(TypedContext context, Expression... args) {
					new DoUntilExpression(args[0].type(context, +BOOLEAN_TYPE), args[1].type(context))
				}

				@Override
				IKismetObject call(Context c, Expression... args) {
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
					new CallExpression(new NameExpression('do_until'), new CallExpression(new NameExpression('not'), args[0]), args[1])
				}
			}
			define 'do', func(Type.NONE), Function.NOP
			define 'don\'t', TEMPLATE_TYPE, new Template() {
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

			define 'hex', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					if (args[0] instanceof NumberExpression || args[0] instanceof NameExpression) {
						String t = args[0] instanceof NumberExpression ?
								((NumberExpression) args[0]).value.inner().toString() :
								((NameExpression) args[0]).text
						new NumberExpression(new BigInteger(t, 16))
					} else throw new UnexpectedSyntaxException('Expression after hex was not a hexadecimal number literal.'
							+ ' To convert hex strings to integers do [from_base str 16], '
							+ ' and to convert integers to hex strings do [to_base i 16].')
				}
			}
			define 'binary', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					if (args[0] instanceof NumberExpression) {
						String t = ((NumberExpression) args[0]).value.inner().toString()
						new NumberExpression(new BigInteger(t, 2))
					} else throw new UnexpectedSyntaxException('Expression after binary was not a binary number literal.'
							+ ' To convert binary strings to integers do [from_base str 2], '
							+ ' and to convert integers to binary strings do [to_base i 2].')
				}
			}
			define 'octal', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					if (args[0] instanceof NumberExpression) {
						String t = ((NumberExpression) args[0]).value.inner().toString()
						new NumberExpression(new BigInteger(t, 8))
					} else throw new UnexpectedSyntaxException('Expression after octal was not a octal number literal.'
							+ ' To convert octal strings to integers do [from_base str 8], '
							+ ' and to convert integers to octal strings do [to_base i 8].')
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
			define 'infix', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					infixLTR(args.toList())
				}
			}
		}
		define 'Any', new GenericType(META_TYPE, Type.ANY), Type.ANY
		define 'None', new GenericType(META_TYPE, Type.NONE), Type.NONE
		define 'null', Type.NONE, Kismet.NULL
		define 'null?', func(BOOLEAN_TYPE, Type.ANY), new Function() {
			IKismetObject call(IKismetObject... args) {
				new KismetBoolean(null == args[0] || null == args[0].inner())
			}
		}
		negated 'null?', 'not_null?'
		bool: {
			define 'Boolean', new GenericType(META_TYPE, BOOLEAN_TYPE), BOOLEAN_TYPE
			define 'true', BOOLEAN_TYPE, KismetBoolean.TRUE
			define 'false', BOOLEAN_TYPE, KismetBoolean.FALSE
			define 'and', new GenericType(INSTRUCTOR_TYPE, new TupleType(new Type[0]).withVarargs(BOOLEAN_TYPE), BOOLEAN_TYPE), new Instructor() {
				@Override
				IKismetObject call(Memory m, Instruction... args) {
					KismetBoolean last = KismetBoolean.TRUE
					for (it in args) if (!(last = (KismetBoolean) it.evaluate(m)).inner) return KismetBoolean.FALSE
					last
				}
			}
			define 'or', new GenericType(INSTRUCTOR_TYPE, new TupleType(new Type[0]).withVarargs(BOOLEAN_TYPE), BOOLEAN_TYPE), new Instructor() {
				@Override
				IKismetObject call(Memory m, Instruction... args) {
					KismetBoolean last = KismetBoolean.FALSE
					for (it in args) if ((last = (KismetBoolean) it.evaluate(m)).inner) return KismetBoolean.TRUE
					last
				}
			}
			define 'and', instr(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Instructor() {
				@Override
				IKismetObject call(Memory m, Instruction... args) {
					new KismetBoolean(((KismetBoolean) args[0].evaluate(m)).inner && ((KismetBoolean) args[1].evaluate(m)).inner)
				}
			}
			define 'or', instr(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Instructor() {
				@Override
				IKismetObject call(Memory m, Instruction... args) {
					new KismetBoolean(((KismetBoolean) args[0].evaluate(m)).inner || ((KismetBoolean) args[1].evaluate(m)).inner)
				}
			}
			define 'xor', func(BOOLEAN_TYPE, BOOLEAN_TYPE, BOOLEAN_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetBoolean) args[0]).inner ^ ((KismetBoolean) args[1]).inner)
				}
			}
			define 'bool', func(BOOLEAN_TYPE, Type.ANY), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(args[0].inner() as boolean)
				}
			}
			alias 'bool', 'true?', '?'
			negated 'bool', 'false?', 'no?', 'off?'
			define 'not', func(BOOLEAN_TYPE, BOOLEAN_TYPE), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(!((KismetBoolean) args[0]).inner)
				}
			}
		}
		number: {
			for (final n : NumberType.values())
				define n.name(), new GenericType(META_TYPE, n), n
			define 'e', NumberType.Float, new KFloat(BigDecimal.valueOf(Math.E))
			define 'pi', NumberType.Float, new KFloat(BigDecimal.valueOf(Math.PI))
			define 'bit_not', func(NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KInt(((KInt) args[0]).inner.not())
				}
			}
			define 'bit_not', func(NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KInt64(((KInt64) args[0]).inner.bitwiseNegate().longValue())
				}
			}
			define 'bit_not', func(NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KInt32(((KInt32) args[0]).inner.bitwiseNegate().intValue())
				}
			}
			define 'bit_not', func(NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KInt16(((KInt16) args[0]).inner.bitwiseNegate().shortValue())
				}
			}
			define 'bit_not', func(NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KInt8(((KInt8) args[0]).inner.bitwiseNegate().byteValue())
				}
			}
			define 'bit_xor', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).xor((KInt) args[1])
				}
			}
			define 'bit_xor', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).xor((KInt64) args[1])
				}
			}
			define 'bit_xor', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).xor((KInt32) args[1])
				}
			}
			define 'bit_xor', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).xor((KInt16) args[1])
				}
			}
			define 'bit_xor', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).xor((KInt8) args[1])
				}
			}
			define 'bit_and', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).and((KInt) args[1])
				}
			}
			define 'bit_and', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).and((KInt64) args[1])
				}
			}
			define 'bit_and', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).and((KInt32) args[1])
				}
			}
			define 'bit_and', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).and((KInt16) args[1])
				}
			}
			define 'bit_and', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).and((KInt8) args[1])
				}
			}
			define 'bit_or', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).or((KInt) args[1])
				}
			}
			define 'bit_or', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).or((KInt64) args[1])
				}
			}
			define 'bit_or', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).or((KInt32) args[1])
				}
			}
			define 'bit_or', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).or((KInt16) args[1])
				}
			}
			define 'bit_or', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).or((KInt8) args[1])
				}
			}
			define 'left_shift', func(NumberType.Int, NumberType.Int, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KInt(((KInt) args[0]).inner.shiftLeft(((KInt32) args[1]).inner))
				}
			}
			define 'left_shift', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).leftShift((KInt64) args[1])
				}
			}
			define 'left_shift', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).leftShift((KInt32) args[1])
				}
			}
			define 'left_shift', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).leftShift((KInt16) args[1])
				}
			}
			define 'left_shift', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).leftShift((KInt8) args[1])
				}
			}
			define 'right_shift', func(NumberType.Int, NumberType.Int, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KInt(((KInt) args[0]).inner.shiftRight(((KInt32) args[1]).inner))
				}
			}
			define 'right_shift', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).rightShift((KInt64) args[1])
				}
			}
			define 'right_shift', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).rightShift((KInt32) args[1])
				}
			}
			define 'right_shift', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).rightShift((KInt16) args[1])
				}
			}
			define 'right_shift', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).rightShift((KInt8) args[1])
				}
			}
			define 'unsigned_right_shift', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).rightShiftUnsigned((KInt64) args[1])
				}
			}
			define 'unsigned_right_shift', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).rightShiftUnsigned((KInt32) args[1])
				}
			}
			define 'unsigned_right_shift', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).rightShiftUnsigned((KInt16) args[1])
				}
			}
			define 'unsigned_right_shift', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).rightShiftUnsigned((KInt8) args[1])
				}
			}
			define '<', func(BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) < 0)
				}
			}
			define '>', func(BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) > 0)
				}
			}
			define '<=', func(BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) <= 0)
				}
			}
			define '>=', func(BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo((KismetNumber) args[1]) >= 0)
				}
			}
			define '<=>', func(NumberType.Int32, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... a) {
					new KInt32(((KismetNumber) a[0]).compareTo((KismetNumber) a[1]))
				}
			}
			define 'positive', func(NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KismetNumber) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Float, NumberType.Float), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat64) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Float32, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat32) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).unaryPlus()
				}
			}
			define 'positive', func(NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).unaryPlus()
				}
			}
			define 'negative', func(NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KismetNumber) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Float, NumberType.Float), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat64) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Float32, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat32) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).unaryMinus()
				}
			}
			define 'negative', func(NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).unaryMinus()
				}
			}
			define 'positive?', func(BOOLEAN_TYPE, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo(KInt32.ZERO) > 0)
				}
			}
			define 'negative?', func(BOOLEAN_TYPE, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo(KInt32.ZERO) < 0)
				}
			}
			define 'zero?', func(BOOLEAN_TYPE, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo(KInt32.ZERO) == 0)
				}
			}
			define 'one?', func(BOOLEAN_TYPE, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).compareTo(KInt32.ONE) == 0)
				}
			}
			define 'even?', func(BOOLEAN_TYPE, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).divisibleBy(KInt32.TWO))
				}
			}
			negated 'even?', 'odd?'
			define 'divisible_by?', func(BOOLEAN_TYPE, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).divisibleBy((KismetNumber) args[1]))
				}
			}
			define 'integer?', func(BOOLEAN_TYPE, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).divisibleBy(KInt32.ONE))
				}
			}
			define 'integer?', func(BOOLEAN_TYPE, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KismetBoolean(((KismetNumber) args[0]).divisibleBy(KInt32.ONE))
				}
			}
			define 'integer?', func(BOOLEAN_TYPE, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					KismetBoolean.TRUE
				}
			}
			negated 'integer?', 'decimal?'
			define 'natural?', TEMPLATE_TYPE, new Template() {
				Expression transform(Parser parser, Expression... args) {
					def onc = new OnceExpression(args[0])
					new CallExpression(new NameExpression('and'),
							new CallExpression(new NameExpression('integer?'), onc),
							new CallExpression(new NameExpression('positive?'), onc))
				}
			}
			define 'absolute',  funcc(true) { ... a -> a[0].invokeMethod('abs', null) }
			define 'squared',  funcc(true) { ... a -> a[0].invokeMethod('multiply', [a[0]] as Object[]) }
			define 'square_root',  funcc(true) { ... args -> ((Object) Math).invokeMethod('sqrt', [args[0]] as Object[]) }
			define 'cube_root',  funcc(true) { ... args -> ((Object) Math).invokeMethod('cbrt', [args[0]] as Object[]) }
			define 'sine',  funcc(true) { ... args -> ((Object) Math).invokeMethod('sin', [args[0]] as Object[]) }
			define 'cosine',  funcc(true) { ... args -> ((Object) Math).invokeMethod('cos', [args[0]] as Object[]) }
			define 'tangent',  funcc(true) { ... args -> ((Object) Math).invokeMethod('tan', [args[0]] as Object[]) }
			define 'hyperbolic_sine',  funcc(true) { ... args -> ((Object) Math).invokeMethod('sinh', [args[0]] as Object[]) }
			define 'hyperbolic_cosine',  funcc(true) { ... args -> ((Object) Math).invokeMethod('cosh', [args[0]] as Object[]) }
			define 'hyperbolic_tangent',  funcc(true) { ... args -> ((Object) Math).invokeMethod('tanh', [args[0]] as Object[]) }
			define 'arcsine',  funcc(true) { ... args -> Math.asin(args[0] as double) }
			define 'arccosine',  funcc(true) { ... args -> Math.acos(args[0] as double) }
			define 'arctangent',  funcc(true) { ... args -> Math.atan(args[0] as double) }
			define 'arctan2',  funcc(true) { ... args -> Math.atan2(args[0] as double, args[1] as double) }
			define 'do_round',  funcc(true) { ...args ->
				def value = args[0]
				String mode = args[1]?.toString()
				if (null != mode) value = value as BigDecimal
				if (value instanceof BigDecimal) {
					RoundingMode realMode
					if (null != mode) {
						final m = roundingModes[mode]
						if (null == m) throw new UnexpectedValueException('Unknown rounding mode ' + mode)
						realMode = m
					} else realMode = RoundingMode.HALF_UP
					value.setScale(null == args[2] ? 0 : args[2] as int, realMode).stripTrailingZeros()
				} else if (value instanceof BigInteger
						|| value instanceof Integer
						|| value instanceof Long) value
				else if (value instanceof Float) Math.round(value.floatValue())
				else Math.round(((Number) value).doubleValue())
			}
			define 'round',  new Template() {
				@Override
				Expression transform(Parser parser, Expression... args) {
					def x = new ArrayList<Expression>(4)
					x.add(new NameExpression('do_round'))
					x.add(args[0])
					if (args.length > 1) x.add(new StaticExpression(args[1], toAtom(args[1])))
					if (args.length > 2) x.add(args[2])
					new CallExpression(x)
				}
			}
			define 'floor',  funcc(true) { ... args ->
				def value = args[0]
				if (args.length > 1) value = value as BigDecimal
				if (value instanceof BigDecimal)
					((BigDecimal) value).setScale(args.length > 1 ? args[1] as int : 0,
							RoundingMode.FLOOR).stripTrailingZeros()
				else if (value instanceof BigInteger ||
						value instanceof Integer ||
						value instanceof Long) value
				else Math.floor(value as double)
			}
			define 'ceil',  funcc(true) { ... args ->
				def value = args[0]
				if (args.length > 1) value = value as BigDecimal
				if (value instanceof BigDecimal)
					((BigDecimal) value).setScale(args.length > 1 ? args[1] as int : 0,
							RoundingMode.CEILING).stripTrailingZeros()
				else if (value instanceof BigInteger ||
						value instanceof Integer ||
						value instanceof Long) value
				else Math.ceil(value as double)
			}
			define 'logarithm', func(NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					new KFloat64(Math.log(((KFloat64) args[0]).inner))
				}
			}
			define 'plus', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KismetNumber) args[0]).plus((KismetNumber) args[1])
				}
			}
			define 'plus', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat) args[0]).plus((KFloat) args[1])
				}
			}
			define 'plus', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat64) args[0]).plus((KFloat64) args[1])
				}
			}
			define 'plus', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat32) args[0]).plus((KFloat32) args[1])
				}
			}
			define 'plus', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).plus((KInt) args[1])
				}
			}
			define 'plus', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).plus((KInt64) args[1])
				}
			}
			define 'plus', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).plus((KInt32) args[1])
				}
			}
			define 'plus', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).plus((KInt16) args[1])
				}
			}
			define 'plus', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).plus((KInt8) args[1])
				}
			}
			define 'minus', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					println args
					println args*.inner()
					println args*.inner()*.getClass()
					((KismetNumber) args[0]).minus((KismetNumber) args[1])
				}
			}
			define 'minus', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat) args[0]).minus((KFloat) args[1])
				}
			}
			define 'minus', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat64) args[0]).minus((KFloat64) args[1])
				}
			}
			define 'minus', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat32) args[0]).minus((KFloat32) args[1])
				}
			}
			define 'minus', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).minus((KInt) args[1])
				}
			}
			define 'minus', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).minus((KInt64) args[1])
				}
			}
			define 'minus', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).minus((KInt32) args[1])
				}
			}
			define 'minus', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).minus((KInt16) args[1])
				}
			}
			define 'minus', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).minus((KInt8) args[1])
				}
			}
			define 'multiply', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KismetNumber) args[0]).multiply((KismetNumber) args[1])
				}
			}
			define 'multiply', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat) args[0]).multiply((KFloat) args[1])
				}
			}
			define 'multiply', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat64) args[0]).multiply((KFloat64) args[1])
				}
			}
			define 'multiply', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat32) args[0]).multiply((KFloat32) args[1])
				}
			}
			define 'multiply', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).multiply((KInt) args[1])
				}
			}
			define 'multiply', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).multiply((KInt64) args[1])
				}
			}
			define 'multiply', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).multiply((KInt32) args[1])
				}
			}
			define 'multiply', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).multiply((KInt16) args[1])
				}
			}
			define 'multiply', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).multiply((KInt8) args[1])
				}
			}
			define 'divide', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KismetNumber) args[0]).div((KismetNumber) args[1])
				}
			}
			define 'divide', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat) args[0]).div((KFloat) args[1])
				}
			}
			define 'divide', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat64) args[0]).div((KFloat64) args[1])
				}
			}
			define 'divide', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat32) args[0]).div((KFloat32) args[1])
				}
			}
			define 'divide', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).div((KInt) args[1])
				}
			}
			define 'divide', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).div((KInt64) args[1])
				}
			}
			define 'divide', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).div((KInt32) args[1])
				}
			}
			define 'divide', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).div((KInt16) args[1])
				}
			}
			define 'divide', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).div((KInt8) args[1])
				}
			}
			define 'div', func(NumberType.Number, NumberType.Number, NumberType.Number), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KismetNumber) args[0]).intdiv((KismetNumber) args[1])
				}
			}
			define 'div', func(NumberType.Float, NumberType.Float, NumberType.Float), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat) args[0]).intdiv((KFloat) args[1])
				}
			}
			define 'div', func(NumberType.Float64, NumberType.Float64, NumberType.Float64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat64) args[0]).intdiv((KFloat64) args[1])
				}
			}
			define 'div', func(NumberType.Float32, NumberType.Float32, NumberType.Float32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KFloat32) args[0]).intdiv((KFloat32) args[1])
				}
			}
			define 'div', func(NumberType.Int, NumberType.Int, NumberType.Int), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt) args[0]).intdiv((KInt) args[1])
				}
			}
			define 'div', func(NumberType.Int64, NumberType.Int64, NumberType.Int64), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt64) args[0]).intdiv((KInt64) args[1])
				}
			}
			define 'div', func(NumberType.Int32, NumberType.Int32, NumberType.Int32), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt32) args[0]).intdiv((KInt32) args[1])
				}
			}
			define 'div', func(NumberType.Int16, NumberType.Int16, NumberType.Int16), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt16) args[0]).intdiv((KInt16) args[1])
				}
			}
			define 'div', func(NumberType.Int8, NumberType.Int8, NumberType.Int8), new Function() {
				IKismetObject call(IKismetObject... args) {
					((KInt8) args[0]).intdiv((KInt8) args[1])
				}
			}
			alias 'plus', '+'
			alias 'minus', '-'
			alias 'multiply', '*'
			alias 'divide', '/'
			define 'rem',  funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('mod', [b] as Object[]) } }
			define 'mod',  funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('abs', null).invokeMethod('mod', [b] as Object[]) } }
			define 'pow',  funcc(true) { ... args -> args.inject { a, b -> a.invokeMethod('power', [b] as Object[]) } }
			define 'sum',  funcc(true) { ... args -> args[0].invokeMethod('sum', null) }
			define 'product',  funcc(true) { ... args -> args[0].inject { a, b -> a.invokeMethod('multiply', [b] as Object[]) } }
			define 'reciprocal',  funcc(true) { ... args -> 1.div(args[0] as Number) }
		}
		define 'now_nanos', func(NumberType.Int64), new Function() {
			IKismetObject call(IKismetObject... args) {
				new KInt64(System.nanoTime())
			}
		}
		define 'now_millis', func(NumberType.Int64), new Function() {
			IKismetObject call(IKismetObject... args) {
				new KInt64(System.currentTimeMillis())
			}
		}
		define 'now_seconds', func(NumberType.Int64), new Function() {
			IKismetObject call(IKismetObject... args) {
				new KInt64(System.currentTimeSeconds())
			}
		}
		define 'parse_date_millis_from_format', func(NumberType.Int64, STRING_TYPE), new Function() {
			@Override
			IKismetObject call(IKismetObject... args) {
				new KInt64(new SimpleDateFormat(args[1].toString()).parse(args[0].toString()).time)
			}
		}
		define 'variable', TYPE_CHECKER_TYPE, new TypeChecker() {
			TypedExpression transform(TypedContext tc, Expression... args) {
				new TypedConstantExpression(Type.ANY, new WrapperKismetObject(tc.find(toAtom(args[0]))))
			}

			IKismetObject call(Context c, Expression... args) {
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

			IKismetObject call(Context c, Expression... args) {
				new WrapperKismetObject(c.variables)
			}
		}
		define 'current_context', TYPE_CHECKER_TYPE, new TypeChecker() {
			TypedExpression transform(TypedContext context, Expression... args) {
				new TypedConstantExpression(Type.ANY, new WrapperKismetObject(context))
			}

			IKismetObject call(Context c, Expression... args) {
				new WrapperKismetObject(c)
			}
		}
		define '<=>', func(NumberType.Int32, STRING_TYPE, STRING_TYPE), new Function() {
			IKismetObject call(IKismetObject... a) {
				new KInt32(((KismetString) a[0]).compareTo((KismetString) a[1]))
			}
		}
		/*define 'try',  macr { Context c, Expression... exprs ->
			try {
				exprs[0].evaluate(c)
			} catch (ex) {
				c = c.child()
				c.set(resolveName(exprs[1], c, 'try'), Kismet.model(ex))
				exprs[2].evaluate(c)
			}
		}*/
		define 'raise', func(Type.NONE, STRING_TYPE), new Function() {
			IKismetObject call(IKismetObject... args) {
				throw new KismetException(((KismetString) args[0]).inner())
			}
		}
		define 'raise', func(Type.NONE), new Function() {
			IKismetObject call(IKismetObject... args) {
				throw new KismetException()
			}
		}
		define 'assert', INSTRUCTOR_TYPE, new Instructor() {
			@Override
			IKismetObject call(Memory m, Instruction... args) {
				IKismetObject val = Kismet.NULL
				for (e in args) if (!(val = e.evaluate(m)))
					throw new KismetAssertionError('Assertion failed for instruction ' +
							e + '. Value was ' + val)
				val
			}
		}
		define 'assert_not', INSTRUCTOR_TYPE, new Instructor() {
			@Override
			IKismetObject call(Memory m, Instruction... args) {
				IKismetObject val = Kismet.NULL
				for (e in args) if ((val = e.evaluate(m)))
					throw new KismetAssertionError('Assertion failed for instruction ' +
							e + '. Value was ' + val)
				val
			}
		}
		define 'assert_is', INSTRUCTOR_TYPE, new Instructor() {
			@Override
			IKismetObject call(Memory c, Instruction... exprs) {
				IKismetObject val = exprs[0].evaluate(c), latest
				for (e in exprs.tail()) if (val != (latest = e.evaluate(c)))
					throw new KismetAssertionError('Assertion failed for instruction ' +
							e + '. Value was expected to be ' + val +
							' but was ' + latest)
				val
			}
		}
		define 'assert_isn\'t', INSTRUCTOR_TYPE, new Instructor() {
			@Override
			IKismetObject call(Memory c, Instruction... exprs) {
				def values = [exprs[0].evaluate(c)]
				IKismetObject retard, latest
				for (e in exprs.tail()) if ((retard = values.find((latest = e.evaluate(c)).&equals)))
					throw new KismetAssertionError('Assertion failed for instruction ' +
							e + '. Value was expected NOT to be ' + retard +
							' but was ' + latest)
				new WrapperKismetObject(values)
			}
		}
		define 'List', new GenericType(META_TYPE, LIST_TYPE), LIST_TYPE
		define '.[]', func(Type.ANY, LIST_TYPE, NumberType.Int32), new Function() {
			IKismetObject call(IKismetObject... args) {
				Kismet.model(args[0].inner().invokeMethod('getAt', [args[1].inner()] as Object[]))
			}
		}
		define '.[]', typedTmpl(META_TYPE, META_TYPE, META_TYPE), new TypedTemplate() {
			@Override
			TypedExpression transform(TypedContext context, TypedExpression... args) {
				final base = (SingleType) args[0].instruction.evaluate(context)
				final arg = (Type) args[1].instruction.evaluate(context)
				final typ = new GenericType(base, arg)
				println META_TYPE.generic(typ)
				new TypedConstantExpression<Type>(new GenericType(META_TYPE, typ), typ)
			}
		}
		define 'hash',  funcc { ... a -> a[0].hashCode() }
		define 'percent',  funcc(true) { ... a -> a[0].invokeMethod 'div', 100 }
		define 'to_percent',  funcc(true) { ... a -> a[0].invokeMethod 'multiply', 100 }
		define 'strip_trailing_zeros',  funcc(true) { ... a -> ((BigDecimal) a[0]).stripTrailingZeros() }
		define 'as',  func { IKismetObject... a -> a[0].invokeMethod('as', [a[1].inner()] as Object[]) }
		define 'is?', func(BOOLEAN_TYPE, Type.ANY, Type.ANY), funcc { ... args -> args.inject { a, b -> a == b } }
		negated 'is?', 'isn\'t?'
		define 'same?',  funcc { ... a -> a[0].is(a[1]) }
		negated 'same?', 'not_same?'
		define 'empty?',  funcc { ... a -> a[0].invokeMethod('isEmpty', null) }
		define 'in?',  funcc { ... a -> a[0] in a[1] }
		negated 'in?', 'not_in?'
		/*define '??',  macr { Context c, Expression... exprs ->
			def p = (PathExpression) exprs[0]
			if (null == p.root) {
				new CallExpression([new NameExpression('fn'), new CallExpression([
						new NameExpression('??'), new PathExpression(new NameExpression('$0'),
						p.steps)])]).evaluate(c)
			} else {
				IKismetObject result = p.root.evaluate(c)
				def iter = p.steps.iterator()
				while (result.inner() != null && iter.hasNext()) {
					final b = iter.next()
					result = b.get(c, result)
				}
				result
			}
		}*/
		define 'defined?',  new TypeChecker() {
			TypedExpression transform(TypedContext context, Expression... args) {
				final type = new TypeBound(args.length > 1 ? (Type) args[1].type(context).instruction.evaluate(context) : Type.ANY)
				if (args[0] instanceof SetExpression) {
					def names = new HashSet<String>(args[0].size())
					for (n in args[0].members) {
						def at = toAtom(n)
						if (null == at) throw new UnexpectedSyntaxException('Unknown symbol ' + n)
						names.add(at)
					}
					new TypedConstantExpression(BOOLEAN_TYPE,
							new KismetBoolean(null != context.find(names, type)))
				} else {
					def at = toAtom(args[0])
					if (null == at) throw new UnexpectedSyntaxException('Unknown symbol ' + args[0])
					new TypedConstantExpression(BOOLEAN_TYPE,
							new KismetBoolean(null != context.find(at, type)))
				}
			}

			IKismetObject call(Context c, Expression... exprs) {
				try {
					c.get(resolveName(exprs[0], c, "defined?"))
					KismetBoolean.TRUE
				} catch (UndefinedVariableException ignored) {
					KismetBoolean.FALSE
				}
			}
		}
		negated 'defined?', 'undefined?'
		define 'integer_from_int8_list',  funcc(true) { ... args -> new BigInteger(args[0] as byte[]) }
		define 'integer_to_int8_list',  funcc(true) { ... args -> (args[0] as BigInteger).toByteArray() as List<Byte> }
		define 'new_rng',  funcc { ... args -> args.length > 0 ? new Random(args[0] as long) : new Random() }
		define 'random_int8_list_from_reference',  funcc { ... args ->
			byte[] bytes = args[1] as byte[]
			(args[0] as Random).nextBytes(bytes)
			bytes as List<Byte>
		}
		define 'random_int32',  funcc { ... args ->
			if (args.length == 0) return (args[0] as Random).nextInt()
			int max = (args.length > 2 ? args[2] as int : args[1] as int) + 1
			int min = args.length > 2 ? args[1] as int : 0
			(args[0] as Random).nextInt(max) + min
		}
		define 'random_int64_of_all',  funcc { ... args -> (args[0] as Random).nextLong() }
		define 'random_float32_between_0_and_1',  funcc { ... args -> (args[0] as Random).nextFloat() }
		define 'random_float64_between_0_and_1',  funcc { ... args -> (args[0] as Random).nextDouble() }
		define 'random_bool',  funcc { ... args -> (args[0] as Random).nextBoolean() }
		define 'next_gaussian',  funcc { ... args -> (args[0] as Random).nextGaussian() }
		define 'random_int',  funcc { ... args ->
			BigInteger lower = args.length > 2 ? args[1] as BigInteger : 0g
			BigInteger higher = args.length > 2 ? args[2] as BigInteger : args[1] as BigInteger
			double x = (args[0] as Random).nextDouble()
			lower + (((higher - lower) * (x as BigDecimal)) as BigInteger)
		}
		define 'random_float',  funcc { ... args ->
			BigDecimal lower = args.length > 2 ? args[1] as BigDecimal : 0g
			BigDecimal higher = args.length > 2 ? args[2] as BigDecimal : args[1] as BigDecimal
			double x = (args[0] as Random).nextDouble()
			lower + (higher - lower) * (x as BigDecimal)
		}
		define 'shuffle!',  funcc { ... args ->
			def l = toList(args[0])
			args[1] instanceof Random ? Collections.shuffle(l, (Random) args[1])
					: Collections.shuffle(l)
			l
		}
		define 'shuffle',  funcc { ... args ->
			def l = new ArrayList(toList(args[0]))
			args[1] instanceof Random ? Collections.shuffle(l, (Random) args[1])
					: Collections.shuffle(l)
			l
		}
		define 'sample',  funcc { ... args ->
			def x = toList(args[0])
			Random r = args.length > 1 && args[1] instanceof Random ? (Random) args[1] : new Random()
			x[r.nextInt(x.size())]
		}
		define 'high',  funcc { ... args ->
			if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MAX_VALUE')
			else if (args[0] instanceof Range) ((Range) args[0]).to
			else if (args[0] instanceof Collection) ((Collection) args[0]).size() - 1
			else throw new UnexpectedValueException('Don\'t know how to get high of ' + args[0] + ' with class ' + args[0].class)
		}
		define 'low',  funcc { ... args ->
			if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MIN_VALUE')
			else if (args[0] instanceof Range) ((Range) args[0]).from
			else if (args[0] instanceof Collection) 0
			else throw new UnexpectedValueException('Don\'t know how to get low of ' + args[0] + ' with class ' + args[0].class)
		}
		define 'collect_range_with_step',  funcc { ... args -> (args[0] as Range).step(args[1] as int) }
		define 'each_range_with_step',  func { IKismetObject... args ->
			(args[0].inner() as Range)
					.step(args[1].inner() as int, ((Function) args[2]).toClosure())
		}
		define 'replace',  funcc { ... args ->
			args[0].toString().replace(args[1].toString(),
					args.length > 2 ? args[2].toString() : '')
		}
		define 'replace_all_regex',  func { IKismetObject... args ->
			def replacement = args.length > 2 ?
					(args[2].inner() instanceof String ? args[2].inner() : ((Function) args[2]).toClosure()) : ''
			def str = args[0].inner().toString()
			def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
			str.invokeMethod('replaceAll', [pattern, replacement] as Object[])
		}
		define 'replace_first_regex',  func { IKismetObject... args ->
			def replacement = args.length > 2 ?
					(args[2].inner() instanceof String ? args[2].inner() : ((Function) args[2]).toClosure()) : ''
			def str = args[0].inner().toString()
			def pattern = args[1].inner() instanceof Pattern ? (Pattern) args[1].inner() : args[1].inner().toString()
			str.invokeMethod('replaceFirst', [pattern, replacement] as Object[])
		}
		define 'blank?',  func(true) { IKismetObject... args -> ((String) args[0].inner() ?: "").isAllWhitespace() }
		define 'whitespace?',  func(true) { IKismetObject... args -> Character.isWhitespace((int) args[0].inner()) }
		define 'quote_regex',  func(true) { IKismetObject... args -> Pattern.quote((String) args[0].inner()) }
		define 'codepoints~',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).codePoints().iterator() }
		define 'chars~',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars().iterator() }
		define 'chars',  func { IKismetObject... args -> ((CharSequence) args[0].inner()).chars.toList() }
		define 'codepoint_to_chars',  funcc { ... args -> Character.toChars((int) args[0]).toList() }
		define 'upper',  funcc(true) { ... args ->
			args[0] instanceof Character ? Character.toUpperCase((char) args[0]) :
					args[0] instanceof Integer ? Character.toUpperCase((int) args[0]) :
							((String) args[0]).toString().toUpperCase()
		}
		define 'lower',  funcc(true) { ... args ->
			args[0] instanceof Character ? Character.toLowerCase((char) args[0]) :
					args[0] instanceof Integer ? Character.toLowerCase((int) args[0]) :
							((String) args[0]).toString().toLowerCase()
		}
		define 'upper?',  funcc(true) { ... args ->
			args[0] instanceof Character ? Character.isUpperCase((char) args[0]) :
					args[0] instanceof Integer ? Character.isUpperCase((int) args[0]) :
							((String) args[0]).chars.every { Character it -> !Character.isLowerCase(it) }
		}
		define 'lower?',  funcc(true) { ... args ->
			args[0] instanceof Character ? Character.isLowerCase((char) args[0]) :
					args[0] instanceof Integer ? Character.isLowerCase((int) args[0]) :
							((String) args[0]).chars.every { char it -> !Character.isUpperCase(it) }
		}
		define 'parse_number',  funcc(true) { ... args ->
			new NumberExpression(args[0].toString()).value
		}
		define 'strip',  funcc(true) { ... args -> ((String) args[0]).trim() }
		define 'strip_start',  funcc(true) { ... args ->
			def x = (String) args[0]
			char[] chars = x.chars
			for (int i = 0; i < chars.length; ++i) {
				if (!Character.isWhitespace(chars[i]))
					return x.substring(i)
			}
			''
			/*
			defn [strip_start x] {
			  i: 0
			  while [and [< i [size x]] [whitespace? x[i]]] [incr i]
			}
			*/
		}
		define 'strip_end',  funcc(true) { ... args ->
			def x = (String) args[0]
			char[] chars = x.chars
			for (int i = chars.length - 1; i >= 0; --i) {
				if (!Character.isWhitespace(chars[i]))
					return x.substring(0, i + 1)
			}
			''
		}
		define 'do_regex',  func(true) { IKismetObject... args -> ~(args[0].toString()) }
		define 'regex',  new Template() {
			Expression transform(Parser parser, Expression... args) {
				new CallExpression(new NameExpression('do_regex'), args[0] instanceof StringExpression ?
						new StaticExpression(((StringExpression) args[0]).raw) : args[0])
			}
		}
		/*define 'set_at', TEMPLATE_TYPE, new Template() {
			Expression transform(Parser parser, Expression... args) {
				def path = args[0] instanceof PathExpression ?
					new PathExpression(((PathExpression) args[0]).root, ((PathExpression) args[0]).steps +
						[new PathExpression.SubscriptStep(args[1])]) :
					new PathExpression(args[0], [new PathExpression.SubscriptStep(args[1])])
				new ColonExpression(path, args[2])
			}
		}
		define 'at', TEMPLATE_TYPE, new Template() {
			Expression transform(Parser parser, Expression... args) {
				new PathExpression(args[0], [(PathExpression.Step) new PathExpression.SubscriptStep(args[1])])
			}
		}*/
		define 'string',  func(true) { IKismetObject... a ->
			if (a.length == 1) return a[0].toString()
			StringBuilder x = new StringBuilder()
			for (s in a) x.append(s)
			x.toString()
		}
		define 'int',  func(true) { IKismetObject... a -> a[0] as BigInteger }
		define 'int8',  func(true) { IKismetObject... a -> a[0] as byte }
		define 'int16',  func(true) { IKismetObject... a -> a[0] as short }
		define 'int32',  func(true) { IKismetObject... a -> a[0] as int }
		define 'int64',  func(true) { IKismetObject... a -> a[0] as long }
		define 'char',  func(true) { IKismetObject... a -> a[0] as Character }
		define 'float',  func(true) { IKismetObject... a -> a[0] as BigDecimal }
		define 'float32',  func(true) { IKismetObject... a -> a[0] as float }
		define 'float64',  func(true) { IKismetObject... a -> a[0] as double }
		define 'to_iterator',  funcc { ... args -> toIterator(args[0]) }
		define 'list_iterator',  funcc { ... args -> args[0].invokeMethod('listIterator', null) }
		define 'has_next?',  funcc { ... args -> args[0].invokeMethod('hasNext', null) }
		define 'next',  funcc { ... args -> args[0].invokeMethod('next', null) }
		define 'has_prev?',  funcc { ... args -> args[0].invokeMethod('hasPrevious', null) }
		define 'prev',  funcc { ... args -> args[0].invokeMethod('previous', null) }
		define 'new_list',  funcc { ... args -> new ArrayList(args[0] as int) }
		define 'list',  funcc { ... args -> args.toList() }
		define 'new_set',  funcc { ... args -> args.length > 1 ? new HashSet(args[0] as int, args[1] as float) : new HashSet(args[0] as int) }
		define 'set',  funcc { ... args ->
			Set x = new HashSet()
			for (a in args) x.add(a)
			x
		}
		define 'pair',  funcc { ... args -> new Pair(args[0], args[1]) }
		define 'tuple',  funcc(true) { ... args -> new Tuple(args) }
// assert_is x [+ [bottom_half x] [top_half x]]
		define 'bottom_half', funcc { ... args ->
			args[0] instanceof Number ? ((Number) args[0]).intdiv(2) :
					args[0] instanceof Pair ? ((Pair) args[0]).first :
							args[0] instanceof Collection ? ((Collection) args[0]).take(((Collection) args[0]).size().intdiv(2) as int) :
									args[0] instanceof Map ? ((Map) args[0]).values() :
											args[0]
		}
		define 'top_half',  funcc { ... args ->
			args[0] instanceof Number ? ((Number) args[0]).minus(((Number) args[0]).intdiv(2)) :
					args[0] instanceof Pair ? ((Pair) args[0]).second :
							args[0] instanceof Collection ? ((Collection) args[0]).takeRight(
									((Collection) args[0]).size().minus(((Collection) args[0]).size().intdiv(2)) as int) :
									args[0] instanceof Map ? ((Map) args[0]).keySet() :
											args[0]
		}
		define 'to_list',  funcc { ... args -> toList(args[0]) }
		define 'to_set',  funcc { ... args -> toSet(args[0]) }
		define 'to_pair',  funcc { ... args -> new Pair(args[0].invokeMethod('getAt', 0), args[0].invokeMethod('getAt', 1)) }
		define 'to_tuple',  funcc(true) { ... args -> new Tuple(args[0] as Object[]) }
		define 'entry_pairs',  funcc { ... args ->
			def r = []
			for (x in (args[0] as Map)) r.add(new Pair(x.key, x.value))
			r
		}
		define 'map_from_pairs',  funcc { ... args ->
			def m = new HashMap()
			for (x in args[0]) {
				def p = x as Pair
				m.put(p.first, p.second)
			}
			m
		}
		/*define '##',  macr { Context c, Expression... args ->
			final map = new HashMap()
			for (e in args) expressiveMap(map, c, e)
			map
		}*/
		define 'uncons',  funcc { ... args -> new Pair(args[0].invokeMethod('head', null), args[0].invokeMethod('tail', null)) }
		define 'cons',  funcc { ... args ->
			def y = args[1]
			def a = new ArrayList((y.invokeMethod('size', null) as int) + 1)
			a.add(args[0])
			a.addAll(y)
			a
		}
		define 'intersperse',  funcc { ... args ->
			def r = []
			boolean x = false
			for (a in args[0]) {
				if (x) r.add(args[1])
				else x = true
				r.add(a)
			}
			r
		}
		define 'intersperse_all',  funcc { ... args ->
			def r = []
			boolean x = false
			for (a in args[0]) {
				if (x) r.addAll(args[1])
				else x = true
				r.add(a)
			}
			r
		}
		define 'memoize',  func { IKismetObject... args ->
			def x = args[1]
			Map<IKismetObject[], IKismetObject> results = new HashMap<>()
			func { IKismetObject... a ->
				def p = results.get(a)
				null == p ? ((Function) x).call(a) : p
			}
		}
		define 'escape',  funcc { ... args -> StringEscaper.escape(args[0].toString()) }
		define 'unescape',  funcc { ... args -> StringEscaper.escape(args[0].toString()) }
		define 'copy_map',  funcc { ... args -> new HashMap(args[0] as Map) }
		define 'new_map',  funcc { ... args -> args.length > 1 ? new HashMap(args[0] as int, args[1] as float) :
				args.length == 1 ? new HashMap(args[0] as int) : new HashMap() }
		define 'zip',  funcc { ... args -> args.toList().transpose() }
		define 'knit',  func { IKismetObject... args ->
			toList(args[0].inner()).transpose()
					.collect { args[1].invokeMethod('call', it as Object[]) }
		}
		define 'transpose',  funcc { ... args -> toList(args[0]).transpose() }
		define 'unique?',  funcc { ... args ->
			args[0].invokeMethod('size', null) ==
					args[0].invokeMethod('unique', false).invokeMethod('size', null)
		}
		define 'unique!',  funcc { ... args -> args[0].invokeMethod('unique', null) }
		define 'unique',  funcc { ... args -> args[0].invokeMethod('unique', false) }
		define 'unique_via?',  func { IKismetObject... args ->
			args[0].inner().invokeMethod('size', null) ==
					args[0].inner().invokeMethod('unique', [false, ((Function) args[1]).toClosure()] as Object[])
		}
		define 'unique_via!',  func { IKismetObject... args -> args[0].inner().invokeMethod('unique', ((Function) args[1]).toClosure()) }
		define 'unique_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('unique', [false, ((Function) args[1]).toClosure()]) }
		define 'spread_map',  funcc { ... args -> args[0].invokeMethod('toSpreadMap', null) }
		define 'spread',  new Template() {
			Expression transform(Parser parser, Expression... args) {
				def m = new ArrayList<Expression>(args.length - 1)
				for (int i = 1; i < args.length; ++i) m.add(args[i])
				new CallExpression(args[0], new ListExpression(m))
			}
		}
		define 'invert_map',  funcc { ... args ->
			final m0 = args[0] as Map
			def m = new HashMap(m0.size())
			for (final e : m0.entrySet()) {
				m.put(e.value, e.key)
			}
			m
		}
		define 'new_json_parser',  funcc { ... args -> new JsonSlurper() }
		define 'parse_json',  funcc { ... args ->
			String text = args.length > 1 ? args[1].toString() : args[0].toString()
			JsonSlurper sl = args.length > 1 ? args[0] as JsonSlurper : new JsonSlurper()
			sl.parseText(text)
		}
		define 'to_json',  funcc { ... args -> ((Object) JsonOutput).invokeMethod('toJson', [args[0]] as Object[]) }
		define 'pretty_print_json',  funcc { ... args -> JsonOutput.prettyPrint(args[0].toString()) }
		define 'size', func(NumberType.Int32, Type.ANY), funcc { ... a -> a[0].invokeMethod('size', null) }
		define 'keys',  funcc { ... a -> a[0].invokeMethod('keySet', null).invokeMethod('toList', null) }
		define 'values',  funcc { ... a -> a[0].invokeMethod('values', null) }
		define 'reverse',  funcc { ... a -> a[0].invokeMethod('reverse', a[0] instanceof CharSequence ? null : false) }
		define 'reverse!',  funcc { ... a -> a[0].invokeMethod('reverse', null) }
		define 'reverse?',  funcc { ... a -> a[0].invokeMethod('reverse', false) == a[1] }
		define 'sprintf',  funcc { ... args -> String.invokeMethod('format', args) }
		define 'expr_type',  funcc { ... args ->
			args[0] instanceof Expression ?
					(args[0].class.simpleName - 'Expression').uncapitalize() : null
		}
		define 'capitalize',  func { IKismetObject... args -> args[0].toString().capitalize() }
		define 'uncapitalize',  func { IKismetObject... args -> args[0].toString().uncapitalize() }
		define 'center',  funcc { ... args ->
			args.length > 2 ? args[0].toString().center(args[1] as Number, args[2].toString()) :
					args[0].toString().center(args[1] as Number)
		}
		define 'pad_start',  funcc { ... args ->
			args.length > 2 ? args[0].toString().padLeft(args[1] as Number, args[2].toString()) :
					args[0].toString().padLeft(args[1] as Number)
		}
		define 'pad_end',  funcc { ... args ->
			args.length > 2 ? args[0].toString().padRight(args[1] as Number, args[2].toString()) :
					args[0].toString().padRight(args[1] as Number)
		}
		define 'prefix?',  funcc { ... args ->
			if (args[1] instanceof String) ((String) args[1]).startsWith(args[0].toString())
			else Collections.indexOfSubList(toList(args[1]), toList(args[0])) == 0
		}
		define 'suffix?',  funcc { ... args ->
			if (args[1] instanceof String) ((String) args[1]).endsWith(args[0].toString())
			else {
				def a = toList(args[0])
				def b = toList(args[1])
				Collections.lastIndexOfSubList(b, a) == b.size() - a.size()
			}
		}
		define 'infix?',  funcc { ... args ->
			if (args[1] instanceof String) ((String) args[1]).contains(args[0].toString())
			else Collections.invokeMethod('indexOfSubList', [toList(args[1]), toList(args[0])] as Object[]) != -1
		}
		define 'subset?',  funcc { ... args -> args[1].invokeMethod('containsAll', [args[0]] as Object[]) }
		define 'rotate!',  funcc { ... args ->
			List x = (List) args[0]
			Collections.rotate(x, args[1] as int)
			x
		}
		define 'rotate',  funcc { ... args ->
			def x = new ArrayList(toList(args[0]))
			Collections.rotate(x, args[1] as int)
			x
		}
		define 'lines',  funcc { ... args -> args[0].invokeMethod('readLines', null) }
		define 'denormalize',  funcc { ... args -> args[0].toString().denormalize() }
		define 'normalize',  funcc { ... args -> args[0].toString().normalize() }
		define 'to_base',  funcc { ... a -> (a[0] as BigInteger).toString(a[1] as int) }
		define 'from_base',  funcc { ... a -> new BigInteger(a[0].toString(), a[1] as int) }
		define 'each',  func { IKismetObject... args -> args[0].inner().each(((Function) args[1]).toClosure()) }
		define 'each_with_index',  func { IKismetObject... args -> args[0].inner().invokeMethod('eachWithIndex', ((Function) args[1]).toClosure()) }
		define 'collect',  func { IKismetObject... args ->
			args[0].inner().collect(((Function) args[1]).toClosure())
		}
		define 'collect_nested',  func { IKismetObject... args -> args[0].inner().invokeMethod('collectNested', ((Function) args[1]).toClosure()) }
		define 'collect_many',  func { IKismetObject... args -> args[0].inner().invokeMethod('collectMany', ((Function) args[1]).toClosure()) }
		define 'collect_map',  func { IKismetObject... args ->
			args[0].inner()
					.invokeMethod('collectEntries') { ... a -> ((Function) args[1]).call(Kismet.model(a)).inner() }
		}
		define 'subsequences',  funcc { ... args -> args[0].invokeMethod('subsequences', null) }
		define 'combinations',  funcc { ... args -> args[0].invokeMethod('combinations', null) }
		define 'permutations',  funcc { ... args -> args[0].invokeMethod('permutations', null) }
		define 'permutations~',  funcc { ... args ->
			new PermutationGenerator(args[0] instanceof Collection ? (Collection) args[0]
					: args[0] instanceof Iterable ? (Iterable) args[0]
					: args[0] instanceof Iterator ? new IteratorIterable((Iterator) args[0])
					: args[0] as Collection)
		}
		define 'any?',  func { IKismetObject... args ->
			args.length > 1 ? args[0].inner().any { ((Function) args[1]).call(Kismet.model(it)) } : args[0].inner().any()
		}
		define 'every?',  func { IKismetObject... args ->
			args.length > 1 ? args[0].inner().every { ((Function) args[1]).call(Kismet.model(it)) } : args[0].inner().every()
		}
		define 'none?',  func { IKismetObject... args ->
			!(args.length > 1 ? args[0].inner().any { ((Function) args[1]).call(Kismet.model(it)) } : args[0].inner().any())
		}
		define 'find',  func { IKismetObject... args -> args[0].inner().invokeMethod('find', ((Function) args[1]).toClosure()) }
		define 'find_result',  func { IKismetObject... args -> args[0].inner().invokeMethod('findResult', ((Function) args[1]).toClosure()) }
		define 'count',  func { IKismetObject... args -> args[0].inner().invokeMethod('count', ((Function) args[1]).toClosure()) }
		define 'count_element',  func { IKismetObject... args ->
			BigInteger i = 0
			def a = args[1].inner()
			def iter = args[0].iterator()
			while (iter.hasNext()) {
				def x = iter.next()
				if (x instanceof IKismetObject) x = x.inner()
				if (a == x) ++i
			}
			i
		}
		define 'count_elements',  func { IKismetObject... args ->
			BigInteger i = 0
			def c = args.tail()
			def b = new Object[c.length]
			for (int m = 0; m < c.length; ++i) b[m] = c[m].inner()
			boolean j = args.length == 1
			def iter = args[0].iterator()
			outer: while (iter.hasNext()) {
				def x = iter.next()
				if (x instanceof IKismetObject) x = x.inner()
				if (j) ++i
				else for (a in b) if (a == x) {
					++i
					continue outer
				}
			}
			i
		}
		define 'count_by',  func { IKismetObject... args -> args[0].inner().invokeMethod('countBy', ((Function) args[1]).toClosure()) }
		define 'group_by',  func { IKismetObject... args -> args[0].inner().invokeMethod('groupBy', ((Function) args[1]).toClosure()) }
		define 'indexed',  func { IKismetObject... args -> args[0].inner().invokeMethod('indexed', args.length > 1 ? args[1] as int : null) }
		define 'find_all',  func { IKismetObject... args -> args[0].inner().findAll(((Function) args[1]).toClosure()) }
		define 'join',  funcc { ... args ->
			args[0].invokeMethod('join', args.length > 1 ? args[1].toString() : '')
		}
		define 'inject',  func { IKismetObject... args -> args[0].inner().inject { a, b -> ((Function) args[1]).call(Kismet.model(a), Kismet.model(b)) } }
		define 'collate',  funcc { ... args -> args[0].invokeMethod('collate', args.tail()) }
		define 'pop',  funcc { ... args -> args[0].invokeMethod('pop', null) }
		define 'add',  func { IKismetObject... args -> args[0].invokeMethod('add', args[1]) }
		define 'add_at',  funcc { ... args -> args[0].invokeMethod('add', [args[1] as int, args[2]]) }
		define 'add_all',  funcc { ... args -> args[0].invokeMethod('addAll', args[1]) }
		define 'add_all_at',  funcc { ... args -> args[0].invokeMethod('addAll', [args[1] as int, args[2]]) }
		define 'remove',  funcc { ... args -> args[0].invokeMethod('remove', args[1]) }
		define 'remove_elements',  funcc { ... args -> args[0].invokeMethod('removeAll', args[1]) }
		define 'remove_any',  func { IKismetObject... args -> args[0].inner().invokeMethod('removeAll', ((Function) args[1]).toClosure()) }
		define 'remove_element',  funcc { ... args -> args[0].invokeMethod('removeElement', args[1]) }
		define 'get',  funcc { ... a ->
			def r = a[0]
			for (int i = 1; i < a.length; ++i)
				r = r.invokeMethod('get', a[i])
			r
		}
		define 'empty',  funcc { ... args -> args[0].invokeMethod('clear', null) }
		define 'put',  funcc { ... args -> args[0].invokeMethod('put', [args[1], args[2]]) }
		define 'put_all',  funcc { ... args -> args[0].invokeMethod('putAll', args[1]) }
		define 'keep_all!',  funcc { ... args -> args[0].invokeMethod('retainAll', args[1]) }
		define 'keep_any!',  func { IKismetObject... args -> args[0].inner().invokeMethod('retainAll', ((Function) args[1]).toClosure()) }
		define 'has?',  funcc { ... args -> args[0].invokeMethod('contains', args[1]) }
		define 'has_all?',  funcc { ... args -> args[0].invokeMethod('containsAll', args[1]) }
		define 'has_key?',  funcc { ... args -> args[0].invokeMethod('containsKey', args[1]) }
		define 'has_key_traverse?',  funcc { ... args ->
			def x = args[0]
			for (a in args.tail()) {
				if (!((boolean) x.invokeMethod('containsKey', args[1]))) return false
				else x = args[0].invokeMethod('getAt', a)
			}
			true
		}
		define 'has_value?',  funcc { ... args -> args[0].invokeMethod('containsValue', args[1]) }
		define 'disjoint?',  funcc { ... args -> args[0].invokeMethod('disjoint', args[1]) }
		define 'intersect?',  funcc { ... args -> !args[0].invokeMethod('disjoint', args[1]) }
		define 'call',  func { IKismetObject... args ->
			println "om"
			println args
			def x = args[1].inner() as Object[]
			def ar = new IKismetObject[x.length]
			for (int i = 0; i < ar.length; ++i) {
				println x[i].getClass()
				ar[i] = Kismet.model(x[i])
				println ar[i].getClass()
			}
			((Function) args[0]).call(ar)
		}
		define 'range',  funcc { ... args -> args[0]..args[1] }
		define 'parse_independent_kismet',  func { IKismetObject... args -> Kismet.parse(args[0].toString()) }
		define 'sort!',  funcc { ... args -> args[0].invokeMethod('sort', null) }
		define 'sort',  funcc { ... args -> args[0].invokeMethod('sort', false) }
		define 'sort_via!',  func { IKismetObject... args -> args[0].inner().invokeMethod('sort', ((Function) args[1]).toClosure()) }
		define 'sort_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('sort', [false, ((Function) args[1]).toClosure()]) }
		define 'head',  funcc { ... args -> args[0].invokeMethod('head', null) }
		define 'tail',  funcc { ... args -> args[0].invokeMethod('tail', null) }
		define 'init',  funcc { ... args -> args[0].invokeMethod('init', null) }
		define 'last',  funcc { ... args -> args[0].invokeMethod('last', null) }
		define 'first',  funcc { ... args -> args[0].invokeMethod('first', null) }
		define 'immutable',  funcc { ... args -> args[0].invokeMethod('asImmutable', null) }
		define 'identity',  Function.IDENTITY
		define 'flatten',  funcc { ... args -> args[0].invokeMethod('flatten', null) }
		define 'concat_list',  funcc { ... args ->
			def c = new ArrayList()
			for (int i = 0; i < args.length; ++i) {
				final x = args[i]
				x instanceof Collection ? c.addAll(x) : c.add(x)
			}
			c
		}
		define 'concat_set',  funcc { ... args ->
			def c = new HashSet()
			for (int i = 0; i < args.length; ++i) {
				final x = args[i]
				x instanceof Collection ? c.addAll(x) : c.add(x)
			}
			c
		}
		define 'concat_tuple',  funcc { ... args ->
			def c = new ArrayList()
			for (int i = 0; i < args.length; ++i) {
				final x = args[i]
				x instanceof Collection ? c.addAll(x) : c.add(x)
			}
			new Tuple(c.toArray())
		}
		define 'indices',  funcc { ... args -> args[0].invokeMethod('getIndices', null) }
		define 'find_index',  func { IKismetObject... args -> args[0].inner().invokeMethod('findIndexOf', ((Function) args[1]).toClosure()) }
		define 'find_index_after',  func { IKismetObject... args ->
			args[0].inner()
					.invokeMethod('findIndexOf', [args[1] as int, ((Function) args[2]).toClosure()])
		}
		define 'find_last_index',  func { IKismetObject... args -> args[0].inner().invokeMethod('findLastIndexOf', ((Function) args[1]).toClosure()) }
		define 'find_last_index_after',  func { IKismetObject... args ->
			args[0].inner()
					.invokeMethod('findLastIndexOf', [args[1] as int, ((Function) args[2]).toClosure()])
		}
		define 'find_indices',  func { IKismetObject... args -> args[0].inner().invokeMethod('findIndexValues', ((Function) args[1]).toClosure()) }
		define 'find_indices_after',  func { IKismetObject... args ->
			args[0].inner()
					.invokeMethod('findIndexValues', [args[1] as int, ((Function) args[2]).toClosure()])
		}
		define 'intersect',  funcc { ... args -> args[0].invokeMethod('intersect', args[1]) }
		define 'split',  funcc { ... args -> args[0].invokeMethod('split', args.tail()) as List }
		define 'tokenize',  funcc { ... args -> args[0].invokeMethod('tokenize', args.tail()) }
		define 'partition',  func { IKismetObject... args -> args[0].inner().invokeMethod('split', ((Function) args[1]).toClosure()) }
		define 'each_consecutive',  func { IKismetObject... args ->
			def x = args[0].inner()
			int siz = x.invokeMethod('size', null) as int
			int con = args[1].inner() as int
			Closure fun = ((Function) args[2]).toClosure()
			def b = []
			for (int i = 0; i <= siz - con; ++i) {
				def a = new Object[con]
				for (int j = 0; j < con; ++j) a[j] = x.invokeMethod('getAt', i + j)
				fun.invokeMethod('call', a)
				b.add(a)
			}
			b
		}
		define 'consecutives',  funcc { ... args ->
			def x = args[0]
			int siz = x.invokeMethod('size', null) as int
			int con = args[1] as int
			def b = []
			for (int i = 0; i <= siz - con; ++i) {
				def a = new ArrayList(con)
				for (int j = 0; j < con; ++j) a.add(x.invokeMethod('getAt', i + j))
				b.add(a)
			}
			b
		}
		define 'consecutives~',  funcc { ... args ->
			def x = args[0]
			int siz = x.invokeMethod('size', null) as int
			int con = args[1] as int
			new IteratorIterable<>(new Iterator<List>() {
				int i = 0

				boolean hasNext() { this.i <= siz - con }

				@Override
				List next() {
					def a = new ArrayList(con)
					for (int j = 0; j < con; ++j) a.add(x.invokeMethod('getAt', this.i + j))
					a
				}
			})
		}
		define 'drop',  funcc { ... args -> args[0].invokeMethod('drop', args[1] as int) }
		define 'drop_right',  funcc { ... args -> args[0].invokeMethod('dropRight', args[1] as int) }
		define 'drop_while',  func { IKismetObject... args -> args[0].inner().invokeMethod('dropWhile', ((Function) args[1]).toClosure()) }
		define 'take',  funcc { ... args -> args[0].invokeMethod('take', args[1] as int) }
		define 'take_right',  funcc { ... args -> args[0].invokeMethod('takeRight', args[1] as int) }
		define 'take_while',  func { IKismetObject... args -> args[0].inner().invokeMethod('takeWhile', ((Function) args[1]).toClosure()) }
		define 'each_combination',  func { IKismetObject... args -> args[0].inner().invokeMethod('eachCombination', ((Function) args[1]).toClosure()) }
		define 'each_permutation',  func { IKismetObject... args -> args[0].inner().invokeMethod('eachPermutation', ((Function) args[1]).toClosure()) }
		define 'each_key_value',  func { IKismetObject... args ->
			def m = (args[0].inner() as Map)
			for (e in m) {
				((Function) args[1]).call(Kismet.model(e.key), Kismet.model(e.value))
			}
			m
		}
		define 'within_range?',  funcc { ... args -> (args[1] as Range).containsWithinBounds(args[0]) }
		define 'is_range_reverse?',  funcc { ... args -> (args[1] as Range).reverse }
		define 'max',  funcc { ... args -> args.max() }
		define 'min',  funcc { ... args -> args.min() }
		define 'max_in',  funcc { ... args -> args[0].invokeMethod('max', null) }
		define 'min_in',  funcc { ... args -> args[0].invokeMethod('min', null) }
		define 'max_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('max', ((Function) args[1]).toClosure()) }
		define 'min_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('min', ((Function) args[1]).toClosure()) }
		define 'consume',  func { IKismetObject... args -> ((Function) args[1]).call(args[0]) }
		define 'tap',  func { IKismetObject... args -> ((Function) args[1]).call(args[0]); args[0] }
		define 'sleep',  funcc { ... args -> sleep args[0] as long }
		define 'times_do',  func { IKismetObject... args ->
			def n = (Number) args[0].inner()
			def l = new ArrayList(n.intValue())
			for (def i = n.minus(n); i < n; i += 1) l.add(((Function) args[1]).call(NumberType.from(i).instantiate(i)))
			l
		}
		define 'compose',  func { IKismetObject... args ->
			funcc { ... a ->
				def r = args[0]
				for (int i = args.length - 1; i >= 0; --i) {
					r = ((Function) args[i]).call(r)
				}
				r
			}
		}
		define 'number?',  funcc { ... args -> args[0] instanceof Number }
		define 'gcd',  funcc { ... args -> gcd(args[0] as Number, args[1] as Number) }
		define 'lcm',  funcc { ... args -> lcm(args[0] as Number, args[1] as Number) }
		define 'reduce_ratio',  funcc { ... args ->
			Pair pair = args[0] as Pair
			def a = pair.first as Number, b = pair.second as Number
			Number gcd = gcd(a, b)
			(a, b) = [a.intdiv(gcd), b.intdiv(gcd)]
			new Pair(a, b)
		}
		define 'repr_expr',  funcc { ... args -> ((Expression) args[0]).repr() }
		define 'sum_range',  funcc { ... args ->
			Range r = args[0] as Range
			def to = r.to as Number, from = r.from as Number
			Number x = to.minus(from).next()
			x.multiply(from.plus(x)).intdiv(2)
		}
		define 'sum_range_with_step',  funcc { ... args ->
			Range r = args[0] as Range
			Number step = args[1] as Number
			def to = (r.to as Number).next(), from = r.from as Number
			to.minus(from).intdiv(step).multiply(from.plus(to.minus(step))).intdiv(2)
		}
		define 'subsequence?',  funcc { ... args ->
			Iterator a = args[1].iterator()
			Iterator b = args[0].iterator()
			if (!b.hasNext()) return true
			def last = ++b
			while (a.hasNext() && b.hasNext()) {
				if (last == ++a) last = ++b
			}
			b.hasNext()
		}
		define 'supersequence?',  funcc { ... args ->
			Iterator a = args[0].iterator()
			Iterator b = args[1].iterator()
			if (!b.hasNext()) return true
			def last = ++b
			while (a.hasNext() && b.hasNext()) {
				if (last == ++a) last = ++b
			}
			b.hasNext()
		}
		define 'average_time_nanos', instr(NumberType.Float, NumberType.Number, Type.ANY), new Instructor() {
			IKismetObject call(Memory c, Instruction... args) {
				int iterations = args[0].evaluate(c).inner() as int
				long sum = 0, size = 0
				for (int i = 0; i < iterations; ++i) {
					long a = System.nanoTime()
					args[1].evaluate(c)
					long b = System.nanoTime()
					sum += b - a
					--size
				}
				new KFloat(sum / size)
			}
		}
		define 'average_time_millis', instr(NumberType.Float, NumberType.Number, Type.ANY), new Instructor() {
			IKismetObject call(Memory c, Instruction... args) {
				int iterations = args[0].evaluate(c).inner() as int
				long sum = 0, size = 0
				for (int i = 0; i < iterations; ++i) {
					long a = System.currentTimeMillis()
					args[1].evaluate(c)
					long b = System.currentTimeMillis()
					sum += b - a
					--size
				}
				new KFloat(sum / size)
			}
		}
		define 'average_time_seconds', instr(NumberType.Float, NumberType.Number, Type.ANY), new Instructor() {
			IKismetObject call(Memory c, Instruction... args) {
				int iterations = args[0].evaluate(c).inner() as int
				long sum = 0, size = 0
				for (int i = 0; i < iterations; ++i) {
					long a = System.currentTimeSeconds()
					args[1].evaluate(c)
					long b = System.currentTimeSeconds()
					sum += b - a
					--size
				}
				new KFloat(sum / size)
			}
		}
		define 'list_time_nanos', instr(new GenericType(LIST_TYPE, NumberType.Int64),
				NumberType.Number, Type.ANY), new Instructor() {
			@Override
			IKismetObject call(Memory c, Instruction... args) {
				int iterations = ((KismetNumber) args[0].evaluate(c)).intValue()
				def times = new ArrayList<KInt64>(iterations)
				for (int i = 0; i < iterations; ++i) {
					long a = System.nanoTime()
					args[1].evaluate(c)
					long b = System.nanoTime()
					times.add(new KInt64(b - a))
				}
				new WrapperKismetObject(times)
			}
		}
		define 'list_time_millis', instr(new GenericType(LIST_TYPE, NumberType.Int64),
				NumberType.Number, Type.ANY), new Instructor() {
			@Override
			IKismetObject call(Memory c, Instruction... args) {
				int iterations = args[0].evaluate(c).inner() as int
				def times = new ArrayList<KInt64>(iterations)
				for (int i = 0; i < iterations; ++i) {
					long a = System.currentTimeMillis()
					args[1].evaluate(c)
					long b = System.currentTimeMillis()
					times.add(new KInt64(b - a))
				}
				new WrapperKismetObject(times)
			}
		}
		define 'list_time_seconds', instr(new GenericType(LIST_TYPE, NumberType.Int64),
				NumberType.Number, Type.ANY), new Instructor() {
			@Override
			IKismetObject call(Memory c, Instruction... args) {
				int iterations = args[0].evaluate(c).inner() as int
				def times = new ArrayList<KInt64>(iterations)
				for (int i = 0; i < iterations; ++i) {
					long a = System.currentTimeSeconds()
					args[1].evaluate(c)
					long b = System.currentTimeSeconds()
					times.add(new KInt64(b - a))
				}
				new WrapperKismetObject(times)
			}
		}
		define 'probability', func(BOOLEAN_TYPE, NumberType.Number), new Function() {
			IKismetObject call(IKismetObject... a) {
				Number x = (KismetNumber) a[0]
				new KismetBoolean(new Random().nextDouble() < x)
			}
		}
		alias 'has_all?', 'superset?'
		alias 'inject', 'reduce', 'fold'
		alias 'collect', 'map'
		alias 'bottom_half', 'half'
		alias 'size', 'length'
		alias 'find_all', 'select', 'filter'
		alias 'next', 'succ'
		alias 'every?', 'all?'
		alias 'any?', 'find?', 'some?'
		alias '<', 'less?'
		alias '>', 'greater?'
		alias '<=', 'less_equal?'
		alias '>=', 'greater_equal?'
		alias 'sum', '+/'
		alias 'product', '*/'
		alias 'list', '&'
		alias 'set', '#'
		alias 'tuple', '$'
		alias 'concat_list', '&/'
		alias 'concat_set', '#/'
		alias 'concat_tuple', '$/'
		alias '=', 'assign'
		alias ':=', 'define'
		alias '::=', 'set_to'
		alias ':::=', 'change'
		alias 'defined?', 'variable?'
		alias 'indexed', 'with_index'
		alias 'divisible_by?', 'divides?', 'divs?'
	}

	static GenericType func(Type returnType, Type... args) {
		new GenericType(FUNCTION_TYPE, new TupleType(args), returnType)
	}

	static GenericType instr(Type returnType, Type... args) {
		new GenericType(INSTRUCTOR_TYPE, new TupleType(args), returnType)
	}

	static GenericType typedTmpl(Type returnType, Type... args) {
		new GenericType(TYPED_TEMPLATE_TYPE, new TupleType(args), returnType)
	}

	static String resolveName(Expression n, Context c, String op) {
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

	static Iterator toIterator(x) {
		if (x instanceof Iterable) ((Iterable) x).iterator()
		else if (x instanceof Iterator) (Iterator) x
		else x.iterator()
	}

	static List toList(x) {
		List r
		try {
			r = x as List
		} catch (ex) {
			try {
				r = (List) x.invokeMethod('toList', null)
			} catch (ignore) {
				throw ex
			}
		}
		r
	}

	static Set toSet(x) {
		Set r
		try {
			r = x as Set
		} catch (ex) {
			try {
				r = (Set) x.invokeMethod('toSet', null)
			} catch (ignore) {
				throw ex
			}
		}
		r
	}

	static CallExpression pipeForwardExpr(Expression base, Collection<Expression> args) {
		if (args.empty) throw new UnexpectedSyntaxException('no |> for epic!')
		for (exp in args) {
			if (exp instanceof CallExpression) {
				Collection<Expression> exprs = new ArrayList<>()
				exprs.add(((CallExpression) exp).callValue)
				exprs.add(base)
				exprs.addAll(((CallExpression) exp).arguments)
				def ex = new CallExpression(exprs)
				base = ex
			} else if (exp instanceof BlockExpression) {
				base = pipeForwardExpr(base, ((BlockExpression) exp).members)
			} else if (exp instanceof NameExpression) {
				base = new CallExpression([exp, base])
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
				def ex = new CallExpression(exprs)
				base = ex
			} else if (exp instanceof BlockExpression) {
				base = pipeBackwardExpr(base, ((BlockExpression) exp).members)
			} else if (exp instanceof NameExpression) {
				base = new CallExpression([exp, base])
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
			new BlockExpression(result)
		} else expr
	}

	private static final NameExpression INFIX_CALLS_LTR_PATH = new NameExpression('infix')

	static Expression infixLTR(Collection<Expression> args) {
		if (args.empty) return NoExpression.INSTANCE
		else if (args.size() == 1) return infixLTR(args[0])
		else if (args.size() == 2) {
			if (INFIX_CALLS_LTR_PATH == args[0]) return args[1]
			final val = infixLTR(args[0])
			def result = new CallExpression((Expression[]) null)
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
		CallExpression result = new CallExpression(
				infixLTR(args[1]),
				infixLTR(args[0]),
				infixLTR(args[2]))
		for (b in calls) {
			def exprs = new ArrayList<>(b.size() + 1)
			int i = 0
			exprs.add(b.get(i++))
			exprs.add(result)
			while (i < b.size()) exprs.add(b.get(i++))
			result = new CallExpression(exprs)
		}
		result
	}

	static void putPathExpression(Context c, Map map, PathExpression path, value) {
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

	static void expressiveMap(Map map, Context c, Expression expr) {
		if (expr instanceof NameExpression) map.put(((NameExpression) expr).text, expr.evaluate(c))
		else if (expr instanceof PathExpression)
			putPathExpression(c, map, (PathExpression) expr, c.eval(expr))
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

	static boolean isAlpha(String string) {
		for (char ch : string.toCharArray()) {
			if (!((ch >= ((char) 'a') && ch <= ((char) 'z')) ||
					(ch >= ((char) 'A') && ch <= ((char) 'Z')) ||
					(ch >= ((char) '0') && ch <= ((char) '9')))) return false
		}
		true
	}

	static boolean check(Context c, IKismetObject val, Expression exp) {
		if (exp instanceof CallExpression) {
			def exprs = new ArrayList<>()
			def valu = exp.callValue
			if (valu instanceof NameExpression) {
				def t = ((NameExpression) valu).text
				exprs.add(new NameExpression(isAlpha(t) ? t + '?' : t))
			} else exprs.add(valu)
			c.set('it', val)
			exprs.add(new NameExpression('it'))
			exprs.addAll(exp.arguments)
			def x = new CallExpression(exprs)
			x.evaluate(c)
		} else if (exp instanceof BlockExpression) {
			boolean result = true
			for (x in exp.members) result = check(c, val, x)
			result
		} else if (exp instanceof NameExpression) {
			c.set('it', val)
			def t = exp.text
			new CallExpression(new NameExpression(isAlpha(t) ? t + '?' : t),
					new NameExpression('it')).evaluate(c)
		} else if (exp instanceof StringExpression) {
			val.inner() == exp.value.inner()
		} else if (exp instanceof NumberExpression) {
			val.inner() == exp.value.inner()
		} else throw new UnexpectedSyntaxException('Did not expect ' + exp.class + ' in check')
	}

	static Number gcd(Number a, Number b) {
		a = a.abs()
		if (b == 0) return a
		b = b.abs()
		while (a % b) (b, a) = [a % b, b]
		b
	}

	static Number lcm(Number a, Number b) {
		a.multiply(b).abs().intdiv(gcd(a, b))
	}

	static GroovyFunction func(boolean pure = false, Closure c) {
		def result = new GroovyFunction(false, c)
		result.pure = pure
		result
	}

	static GroovyFunction funcc(boolean pure = false, Closure c) {
		def result = new GroovyFunction(true, c)
		result.pure = pure
		result
	}

	static String toAtom(Expression expression) {
		if (expression instanceof StringExpression) {
			return ((StringExpression) expression).value
		} else if (expression instanceof NameExpression) {
			return ((NameExpression) expression).text
		}
		null
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
					last = new VariableModifyExpression(type, atom, last)
				else if (name instanceof CallExpression)
					last = new FunctionDefineExpression(args)
				else if (name instanceof PathExpression)
					last = new Optimizer.PathStepSetExpression((PathExpression) name, last)
				else throw new UnexpectedSyntaxException("Cannot perform assignment $type $name, value expression is $last")
			}
			if (last instanceof NameExpression)
				last = new VariableModifyExpression(AssignmentType.SET, ((NameExpression) last).text, NoExpression.INSTANCE)
			last
		}
	}
}








