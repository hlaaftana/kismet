import tokenizer

type
  ExprKind* = enum
    ekNone, ekNumber, ekString, ekName
    ekBlock, ekCall
    ekSubscript, ekProperty, ekColon, ekDotCurly
    ekParenList, ekBrackList, ekCurlyList
  
  ExprObj* = object
    wrapped*: bool
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

template error(s: string) = raise newException(Exception, s)

proc `$`*(ex: Expr | ExprObj): string =
  result.add(($ex.kind)[2..^1])
  case ex.kind
  of ekNone: discard
  of ekNumber: result.add "(" & $ex.num & ")"
  of ekString:
    result.add '('
    for c in ex.str:
      result.addEscapedChar(c)
    result.add ')'
  of ekName: result.add "(" & ex.name & ")"
  of ekBlock, ekCall, ekParenList, ekBrackList, ekCurlyList:
    result.add '('
    for i, e in ex.exprs:
      if i != 0: result.add(", ")
      result.add($e)
    result.add ')'
  of ekSubscript, ekProperty, ekColon, ekDotCurly:
    result.add "(" & $ex.left & ", " & $ex.right & ")"

from strutils import replace

proc repr*(ex: Expr | ExprObj): string =
  case ex.kind:
  of ekNone: result = "()"
  of ekNumber: result = $ex.num
  of ekString: result = "\"" & ex.str & "\""
  of ekName: result = ex.name
  of ekBlock:
    result = "{\n"
    for e in ex.exprs:
      result.add("  " & repr(e).replace("\n", "\n  ") & "\n")
    result.add("}")
  of ekCall:
    result = "["
    for i, e in ex.exprs:
      if i != 0: result.add(' ')
      result.add(repr(e))
    result.add("]")
  of ekParenList, ekBrackList, ekCurlyList:
    const chs: array[ekParenList..ekCurlyList, tuple[st, en: char]] = [('(', ')'), ('[', ']'), ('{', '}')]
    result.add(chs[ex.kind].st)
    for i, e in ex.exprs:
      if i != 0: result.add(", ")
      result.add(repr(e))
    result.add(chs[ex.kind].en)
  of ekSubscript:
    result = repr(ex.left) & "[" & repr(ex.right) & "]"
  of ekProperty:
    result = repr(ex.left) & "." & repr(ex.right)
  of ekColon:
    result = repr(ex.left) & ": " & repr(ex.right)
  of ekDotCurly:
    result = repr(ex.left) & ".(curly)" & repr(ex.right)

type Parser* = object
  tokens*: seq[Token]
  pos*: int

iterator nextTokens*(p: var Parser): Token =
  while p.pos < p.tokens.len:
    yield p.tokens[p.pos]
    inc p.pos

proc recordLine*(p: var Parser, ignoreNewline = false): Expr

proc recordWideLine*(p: var Parser, ignoreNewline = true, open = false): Expr =
  var s: seq[Expr]
  var semicoloned = false
  for t in p.nextTokens:
    case t.kind
    of tkSemicolon:
      semicoloned = true
      #inc i
      #s.add(recordLine(p, true))
    of tkComma, tkCloseParen, tkCloseBrack, tkCloseCurly:
      dec p.pos
      break
    of tkNewline:
      if not ignoreNewline:
        dec p.pos
        break
    else:
      s.add(recordLine(p, ignoreNewline))
  if semicoloned:
    Expr(kind: ekBlock, exprs: s)
  elif s.len == 0: Expr(kind: ekNone)
  else: s[0]

#[template checkSequence(ar: openarray[TokenKind]): bool =
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
    all)]#

proc recordName*(p: var Parser): Expr =
  var text: string
  let initPos = p.pos
  for t in p.nextTokens:
    if t.kind in {tkWord, tkSymbol, tkBackslash} and (p.pos == initPos or not t.quoted):
      text.add(t.raw)
    else:
      dec p.pos
      break
  Expr(kind: ekName, name: text)

proc tokenToExpr*(token: Token): Expr =
  case token.kind
  of tkNumber: result = Expr(kind: ekNumber, num: token.num)
  of tkString: result = Expr(kind: ekString, str: token.content)
  else: result = nil

#[tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewLine
      tkBackslash, tkDot, tkComma, tkColon, tkSemicolon
      tkOpenParen, tkCloseParen, tkOpenBrack, tkCloseBrack, tkOpenCurly, tkCloseCurly
      tkString, tkNumber, tkWord, tkSymbol]#

proc recordParen*(p: var Parser): Expr =
  var commad = false
  var s: seq[Expr]
  for t in p.nextTokens:
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma: commad = true
    of tkCloseParen:
      #inc i
      break
    of tkCloseCurly, tkCloseBrack:
      error "error wwrong token"
    else:
      s.add(recordWideLine(p))
  if commad:
    Expr(kind: ekParenList, exprs: s, wrapped: true)
  elif s.len == 0: Expr(kind: ekParenList, exprs: @[], wrapped: true)
  else:
    s[0].wrapped = true
    s[0]

proc recordBrack*(p: var Parser): Expr =
  var commad = false
  var s: seq[Expr]
  for t in p.nextTokens:
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma: commad = true
    of tkCloseBrack:
      #inc i
      break
    of tkCloseCurly, tkCloseParen:
      error "error wwrong token for brack"
    else:
      s.add(recordWideLine(p))
  if commad:
    Expr(kind: ekBrackList, exprs: s, wrapped: true)
  elif s.len == 0: Expr(kind: ekBrackList, exprs: @[], wrapped: true)
  elif s[0].kind == ekCall and not s[0].wrapped: s[0]
  else: Expr(kind: ekCall, exprs: @[s[0]], wrapped: true)

proc recordCurly*(p: var Parser): Expr =
  var commad = false
  var s: seq[Expr]
  for t in p.nextTokens:
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma: commad = true
    of tkCloseCurly:
      #inc p.pos
      break
    of tkCloseParen, tkCloseBrack:
      error "error wwrong token for curly"
    else:
      s.add(recordWideLine(p, commad))
  if commad:
    Expr(kind: ekCurlyList, exprs: s, wrapped: true)
  else: Expr(kind: ekBlock, exprs: s, wrapped: true)

proc recordDot*(p: var Parser, previous: Expr): Expr =
  template finish(val: Expr, k = ekProperty) =
    return Expr(kind: k, left: previous, right: val)
  for t in p.nextTokens:
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkWord, tkSymbol, tkBackslash:
      finish(recordName(p))
    of tkOpenParen:
      inc p.pos
      let p = recordParen(p)
      var exprs = @[previous]
      if p.kind == ekParenList:
        exprs.add(p.exprs)
      else:
        exprs.add(p)
      return Expr(kind: ekCall, exprs: exprs, wrapped: true)
    of tkOpenBrack:
      inc p.pos
      var rec = recordBrack(p)
      case rec.kind
      of ekCall:
        rec = if rec.exprs.len > 1: rec else: rec.exprs[0]
        rec.wrapped = true
      of ekBrackList: rec = Expr(kind: ekParenList, exprs: rec.exprs, wrapped: true)
      else: discard
      finish(rec, ekSubscript)
    of tkOpenCurly:
      inc p.pos
      finish(recordCurly(p), ekDotCurly)
    of tkNumber, tkString: finish(tokenToExpr(t))
    else: error "error invalid token after dot"
  error "error no valid token after dot"

proc recordSingle*(p: var Parser): Expr =
  for t in p.nextTokens:
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkWord, tkSymbol, tkBackslash: return recordName(p)
    of tkNumber, tkString: return tokenToExpr(t)
    of tkOpenParen: inc p.pos; return recordParen(p)
    of tkOpenBrack: inc p.pos; return recordBrack(p)
    of tkOpenCurly: inc p.pos; return recordCurly(p)
    of tkDot: return recordDot(p, nil)
    of tkColon: inc p.pos; return Expr(kind: ekColon, left: nil, right: recordLine(p, false))
    of tkComma, tkSemicolon,
      tkCloseParen, tkCloseBrack, tkCloseCurly:
      dec p.pos
      return nil

proc recordLine*(p: var Parser, ignoreNewline = false): Expr =
  var s: seq[Expr]
  for t in p.nextTokens:
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack: discard
    of tkNewline:
      if not ignoreNewline:
        dec p.pos
        break
    of tkComma, tkSemicolon, tkCloseParen, tkCloseBrack, tkCloseCurly:
      dec p.pos
      break
    of tkOpenParen, tkOpenBrack, tkOpenCurly:
      if s.len > 0 and p.pos > 0 and p.tokens[p.pos - 1].kind notin
        {tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline, tkCloseParen}:
        s.add(recordDot(p, s.pop))
      else:
        s.add(recordSingle(p))
    of tkDot:
      inc p.pos
      s.add(recordDot(p, if s.len > 0: s.pop else: nil))
    of tkColon:
      inc p.pos
      s.add(Expr(kind: ekColon, left: if s.len > 0: s.pop else: nil, right: recordLine(p, false)#[recordSingle(tokens, i)]#))
    else:
      s.add(recordSingle(p))
  case s.len
  of 0:
    Expr(kind: ekNone)
  of 1:
    s[0]
  else:
    Expr(kind: ekCall, exprs: s)

proc recordOpenBlock*(p: var Parser): Expr =
  var s: seq[Expr]
  for t in p.nextTokens:
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma, tkCloseCurly, tkCloseParen, tkCloseBrack:
      error "wrong tokens at: " & $p.pos
    else:
      s.add(recordWideLine(p, ignoreNewline = false, open = true))
  if s.len == 1: s[0]
  else: Expr(kind: ekBlock, exprs: s)

proc recordOpenBlock*(tokens: seq[Token]): Expr =
  var parser = Parser(tokens: tokens, pos: 0)
  recordOpenBlock(parser)

when isMainModule:
  import os, strutils

  for (k, p) in walkDir("../examples"):
    if k == pcFile and p.endsWith(".ksmt"):
      echo "file: ", p
      let t = tokenize(readFile(p))
      echo "tokens: ", t
      let e = recordOpenBlock(t)
      echo "expr: ", repr e
