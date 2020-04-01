import tokenizer

type
  ExprKind* = enum
    ekNone, ekNumber, ekString, ekName
    ekBlock, ekCall, ekSubscript, ekProperty
    ekParenList, ekBrackList, ekCurlyList
  
  ExprObj* = object
    case kind*: ExprKind
    of ekNone: discard
    of ekNumber:
      num*: NumberToken
    of ekString:
      str*: string
    of ekName:
      name*: string
    of ekBlock, ekCall, ekParenList, ekBrackList, ekCurlyList:
      exprs*: seq[ref ExprObj]
    of ekSubscript, ekProperty:
      left*, right*: seq[ref ExprObj]
  Expr* = ref ExprObj


proc recordOpenBlock*(tokens: seq[Token]): seq[Expr] =
  var i = 0
  while i < tokens.len:
    let t = tokens[i]
