type
  OperatorKind* = enum
    okNone, okPrefix, okInfix, okPostfix

  OperatorGroupObj* {.acyclic.} = object
    name*: string
    precedence*: int
    kind*: OperatorKind
    symbols*: seq[string]
    next*: ref OperatorGroupObj
  OperatorGroup* = ref OperatorGroupObj

template `<`*(a, b: OperatorGroup | OperatorGroupObj): bool =
  a.precedence < b.precedence

proc newFirstOperatorGroup*(): OperatorGroup =
  new(result)

template sandwich(node: OperatorGroup) =
  new(result)
  result[] = node[]
  node.next = result

proc insertAfterLast*(node: OperatorGroup): OperatorGroup =
  ## simpler proc for when node.next is known to be nil 
  sandwich(node)

proc insertAfter*(node: OperatorGroup): OperatorGroup =
  sandwich(node)
  var n = result
  while not n.isNil:
    inc n.precedence
    n = n.next

proc insertReplace*(node: OperatorGroup) =
  var newNode: OperatorGroup
  new(newNode)
  newNode[] = node[]
  reset(node[])
  node.next = newNode