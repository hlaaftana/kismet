import tokenizer

type
  ExprKind* = enum
    Call
    Infix
    Prefix
    Literal
  Expr* = ref object
    case kind: ExprKind
    of Atom: tok: Token
    of List: exprs: seq[Expr]

proc glean*(tokens: seq[Token]): Expr =
  discard