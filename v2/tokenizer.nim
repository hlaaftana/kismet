import strutils, unicode, options

type
  TokenType* = enum
    ttNone, ttWhitespace, ttIndent, ttIndentBack, ttNewLine
    ttBackslash, ttDot, ttComma, ttColon, ttSemicolon
    ttOpenParen, ttCloseParen, ttOpenBrack, ttCloseBrack, ttOpenCurly, ttCloseCurly
    ttString, ttNumber, ttWord, ttSymbol

type
  DecimalFlags* = enum
    dfNegative, dfFloating

  PositiveDecimal* = object
    base*: seq[byte]
    floating*: bool
    exp*: int

  Token* = object
    case kind*: TokenType
    of ttString:
      content*: string
    of ttNumber:
      num*: PositiveDecimal
    of ttWord, ttSymbol:
      raw*: string
    of ttNewLine:
      ampersand*: bool
    else: discard

proc `$`*(number: PositiveDecimal): string =
  result = newStringOfCap(number.base.len + 2)
  for d in number.base:
    result.add(('0'.byte + d).char)
  if number.floating and -number.exp < number.base.len:
    result.insert(".", number.base.len + number.exp)
  else:
    result.add('e')
    result.add($number.exp)

proc `$`*(token: Token): string =
  result = case token.kind
  of ttNone: "<none>"
  of ttWhitespace: " "
  of ttIndent: "<indent>"
  of ttIndentBack: "<indentback>"
  of ttNewLine: "\p"
  of ttBackslash: "\\"
  of ttDot: "."
  of ttComma: ","
  of ttColon: ":"
  of ttSemicolon: ";"
  #of ttQuestionMark: "?"
  of ttOpenParen: "("
  of ttCloseParen: ")"
  of ttOpenBrack: "["
  of ttCloseBrack: "]"
  of ttOpenCurly: "{"
  of ttCloseCurly: "}"
  of ttString: "\"" & token.content & "\""
  of ttNumber: $token.num
  of ttWord, ttSymbol: token.raw

iterator rev[T](a: openArray[T]): T {.inline.} =
  var i = a.len
  while i > 0:
    dec(i)
    yield a[i]

iterator runes*(i: var int, s: string): Rune =
  var
    result: Rune
  while i < len(s):
    fastRuneAt(s, i, result, true)
    yield result

proc contains*(s: set[char], r: Rune): bool {.inline.} =
  r.int32 < 128i32 and r.char in s

proc `==`*(s: char, r: Rune): bool {.inline.} =
  r.int32 < 128i32 and r.char == s

template `==`*(r: Rune, s: char): bool = s == r

proc unescape*(str: string): string =
  result = newStringOfCap(str.len)
  var
    escaped = false
    recordU = false
    uFunc: proc(s: string): int = nil
    u: string
  for c in str:
    if escaped:
      if u.len != 0:
        if recordU:
          if c == '}':
            recordU = false
            result.add($Rune(uFunc(u)))
            u = ""
            uFunc = parseHexInt
          else: u.add(c)
        else:
          case c
          of 'x', 'X': uFunc = parseHexInt
          of 'o', 'O': uFunc = parseOctInt
          of 'd', 'D': uFunc = parseInt
          of 'b', 'B': uFunc = parseBinInt
          of '{': recordU = true
          else:
            recordU = false
            result.add('\\')
            result.add('u')
            result.add(c)
            u = ""
          continue
      else:
        if c == 'u':
          u = newStringOfCap(4)
          continue
        else:
          result.setLen(result.len - 1)
          let ch = case c
            of 't': '\t'
            of '"': '"'
            of '\'': '\''
            of '\\': '\\'
            of 'r': '\r'
            of 'n': '\l'
            of 'f': '\f'
            of 'v': '\v'
            of 'a': '\a'
            of 'b': '\b'
            of 'e': '\e'
            else:
              result.add('\\')
              c
          result.add(ch)
    else: result.add(c)
    escaped = c == '\\'

proc recordString*(str: string, i: var int, quote: char): string =
  var
    escaped = false
    recordU = false
    uLevel = 16
    uNum = 0
    startedU = -1
  while i < str.len:
    let c = str[i]
    if startedU != -1:
      if recordU:
        case c
        of '}':
          recordU = false
          result.add($Rune(uNum))
          uNum = 0
          uLevel = 16
          startedU = -1
        of '_':
          discard
        of '0'..'9':
          uNum = uNum * uLevel + int(c.byte - '0'.byte)
        of 'a'..'f':
          uNum = uNum * uLevel + 10 + int(c.byte - 'a'.byte)
        of 'A'..'F':
          uNum = uNum * uLevel + 10 + int(c.byte - 'A'.byte)
        else:
          dec i
          result.add(str[startedU..i])
          recordU = false
          uNum = 0
          uLevel = 16
          startedU = -1
      else:
        case c
        of 'x', 'X': uLevel = 16
        of 'o', 'O': uLevel = 8
        of 'd', 'D': uLevel = 10
        of 'b', 'B': uLevel = 2
        of '{': recordU = true
        else:
          recordU = false
          startedU = -1
          result.add('\\')
          result.add('u')
          result.add(c)
    elif escaped:
      if c == 'u':
        startedU = i - 1
      else:
        let ch = case c
          of 't': '\t'
          of '"': '"'
          of '\'': '\''
          of '`': '`'
          of '\\': '\\'
          of 'r': '\r'
          of 'n': '\l'
          of 'f': '\f'
          of 'v': '\v'
          of 'a': '\a'
          of 'b': '\b'
          of 'e': '\e'
          else:
            result.add('\\')
            c
        result.add(ch)
      escaped = false
    else:
      if c == quote:
        inc i
        return
      elif c == '\\':
        escaped = true
      else:
        result.add(c)
    inc i

proc recordNumber*(str: string, i: var int): PositiveDecimal =
  type Stage = enum
    inBase, inDecimal, inExpStart, inExp, inExpNeg

  var
    stage = inBase
    lastZeros: Natural = 0
  
  defer:
    if not result.floating:
      if lastZeros < -result.exp:
        result.floating = true
      elif result.exp < 0:
        # remove excessive zeros, ie 10000e-3 is simplified to 10
        result.exp = 0
        result.base.setLen(result.base.len + result.exp)

  while i < str.len:
    let c = str[i]
    case stage
    of inBase:
      case c
      of '0'..'9':
        if c == '0':
          inc lastZeros
        else:
          lastZeros = 0
        result.base.add(c.byte - '0'.byte)
      of '.':
        result.floating = true
        stage = inDecimal
      of 'e', 'E':
        stage = inExpStart
      else:
        return
    of inDecimal:
      case c
      of '0'..'9':
        if c == '0':
          inc lastZeros
        else:
          lastZeros = 0
        result.base.add(c.byte - '0'.byte)
        dec result.exp
      of 'e', 'E':
        stage = inExpStart
      else:
        return
    of inExpStart:
      case c
      of '+':
        stage = inExp
      of '-':
        stage = inExpNeg
      of '0'..'9':
        stage = inExp
        continue
      else:
        dec i
        return
    of inExp, inExpNeg:
      case c
      of 'i', 'I':
        result.floating = false
        return
      of 'f', 'F':
        result.floating = true
        return
      of '0'..'9':
        let val = (c.byte - '0'.byte).int
        result.exp = result.exp * 10 + (if stage == inExpNeg: -val else: val)
      else:
        return
    inc i

proc recordWord*(str: string, i: var int): string =
  for c in runes(i, str):
    if c == Rune('_') or c.isAlpha:
      result.add(c)
    else:
      return

proc recordSymbol*(str: string, i: var int): string =
  for c in runes(i, str):
    if c notin (Whitespace + {'0'..'9', '_', '\'', '"', '`', '#', ';', ':', ',',
        '(', ')', '[', ']', '{', '}'}) and not c.isAlpha:
      result.add(c)
    else:
      return

proc tokenize*(str: string): seq[Token] =
  result = newSeq[Token]()
  var
    #currentLineBars: int
    lastKind: TokenType
    lastIndents: seq[int]
    lastIndentSum, indent: int
    recordingIndent: bool
    comment: bool

  template addToken(t: Token) =
    result.add(t)
    lastKind = t.kind

  template dropLast =
    let l1 = result.len - 1
    result.setLen(l1)
    lastKind = if unlikely(l1 == 0): ttNone else: result[l1 - 1].kind

  template addTokenOf(tt: TokenType) =
    addToken(Token(kind: tt))

  var i = 0
  for c in runes(i, str):
    if comment:
      if c == Rune('\l'):
        comment = false
      else: continue

    let w = c in Whitespace

    if recordingIndent and c notin {'\l', '\r'}:
      if c == '\t'.Rune: inc indent, 4
      elif w: inc indent
      elif c != Rune('#'):
        let diff = indent - lastIndentSum
        if diff < 0:
          var d = -diff # 11
          for indt in lastIndents.rev:
            dec d, indt # -2
            dec lastIndentSum, indt
            addTokenOf(ttIndentBack)
            if d < 0:
              dec lastIndentSum, d
              lastIndents[lastIndents.high] = -d
              addTokenOf(ttIndent)
              break
            lastIndents.setLen(lastIndents.high)
            if d == 0: break
        elif diff > 0:
          lastIndents.add(diff)
          inc lastIndentSum, diff
          addTokenOf(ttIndent)
        indent = 0
        recordingIndent = false

    case c
    of Rune('#'): comment = true
    of Rune(','): addTokenOf(ttComma)
    of Rune(':'): addTokenOf(ttColon)
    of Rune(';'): addTokenOf(ttSemicolon)
    of Rune('.'):
      if lastKind == ttDot:
        dropLast
        addToken(Token(kind: ttSymbol, raw: "." & recordSymbol(str, i)))
      else:
        addTokenOf(ttDot)
    of Rune('\\'): addTokenOf(ttBackslash)
    #of Rune('?'): addTokenOf(ttQuestionMark)
    of Rune('['): addTokenOf(ttOpenBrack)
    of Rune(']'): addTokenOf(ttCloseBrack)
    of Rune('('): addTokenOf(ttOpenParen)
    of Rune(')'): addTokenOf(ttCloseParen)
    of Rune('{'): addTokenOf(ttOpenCurly)
    of Rune('}'): addTokenOf(ttCloseCurly)
    #[of Rune('|'):
      addTokenOf(ttNewLine)
      addTokenOf(ttIndent)
      inc currentLineBars
    of Rune('^'):
      addTokenOf(ttNewLine)
      addTokenOf(ttIndentBack)
      dec currentLineBars]#
    of Rune('&'):
      addToken(Token(kind: ttNewLine, ampersand: true))
    of Rune('\''), Rune('"'), Rune('`'):
      let s = recordString(str, i, c.char)
      if c == Rune('`'):
        addToken(Token(kind: ttSymbol, raw: s))
      else:
        addToken(Token(kind: ttString, content: s))
      when false:
        stringQuote = c
        recorded = ""
        recordedType = ttString
    of Rune('0')..Rune('9'):
      let n = recordNumber(str, i)
      addToken(Token(kind: ttNumber, num: n))
    of Rune('\l'), Rune('\c'):
      if c == Rune('\c') and str.len > i + 1 and str[i + 1] == '\n':
        inc i
      let r = high(result)
      for xi in countdown(r, 0):
        if result[xi].kind == ttWhitespace:
          continue
        elif result[xi].kind == ttNewline and result[xi].ampersand:
          result.del(xi)
        break
      if r == high(result):
        addTokenOf(ttNewLine)
        when false:
          for _ in 1..currentLineBars:
            addTokenOf(ttIndentBack)
          currentLineBars = 0
        recordingIndent = true
    elif w:
      if lastKind != ttWhitespace:
        addTokenOf(ttWhitespace)
    elif c == '_' or c.isAlpha:
      addToken(Token(kind: ttWord, raw: recordWord(str, i)))
    else:
      addToken(Token(kind: ttSymbol, raw: recordSymbol(str, i)))

when isMainModule:
  import times
  template bench*(body) =
    let a = cpuTime()
    for i in 1..10000000: body
    let b = cpuTime()
    echo "took ", b - a

  echo unescape("aaaa\\u{28483} ğpppppp \\t \\ux99 \\ub0101010100 \\\" ")
  echo tokenize("hello: \"aaaa\\u{28483} ğpppppp \\t \\u99 \\ux{99} \\ub{0101010100\\ub{0101010100} a\\\" oo\")()")
