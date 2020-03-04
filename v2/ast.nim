import tokenizer

type
  ExprKind = enum
    ekNumber, ekString, ekName
    ekPrefix, ekInfix, ekCall
    ekSubscript
    ekBrackList, ekCurlyList 