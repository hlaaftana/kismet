# TODO:
# make colons parse a single expression instead of line (unlike regular kismet)
# make a actual new syntax

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

proc recordLine*(tokens: seq[Token], i: var int, ignoreNewline = false): Expr

proc recordWideLine*(tokens: seq[Token], i: var int, ignoreNewline = true): Expr =
  var s: seq[Expr]
  var semicoloned = false
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkSemicolon:
      semicoloned = true
      #inc i
      #s.add(recordLine(tokens, i, true))
    of tkComma, tkCloseParen, tkCloseBrack, tkCloseCurly:
      dec i
      break
    of tkNewline:
      if not ignoreNewline:
        dec i
        break
    else:
      s.add(recordLine(tokens, i, ignoreNewline))
    inc i
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

proc recordName*(tokens: seq[Token], i: var int): Expr =
  var text: string
  let initI = i
  while i < tokens.len:
    let t = tokens[i]
    if t.kind in {tkWord, tkSymbol, tkBackslash} and (i == initI or not t.quoted):
      text.add(t.raw)
    else:
      dec i
      break
    inc i
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

proc recordParen*(tokens: seq[Token], i: var int): Expr =
  var commad = false
  var s: seq[Expr]
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma: commad = true
    of tkCloseParen:
      #inc i
      break
    of tkCloseCurly, tkCloseBrack:
      error "error wwrong token"
    else:
      s.add(recordWideLine(tokens, i))
    inc i
  if commad:
    Expr(kind: ekParenList, exprs: s)
  elif s.len == 0: Expr(kind: ekParenList, exprs: @[])
  else: s[0]

proc recordBrack*(tokens: seq[Token], i: var int): Expr =
  var commad = false
  var s: seq[Expr]
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma: commad = true
    of tkCloseBrack:
      #inc i
      break
    of tkCloseCurly, tkCloseParen:
      error "error wwrong token for brack"
    else:
      s.add(recordWideLine(tokens, i))
    inc i
  if commad:
    Expr(kind: ekBrackList, exprs: s)
  elif s.len == 0: Expr(kind: ekBrackList, exprs: @[])
  elif s[0].kind == ekCall: s[0]
  else: Expr(kind: ekCall, exprs: @[s[0]])

proc recordCurly*(tokens: seq[Token], i: var int): Expr =
  var commad = false
  var s: seq[Expr]
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma: commad = true
    of tkCloseCurly:
      #inc i
      break
    of tkCloseParen, tkCloseBrack:
      error "error wwrong token for curly"
    else:
      s.add(recordWideLine(tokens, i, commad))
    inc i
  if commad:
    Expr(kind: ekCurlyList, exprs: s)
  else: Expr(kind: ekBlock, exprs: s)

proc recordDot*(tokens: seq[Token], i: var int, previous: Expr): Expr =
  template finish(val: Expr, k = ekProperty) =
    return Expr(kind: k, left: previous, right: val)
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkWord, tkSymbol, tkBackslash:
      finish(recordName(tokens, i))
    of tkOpenParen:
      inc i
      let p = recordParen(tokens, i)
      var exprs = @[previous]
      if p.kind == ekParenList:
        exprs.add(p.exprs)
      else:
        exprs.add(p)
      return Expr(kind: ekCall, exprs: exprs)
    of tkOpenBrack:
      inc i
      var rec = recordBrack(tokens, i)
      case rec.kind
      of ekCall: rec = if rec.exprs.len > 1: rec else: rec.exprs[0]
      of ekBrackList: rec = Expr(kind: ekParenList, exprs: rec.exprs)
      else: discard
      finish(rec, ekSubscript)
    of tkOpenCurly:
      inc i
      finish(recordCurly(tokens, i), ekDotCurly)
    of tkNumber, tkString: finish(tokenToExpr(t))
    else: error "error invalid token after dot"
    inc i
  error "error no valid token after dot"

proc recordSingle*(tokens: seq[Token], i: var int): Expr =
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkWord, tkSymbol, tkBackslash: return recordName(tokens, i)
    of tkNumber, tkString: return tokenToExpr(t)
    of tkOpenParen: inc i; return recordParen(tokens, i)
    of tkOpenBrack: inc i; return recordBrack(tokens, i)
    of tkOpenCurly: inc i; return recordCurly(tokens, i)
    of tkDot: return recordDot(tokens, i, nil)
    of tkColon: inc i; return Expr(kind: ekColon, left: nil, right: recordLine(tokens, i, false))
    of tkComma, tkSemicolon,
      tkCloseParen, tkCloseBrack, tkCloseCurly:
      dec i
      return nil
    inc i

proc recordLine*(tokens: seq[Token], i: var int, ignoreNewline = false): Expr =
  var s: seq[Expr]
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack: discard
    of tkNewline:
      if not ignoreNewline:
        dec i
        break
    of tkComma, tkSemicolon, tkCloseParen, tkCloseBrack, tkCloseCurly:
      dec i
      break
    of tkOpenParen, tkOpenBrack, tkOpenCurly:
      if s.len > 0 and i > 0 and tokens[i - 1].kind notin {tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline}:
        s.add(recordDot(tokens, i, s.pop))
      else:
        s.add(recordSingle(tokens, i))
    of tkDot:
      inc i
      s.add(recordDot(tokens, i, if s.len > 0: s.pop else: nil))
    of tkColon:
      inc i
      s.add(Expr(kind: ekColon, left: if s.len > 0: s.pop else: nil, right: recordLine(tokens, i, false)#[recordSingle(tokens, i)]#))
    else:
      s.add(recordSingle(tokens, i))
    inc i
  case s.len
  of 0:
    Expr(kind: ekNone)
  of 1:
    s[0]
  else:
    Expr(kind: ekCall, exprs: s)

proc recordOpenBlock*(tokens: seq[Token]): Expr =
  var s: seq[Expr]
  var i = 0
  while i < tokens.len:
    let t = tokens[i]
    case t.kind
    of tkNone, tkWhitespace, tkIndent, tkIndentBack, tkNewline: discard
    of tkComma, tkCloseCurly, tkCloseParen, tkCloseBrack:
      error "wrong tokens at: " & $i
    else:
      s.add(recordWideLine(tokens, i, false))
    inc i
  if s.len == 1: s[0]
  else: Expr(kind: ekBlock, exprs: s)

when isMainModule:
  import os, strutils

  for (k, p) in walkDir("../examples"):
    if k == pcFile and p.endsWith(".ksmt"):
      echo "file: ", p
      let t = tokenize(readFile(p))
      echo "tokens: ", t
      let e = recordOpenBlock(t)
      echo "expr: ", repr e
