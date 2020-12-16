import strutils

from tokenizer import NumberToken, runes

const commentStart = ";;"

type
  ExpressionKind = enum
    ekNone
    ekBlock, ekCall
    ekList, ekTuple, ekSet, ekMap,
    ekName, ekColon, ekPath
    ekNumber, ekString
  
  PathStepKind = enum
    pskProperty, pskSubscript, pskEnter
  
  PathStep = object
    case kind: PathStepKind
    of pskProperty:
      name: string
    of pskSubscript, pskEnter:
      expr: Expression

  Expression = ref object
    ln, cl: int
    case kind: ExpressionKind
    of ekNone: discard
    of ekBlock..ekMap:
      content: seq[Expression]
    of ekName:
      name: string
    of ekColon:
      left, right: Expression
    of ekPath:
      root: Expression
      steps: seq[PathStep]
    of ekNumber:
      num: NumberToken
      numNeg: bool
    of ekString:
      str: string

  Parser = ref object
    ln, cl: int
  
  ExprBuilderKind = enum
    ebkBracket, ebkParen, ebkCurly,
    ebkBlock, ebkLine,
    ebkNumber, ebkName, ebkPath,
    ebkString, ebkAccent
  
  Delimed = object
    exprs: seq[Expression]
    last: ExprBuilder
    commad: bool
  
  NumberStage = enum
    nsNumber, nsFraction, nsExponent, nsBits, nsNeg

  ExprBuilder = ref object
    parser: Parser
    ln, cl: int
    goBack: bool
    case kind: ExprBuilderKind
    of ebkBracket, ebkParen:
      delimed: Delimed
    of ebkCurly:
      curlyDelimed: Delimed
      curlyFirst, curlyMap: bool
    of ebkBlock:
      blockExprs: seq[Expression]
      blockLast: ExprBuilder
    of ebkLine:
      whitespaced: seq[Expression]
      semicoloned: seq[seq[Expression]]
      lineLast: ExprBuilder
      eagerEnd, ignoreNewline: bool
    of ebkNumber:
      numberStage: NumberStage
      numberStrs: array[NumberStage, string]
      numberNewlyStage, numberFloating: bool
    of ebkName:
      name: string
    of ebkPath:
      pathExpr: Expression 
      pathStepKind: PathStepKind
      pathLast: ExprBuilder
      pathInPropertyQueue: bool
    of ebkString, ebkAccent:
      str: string
      strEscaped: bool
      strQuote: char

proc newParser(): Parser =
  new(result)
  result.ln = 1

using
  parser: Parser
  builder: ExprBuilder

template exists(x): bool = not x.isNil

proc newBuilder(parser; kind: ExprBuilderKind): ExprBuilder =
  new(result)
  if parser.exists:
    result.parser = parser
    result.ln = parser.ln
    result.cl = parser.cl

proc finish(builder): Expression =
  new(result)
  result.ln = builder.ln
  result.cl = builder.cl
  case builder.kind
  of ebkBracket:
    if builder.delimed.last.exists:
      let x = builder.delimed.last.finish()
      if x.exists:
        builder.delimed.exprs.add(x)
      builder.delimed.last = nil
    let len = builder.delimed.exprs.len
    if len == 0:
      discard
  else: discard

const NonIdentifier = Whitespace + {'.', '[', ']', '(', ')', '{', '}', ',', ':', ';'}

proc recordName(str: string, i: var int): Expression =
  new result
  result.kind = ekName
  while i < str.len:
    let ch = str[i]
    inc i
    if ch in NonIdentifier:
      return
    else:
      result.name.add(ch)

proc recordNumber(str: string, i: var int): Expression =
  result = Expression(kind: ekNumber, num: tokenizer.recordNumber(str, i))
  if str[i] == 'n':
    result.numNeg = true
    inc i

proc recordLine(str: string, i: var int): Expression

proc recordLine(str: string, i: var int): Expression =
  result = Expression(kind: ekCall)
  defer:
    if result.content.len == 1:
      result = result.content[0]
  while i < str.len:
    let ch = str[i]
    case ch
    of '\c', '\L':
      return
    of Whitespace - {'\c', '\L'}: discard
    of '0'..'9':
      result.content.add(recordNumber(str, i))
    of '[':
      result.content.add(recordCall)
  

proc recordOpenBlock(str: string, i: var int): Expression =
  new(result)
  result.kind = ekBlock
  for ch in runes(i, str):
    result.content.add(recordLine(str, i))

proc parse(str: string): Expression =
  var i = 0
  result = recordOpenBlock(str, i)