import tokenizer

type
  ExprKind = enum
    ekNumber, ekString, ekName
    ekCall, ekSubscript
    ekParenList, ekBrackList, ekCurlyList