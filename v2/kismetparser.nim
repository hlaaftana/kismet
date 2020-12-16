import tokenizer

type
  ExprKind* = enum
    ekNone, ekNumber, ekString, ekName
    ekBlock, ekCall
    ekSubscript, ekProperty, ekColon, ekDotCurly
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
    of ekSubscript, ekProperty, ekColon, ekDotCurly:
      left*, right*: ref ExprObj
  Expr* = ref ExprObj

proc recordLine*(tokens: seq[Token], i: var int, ignoreNewline = false): Expr

template checkSequence(ar: openarray[TokenKind]): bool =
  i + ar.len < tokens.len and (block:
    var all = true
    for j, x in ar:
      if tokens[i + j].kind != x:
        all = false
        break
    all)

template checkSequence(ar: set[TokenKind], l: int): bool =
  i + l < tokens.len and (block:
    var all = true
    for j in 0..<l:
      if tokens[i + j].kind notin ar:
        all = false
        break
    all)

proc recordName*(tokens: seq[Token], i: var int): Expr =
  var text: string
  while i < tokens.len and (let t = tokens[i];
    t.kind in {tkWord, tkSymbol, tkBackslash} and not t.quoted):
    text.add(t.raw)
    inc i
  Expr(kind: ekName, name: text)

#[tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewLine
      tkBackslash, tkDot, tkComma, tkColon, tkSemicolon
      tkOpenParen, tkCloseParen, tkOpenBrack, tkCloseBrack, tkOpenCurly, tkCloseCurly
      tkString, tkNumber, tkWord, tkSymbol]#

proc recordDot*(tokens: seq[Token], i: var int, previous: Expr): Expr =
  template finish(right: Expr, kind = ekProperty) =
    return Expr(kind: kind, left: previous, right: right)
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkWord, tkSymbol, tkBackslash:
      finish(recordName(tokens, i))
    of tkOpenParen:
      inc i
      return Expr(kind: ekCall, exprs: @[previous] & recordParen(tokens, i))
    of tkOpenBrack:
      inc i
      var rec = recordBrack(tokens, i)
      case rec.kind
      of ekCall: rec = if rec.exprs.len > 1: rec else: rec[0]
      of ekBrackList: rec = Expr(kind: ekParenList, exprs: rec.exprs)
      else: discard
      finish(rec, ekSubscript)
    of tkOpenCurly:
      inc i
      finish(recordCurly(tokens, i), ekDotCurly)

    inc i

proc recordLine*(tokens: seq[Token], i: var int, ignoreNewline = false): Expr =
  # wtf
  var semicoloned = false
  var s: seq[Expr]
  var semicolons: seq[seq[Expr]]
  template add(ex: Expr) =
    s.add(ex)
  template finish() =
    return if semicoloned:
      Expr(kind: ekBlock, )
    else:
      Expr(kind: ekCall, exprs: s)
  template last: Expr =
    if semicoloned:
      if s[0].exprs.len > 0: s[0][^1]
    else:
      s[^1]
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack: discard
    of tkWord, tkSymbol, tkBackslash: add(recordName(tokens, i))
    of tkNumber: add(Expr(kind: ekNumber, num: t.num))
    of tkString: add(Expr(kind: ekString, str: t.content))
    of tkNewline:
      if not ignoreNewline:
        finish()
    of tkComma:
      dec i
      finish()
    of tkSemicolon:
      if semicoloned:
        add(Expr(kind: ekCall, ))
      else:
        semicoloned = true
        let newEx = Expr(kind: ekBlock, exprs: s)
        s = @[newEx]
    of tkDot:
      add(recordDot())
    inc i

proc recordOpenBlock*(tokens: seq[Token]): seq[Expr] =
  var i = 0
  while i < tokens.len:
    let t = tokens[i]
    inc i
