package hlaaftana.kismet.lib

import groovy.transform.CompileStatic
import hlaaftana.kismet.Kismet
import hlaaftana.kismet.call.Function
import hlaaftana.kismet.exceptions.UnexpectedValueException
import hlaaftana.kismet.scope.Context
import hlaaftana.kismet.scope.IteratorIterable
import hlaaftana.kismet.scope.Pair
import hlaaftana.kismet.scope.TypedContext
import hlaaftana.kismet.type.*
import hlaaftana.kismet.vm.IKismetObject
import hlaaftana.kismet.vm.KInt
import hlaaftana.kismet.vm.KInt32
import hlaaftana.kismet.vm.KismetNumber
import hlaaftana.kismet.vm.KismetTuple

import java.util.Collections as JCollections

import static hlaaftana.kismet.lib.Functions.func
import static hlaaftana.kismet.lib.Functions.funcc

@CompileStatic
@SuppressWarnings("ChangeToOperator")
class CollectionsIterators extends LibraryModule {
    static final SingleType LIST_TYPE =
        new SingleType('List', [+Type.ANY] as TypeBound[]),
        SET_TYPE = new SingleType('Set', [+Type.ANY] as TypeBound[]),
        MAP_TYPE = new SingleType('Map', [+Type.ANY, +Type.ANY] as TypeBound[]),
        CLOSURE_ITERATOR_TYPE = new SingleType('ClosureIterator')
    TypedContext typed = new TypedContext("collections")
    Context defaultContext = new Context()

    static Iterator toIterator(x) {
        if (x instanceof Iterable) ((Iterable) x).iterator()
        else if (x instanceof Iterator) (Iterator) x
        else x.iterator()
    }

    static List toList(x) {
        List r
        try {
            r = x as List
        } catch (ex) {
            try {
                r = (List) x.invokeMethod('toList', null)
            } catch (ignore) {
                throw ex
            }
        }
        r
    }

    static Set toSet(x) {
        Set r
        try {
            r = x as Set
        } catch (ex) {
            try {
                r = (Set) x.invokeMethod('toSet', null)
            } catch (ignore) {
                throw ex
            }
        }
        r
    }

    CollectionsIterators() {
        define LIST_TYPE
        define SET_TYPE
        define MAP_TYPE
        define TupleType.BASE
        define CLOSURE_ITERATOR_TYPE
        define '.[]', func(Type.ANY, LIST_TYPE, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((List) args[0].inner()).get(((KInt32) args[1]).inner))
            }
        }
        define '.[]', func(Type.ANY, LIST_TYPE, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((List) args[0].inner()).get(((KInt) args[1]).intValue()))
            }
        }
        define '.[]', func(Type.ANY, TupleType.BASE, NumberType.Int32), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((KismetTuple) args[0]).get(((KInt32) args[1]).inner))
            }
        }
        define '.[]', func(Type.ANY, TupleType.BASE, NumberType.Int), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((KismetTuple) args[0]).get(((KInt) args[1]).intValue()))
            }
        }
        define '.[]', func(Type.ANY, MAP_TYPE, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((Map) args[0].inner()).get(args[1].inner()))
            }
        }
        define '.[]=', func(Type.ANY, LIST_TYPE, NumberType.Int32, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((List) args[0].inner()).putAt(((KInt32) args[1]).inner, args[2].inner()))
            }
        }
        define '.[]=', func(Type.ANY, LIST_TYPE, NumberType.Int, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((List) args[0].inner()).putAt(((KInt) args[1]).intValue(), args[2].inner()))
            }
        }
        define '.[]=', func(Type.ANY, TupleType.BASE, NumberType.Int32, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((KismetTuple) args[0]).set(((KInt32) args[1]).inner, args[2]))
            }
        }
        define '.[]=', func(Type.ANY, TupleType.BASE, NumberType.Int, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((KismetTuple) args[0]).set(((KInt) args[1]).intValue(), args[2]))
            }
        }
        define '.[]=', func(Type.ANY, MAP_TYPE, Type.ANY, Type.ANY), new Function() {
            IKismetObject call(IKismetObject... args) {
                Kismet.model(((Map) args[0].inner()).putAt(args[1].inner(), args[2].inner()))
            }
        }
        define 'in?',  funcc { ... a -> a[0] in a[1] }
        negated 'in?', 'not_in?'
        define 'immutable',  funcc { ... args -> args[0].invokeMethod('asImmutable', null) }
        define 'size', func(NumberType.Int32, LIST_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetNumber.from(((List) args[0].inner()).size())
            }
        }
        define 'size', func(NumberType.Int32, TupleType.BASE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetNumber.from(((KismetTuple) args[0]).size())
            }
        }
        define 'size', func(NumberType.Int32, MAP_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetNumber.from(((Map) args[0].inner()).size())
            }
        }
        define 'size', func(NumberType.Int32, SET_TYPE), new Function() {
            @Override
            IKismetObject call(IKismetObject... args) {
                KismetNumber.from(((Set) args[0].inner()).size())
            }
        }
        define 'shuffle!',  funcc { ... args ->
            def l = toList(args[0])
            args[1] instanceof Random ? JCollections.shuffle(l, (Random) args[1])
                    : JCollections.shuffle(l)
            l
        }
        define 'shuffle',  funcc { ... args ->
            def l = new ArrayList(toList(args[0]))
            args[1] instanceof Random ? JCollections.shuffle(l, (Random) args[1])
                    : JCollections.shuffle(l)
            l
        }
        define 'sample',  funcc { ... args ->
            def x = toList(args[0])
            Random r = args.length > 1 && args[1] instanceof Random ? (Random) args[1] : new Random()
            x[r.nextInt(x.size())]
        }
        define 'upper_bound',  funcc { ... args ->
            if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MAX_VALUE')
            else if (args[0] instanceof Range) ((Range) args[0]).to
            else if (args[0] instanceof Collection) ((Collection) args[0]).size() - 1
            else throw new UnexpectedValueException('Don\'t know how to get high of ' + args[0] + ' with class ' + args[0].class)
        }
        define 'lower_bound',  funcc { ... args ->
            if (args[0] instanceof Number) ((Object) args[0].class).invokeMethod('getProperty', 'MIN_VALUE')
            else if (args[0] instanceof Range) ((Range) args[0]).from
            else if (args[0] instanceof Collection) 0
            else throw new UnexpectedValueException('Don\'t know how to get low of ' + args[0] + ' with class ' + args[0].class)
        }
        define 'collect_range_with_step',  funcc { ... args -> (args[0] as Range).step(args[1] as int) }
        define 'each_range_with_step',  func { IKismetObject... args ->
            (args[0].inner() as Range)
                    .step(args[1].inner() as int, ((Function) args[2]).toClosure())
        }
        define 'to_iterator',  funcc { ... args -> toIterator(args[0]) }
        define 'list_iterator',  funcc { ... args -> args[0].invokeMethod('listIterator', null) }
        define 'has_next?',  funcc { ... args -> args[0].invokeMethod('hasNext', null) }
        define 'next',  funcc { ... args -> args[0].invokeMethod('next', null) }
        define 'has_prev?',  funcc { ... args -> args[0].invokeMethod('hasPrevious', null) }
        define 'prev',  funcc { ... args -> args[0].invokeMethod('previous', null) }
        define 'new_list',  funcc { ... args -> new ArrayList(args[0] as int) }
        define 'list',  funcc { ... args -> args.toList() }
        define 'new_set',  funcc { ... args -> args.length > 1 ? new HashSet(args[0] as int, args[1] as float) : new HashSet(args[0] as int) }
        define 'set',  funcc { ... args ->
            Set x = new HashSet()
            for (a in args) x.add(a)
            x
        }
        define 'pair',  funcc { ... args -> new Pair(args[0], args[1]) }
        define 'tuple',  func(true) { IKismetObject... args -> new KismetTuple(args) }
        // assert_is x [+ [bottom_half x] [top_half x]]
        define 'bottom_half', funcc { ... args ->
            args[0] instanceof Number ? ((Number) args[0]).intdiv(2) :
                    args[0] instanceof Pair ? ((Pair) args[0]).first :
                            args[0] instanceof Collection ? ((Collection) args[0]).take(((Collection) args[0]).size().intdiv(2) as int) :
                                    args[0] instanceof Map ? ((Map) args[0]).values() :
                                            args[0]
        }
        define 'top_half',  funcc { ... args ->
            args[0] instanceof Number ? ((Number) args[0]).minus(((Number) args[0]).intdiv(2)) :
                    args[0] instanceof Pair ? ((Pair) args[0]).second :
                            args[0] instanceof Collection ? ((Collection) args[0]).takeRight(
                                    ((Collection) args[0]).size().minus(((Collection) args[0]).size().intdiv(2)) as int) :
                                    args[0] instanceof Map ? ((Map) args[0]).keySet() :
                                            args[0]
        }
        define 'to_list',  funcc { ... args -> toList(args[0]) }
        define 'to_set',  funcc { ... args -> toSet(args[0]) }
        define 'to_pair',  funcc { ... args -> new Pair(args[0].invokeMethod('getAt', 0), args[0].invokeMethod('getAt', 1)) }
        define 'to_tuple',  funcc(true) { ... args -> new KismetTuple(args[0] as IKismetObject[]) }
        define 'pairs', func(LIST_TYPE, MAP_TYPE), funcc { ... args ->
            def r = []
            for (x in (args[0] as Map)) r.add(new Pair(x.key, x.value))
            r
        }
        define 'map_from_pairs',  funcc { ... args ->
            def m = new HashMap()
            for (x in args[0]) {
                def p = x as Pair
                m.put(p.first, p.second)
            }
            m
        }
        define 'uncons',  funcc { ... args -> new Pair(args[0].invokeMethod('head', null), args[0].invokeMethod('tail', null)) }
        define 'cons',  funcc { ... args ->
            def y = args[1]
            def a = new ArrayList((y.invokeMethod('size', null) as int) + 1)
            a.add(args[0])
            a.addAll(y)
            a
        }
        define 'intersperse',  funcc { ... args ->
            def r = []
            boolean x = false
            for (a in args[0]) {
                if (x) r.add(args[1])
                else x = true
                r.add(a)
            }
            r
        }
        define 'intersperse_all',  funcc { ... args ->
            def r = []
            boolean x = false
            for (a in args[0]) {
                if (x) r.addAll(args[1])
                else x = true
                r.add(a)
            }
            r
        }
        define 'copy_map',  funcc { ... args -> new HashMap(args[0] as Map) }
        define 'new_map',  funcc { ... args -> args.length > 1 ? new HashMap(args[0] as int, args[1] as float) :
                args.length == 1 ? new HashMap(args[0] as int) : new HashMap() }
        define 'zip',  funcc { ... args -> args.toList().transpose() }
        define 'knit',  func { IKismetObject... args ->
            toList(args[0].inner()).transpose()
                    .collect { args[1].invokeMethod('call', it as Object[]) }
        }
        define 'transpose',  funcc { ... args -> toList(args[0]).transpose() }
        define 'unique?',  funcc { ... args ->
            args[0].invokeMethod('size', null) ==
                    args[0].invokeMethod('unique', false).invokeMethod('size', null)
        }
        define 'unique!',  funcc { ... args -> args[0].invokeMethod('unique', null) }
        define 'unique',  funcc { ... args -> args[0].invokeMethod('unique', false) }
        define 'unique_via?',  func { IKismetObject... args ->
            args[0].inner().invokeMethod('size', null) ==
                    args[0].inner().invokeMethod('unique', [false, ((Function) args[1]).toClosure()] as Object[])
        }
        define 'unique_via!',  func { IKismetObject... args -> args[0].inner().invokeMethod('unique', ((Function) args[1]).toClosure()) }
        define 'unique_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('unique', [false, ((Function) args[1]).toClosure()]) }
        define 'spread_map',  funcc { ... args -> args[0].invokeMethod('toSpreadMap', null) }
        define 'invert_map',  funcc { ... args ->
            final m0 = args[0] as Map
            def m = new HashMap(m0.size())
            for (final e : m0.entrySet()) {
                m.put(e.value, e.key)
            }
            m
        }
        define 'keys',  funcc { ... a -> a[0].invokeMethod('keySet', null).invokeMethod('toList', null) }
        define 'values',  funcc { ... a -> a[0].invokeMethod('values', null) }
        define 'reverse',  funcc { ... a -> a[0].invokeMethod('reverse', a[0] instanceof CharSequence ? null : false) }
        define 'reverse!',  funcc { ... a -> a[0].invokeMethod('reverse', null) }
        define 'reverse?',  funcc { ... a -> a[0].invokeMethod('reverse', false) == a[1] }
        define 'prefix?',  funcc { ... args ->
            if (args[1] instanceof String) ((String) args[1]).startsWith(args[0].toString())
            else JCollections.indexOfSubList(toList(args[1]), toList(args[0])) == 0
        }
        define 'suffix?',  funcc { ... args ->
            if (args[1] instanceof String) ((String) args[1]).endsWith(args[0].toString())
            else {
                def a = toList(args[0])
                def b = toList(args[1])
                JCollections.lastIndexOfSubList(b, a) == b.size() - a.size()
            }
        }
        define 'infix?',  funcc { ... args ->
            if (args[1] instanceof String) ((String) args[1]).contains(args[0].toString())
            else JCollections.invokeMethod('indexOfSubList', [toList(args[1]), toList(args[0])] as Object[]) != -1
        }
        define 'subset?',  funcc { ... args -> args[1].invokeMethod('containsAll', [args[0]] as Object[]) }
        define 'rotate!',  funcc { ... args ->
            List x = (List) args[0]
            JCollections.rotate(x, args[1] as int)
            x
        }
        define 'rotate',  funcc { ... args ->
            def x = new ArrayList(toList(args[0]))
            JCollections.rotate(x, args[1] as int)
            x
        }
        define 'each',  func { IKismetObject... args -> args[0].inner().each(((Function) args[1]).toClosure()) }
        define 'each_with_index',  func { IKismetObject... args -> args[0].inner().invokeMethod('eachWithIndex', ((Function) args[1]).toClosure()) }
        define 'collect',  func { IKismetObject... args ->
            args[0].inner().collect(((Function) args[1]).toClosure())
        }
        define 'collect_nested',  func { IKismetObject... args -> args[0].inner().invokeMethod('collectNested', ((Function) args[1]).toClosure()) }
        define 'collect_many',  func { IKismetObject... args -> args[0].inner().invokeMethod('collectMany', ((Function) args[1]).toClosure()) }
        define 'collect_map',  func { IKismetObject... args ->
            args[0].inner()
                    .invokeMethod('collectEntries') { ... a -> ((Function) args[1]).call(Kismet.model(a)).inner() }
        }
        define 'subsequences',  funcc { ... args -> args[0].invokeMethod('subsequences', null) }
        define 'combinations',  funcc { ... args -> args[0].invokeMethod('combinations', null) }
        define 'permutations',  funcc { ... args -> args[0].invokeMethod('permutations', null) }
        define 'permutations~',  funcc { ... args ->
            new PermutationGenerator(args[0] instanceof Collection ? (Collection) args[0]
                    : args[0] instanceof Iterable ? (Iterable) args[0]
                    : args[0] instanceof Iterator ? new IteratorIterable((Iterator) args[0])
                    : args[0] as Collection)
        }
        define 'any?',  func { IKismetObject... args ->
            args.length > 1 ? args[0].inner().any { ((Function) args[1]).call(Kismet.model(it)) } : args[0].inner().any()
        }
        define 'every?',  func { IKismetObject... args ->
            args.length > 1 ? args[0].inner().every { ((Function) args[1]).call(Kismet.model(it)) } : args[0].inner().every()
        }
        define 'none?',  func { IKismetObject... args ->
            !(args.length > 1 ? args[0].inner().any { ((Function) args[1]).call(Kismet.model(it)) } : args[0].inner().any())
        }
        define 'find',  func { IKismetObject... args -> args[0].inner().invokeMethod('find', ((Function) args[1]).toClosure()) }
        define 'find_result',  func { IKismetObject... args -> args[0].inner().invokeMethod('findResult', ((Function) args[1]).toClosure()) }
        define 'count',  func { IKismetObject... args -> args[0].inner().invokeMethod('count', ((Function) args[1]).toClosure()) }
        define 'count_element',  func { IKismetObject... args ->
            BigInteger i = 0
            def a = args[1].inner()
            def iter = args[0].iterator()
            while (iter.hasNext()) {
                def x = iter.next()
                if (x instanceof IKismetObject) x = x.inner()
                if (a == x) ++i
            }
            i
        }
        define 'count_elements',  func { IKismetObject... args ->
            BigInteger i = 0
            def c = args.tail()
            def b = new Object[c.length]
            for (int m = 0; m < c.length; ++i) b[m] = c[m].inner()
            boolean j = args.length == 1
            def iter = args[0].iterator()
            outer: while (iter.hasNext()) {
                def x = iter.next()
                if (x instanceof IKismetObject) x = x.inner()
                if (j) ++i
                else for (a in b) if (a == x) {
                    ++i
                    continue outer
                }
            }
            i
        }
        define 'count_by',  func { IKismetObject... args -> args[0].inner().invokeMethod('countBy', ((Function) args[1]).toClosure()) }
        define 'group_by',  func { IKismetObject... args -> args[0].inner().invokeMethod('groupBy', ((Function) args[1]).toClosure()) }
        define 'indexed',  func { IKismetObject... args -> args[0].inner().invokeMethod('indexed', args.length > 1 ? args[1] as int : null) }
        define 'find_all',  func { IKismetObject... args -> args[0].inner().findAll(((Function) args[1]).toClosure()) }
        define 'join',  funcc { ... args ->
            args[0].invokeMethod('join', args.length > 1 ? args[1].toString() : '')
        }
        define 'inject',  func { IKismetObject... args -> args[0].inner().inject { a, b -> ((Function) args[1]).call(Kismet.model(a), Kismet.model(b)) } }
        define 'collate',  funcc { ... args -> args[0].invokeMethod('collate', args.tail()) }
        define 'pop',  funcc { ... args -> args[0].invokeMethod('pop', null) }
        define 'add',  func { IKismetObject... args -> args[0].invokeMethod('add', args[1]) }
        define 'add_at',  funcc { ... args -> args[0].invokeMethod('add', [args[1] as int, args[2]]) }
        define 'add_all',  funcc { ... args -> args[0].invokeMethod('addAll', args[1]) }
        define 'add_all_at',  funcc { ... args -> args[0].invokeMethod('addAll', [args[1] as int, args[2]]) }
        define 'remove',  funcc { ... args -> args[0].invokeMethod('remove', args[1]) }
        define 'remove_elements',  funcc { ... args -> args[0].invokeMethod('removeAll', args[1]) }
        define 'remove_any',  func { IKismetObject... args -> args[0].inner().invokeMethod('removeAll', ((Function) args[1]).toClosure()) }
        define 'remove_element',  funcc { ... args -> args[0].invokeMethod('removeElement', args[1]) }
        define 'get',  funcc { ... a ->
            def r = a[0]
            for (int i = 1; i < a.length; ++i)
                r = r.invokeMethod('get', a[i])
            r
        }
        define 'clear',  funcc { ... args -> args[0].invokeMethod('clear', null) }
        define 'put',  funcc { ... args -> args[0].invokeMethod('put', [args[1], args[2]]) }
        define 'put_all',  funcc { ... args -> args[0].invokeMethod('putAll', args[1]) }
        define 'keep_all!',  funcc { ... args -> args[0].invokeMethod('retainAll', args[1]) }
        define 'keep_any!',  func { IKismetObject... args -> args[0].inner().invokeMethod('retainAll', ((Function) args[1]).toClosure()) }
        define 'has?',  funcc { ... args -> args[0].invokeMethod('contains', args[1]) }
        define 'has_all?',  funcc { ... args -> args[0].invokeMethod('containsAll', args[1]) }
        define 'has_key?',  funcc { ... args -> args[0].invokeMethod('containsKey', args[1]) }
        define 'has_key_traverse?',  funcc { ... args ->
            def x = args[0]
            for (a in args.tail()) {
                if (!((boolean) x.invokeMethod('containsKey', args[1]))) return false
                else x = args[0].invokeMethod('getAt', a)
            }
            true
        }
        define 'has_value?',  funcc { ... args -> args[0].invokeMethod('containsValue', args[1]) }
        define 'disjoint?',  funcc { ... args -> args[0].invokeMethod('disjoint', args[1]) }
        define 'intersect?',  funcc { ... args -> !args[0].invokeMethod('disjoint', args[1]) }
        define 'range',  funcc { ... args -> args[0]..args[1] }
        define 'sort!',  funcc { ... args -> args[0].invokeMethod('sort', null) }
        define 'sort',  funcc { ... args -> args[0].invokeMethod('sort', false) }
        define 'sort_via!',  func { IKismetObject... args -> args[0].inner().invokeMethod('sort', ((Function) args[1]).toClosure()) }
        define 'sort_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('sort', [false, ((Function) args[1]).toClosure()]) }
        define 'head',  funcc { ... args -> args[0].invokeMethod('head', null) }
        define 'tail',  funcc { ... args -> args[0].invokeMethod('tail', null) }
        define 'init',  funcc { ... args -> args[0].invokeMethod('init', null) }
        define 'last',  funcc { ... args -> args[0].invokeMethod('last', null) }
        define 'first',  funcc { ... args -> args[0].invokeMethod('first', null) }
        define 'flatten',  funcc { ... args -> args[0].invokeMethod('flatten', null) }
        define 'concat_list',  funcc { ... args ->
            def c = new ArrayList()
            for (int i = 0; i < args.length; ++i) {
                final x = args[i]
                x instanceof Collection ? c.addAll(x) : c.add(x)
            }
            c
        }
        define 'concat_set',  funcc { ... args ->
            def c = new HashSet()
            for (int i = 0; i < args.length; ++i) {
                final x = args[i]
                x instanceof Collection ? c.addAll(x) : c.add(x)
            }
            c
        }
        define 'concat_tuple',  funcc { ... args ->
            def c = new ArrayList<IKismetObject>()
            for (int i = 0; i < args.length; ++i) {
                final x = args[i]
                if (x instanceof Collection)
                    for (a in x)
                        c.add(Kismet.model(x))
                else c.add(Kismet.model(x))
            }
            def arr = new IKismetObject[c.size()]
            for (int i = 0; i < arr.length; ++i) arr[i] = c.get(i)
            new KismetTuple(arr)
        }
        define 'indices',  funcc { ... args -> args[0].invokeMethod('getIndices', null) }
        define 'find_index',  func { IKismetObject... args -> args[0].inner().invokeMethod('findIndexOf', ((Function) args[1]).toClosure()) }
        define 'find_index_after',  func { IKismetObject... args ->
            args[0].inner()
                    .invokeMethod('findIndexOf', [args[1] as int, ((Function) args[2]).toClosure()])
        }
        define 'find_last_index',  func { IKismetObject... args -> args[0].inner().invokeMethod('findLastIndexOf', ((Function) args[1]).toClosure()) }
        define 'find_last_index_after',  func { IKismetObject... args ->
            args[0].inner()
                    .invokeMethod('findLastIndexOf', [args[1] as int, ((Function) args[2]).toClosure()])
        }
        define 'find_indices',  func { IKismetObject... args -> args[0].inner().invokeMethod('findIndexValues', ((Function) args[1]).toClosure()) }
        define 'find_indices_after',  func { IKismetObject... args ->
            args[0].inner()
                    .invokeMethod('findIndexValues', [args[1] as int, ((Function) args[2]).toClosure()])
        }
        define 'intersect',  funcc { ... args -> args[0].invokeMethod('intersect', args[1]) }
        define 'split',  funcc { ... args -> args[0].invokeMethod('split', args.tail()) as List }
        define 'tokenize',  funcc { ... args -> args[0].invokeMethod('tokenize', args.tail()) }
        define 'partition',  func { IKismetObject... args -> args[0].inner().invokeMethod('split', ((Function) args[1]).toClosure()) }
        define 'each_consecutive',  func { IKismetObject... args ->
            def x = args[0].inner()
            int siz = x.invokeMethod('size', null) as int
            int con = args[1].inner() as int
            Closure fun = ((Function) args[2]).toClosure()
            def b = []
            for (int i = 0; i <= siz - con; ++i) {
                def a = new Object[con]
                for (int j = 0; j < con; ++j) a[j] = x.invokeMethod('getAt', i + j)
                fun.invokeMethod('call', a)
                b.add(a)
            }
            b
        }
        define 'consecutives',  funcc { ... args ->
            def x = args[0]
            int siz = x.invokeMethod('size', null) as int
            int con = args[1] as int
            def b = []
            for (int i = 0; i <= siz - con; ++i) {
                def a = new ArrayList(con)
                for (int j = 0; j < con; ++j) a.add(x.invokeMethod('getAt', i + j))
                b.add(a)
            }
            b
        }
        define 'consecutives~',  funcc { ... args ->
            def x = args[0]
            int siz = x.invokeMethod('size', null) as int
            int con = args[1] as int
            new IteratorIterable<>(new Iterator<List>() {
                int i = 0

                boolean hasNext() { this.i <= siz - con }

                @Override
                List next() {
                    def a = new ArrayList(con)
                    for (int j = 0; j < con; ++j) a.add(x.invokeMethod('getAt', this.i + j))
                    a
                }
            })
        }
        define 'drop',  funcc { ... args -> args[0].invokeMethod('drop', args[1] as int) }
        define 'drop_right',  funcc { ... args -> args[0].invokeMethod('dropRight', args[1] as int) }
        define 'drop_while',  func { IKismetObject... args -> args[0].inner().invokeMethod('dropWhile', ((Function) args[1]).toClosure()) }
        define 'take',  funcc { ... args -> args[0].invokeMethod('take', args[1] as int) }
        define 'take_right',  funcc { ... args -> args[0].invokeMethod('takeRight', args[1] as int) }
        define 'take_while',  func { IKismetObject... args -> args[0].inner().invokeMethod('takeWhile', ((Function) args[1]).toClosure()) }
        define 'each_combination',  func { IKismetObject... args -> args[0].inner().invokeMethod('eachCombination', ((Function) args[1]).toClosure()) }
        define 'each_permutation',  func { IKismetObject... args -> args[0].inner().invokeMethod('eachPermutation', ((Function) args[1]).toClosure()) }
        define 'each_key_value',  func { IKismetObject... args ->
            def m = (args[0].inner() as Map)
            for (e in m) {
                ((Function) args[1]).call(Kismet.model(e.key), Kismet.model(e.value))
            }
            m
        }
        define 'within_range?',  funcc { ... args -> (args[1] as Range).containsWithinBounds(args[0]) }
        define 'is_range_reverse?',  funcc { ... args -> (args[1] as Range).reverse }
        define 'max_in',  funcc { ... args -> args[0].invokeMethod('max', null) }
        define 'min_in',  funcc { ... args -> args[0].invokeMethod('min', null) }
        define 'max_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('max', ((Function) args[1]).toClosure()) }
        define 'min_via',  func { IKismetObject... args -> args[0].inner().invokeMethod('min', ((Function) args[1]).toClosure()) }
        define 'subsequence?',  funcc { ... args ->
            Iterator a = args[1].iterator()
            Iterator b = args[0].iterator()
            if (!b.hasNext()) return true
            def last = ++b
            while (a.hasNext() && b.hasNext()) {
                if (last == ++a) last = ++b
            }
            b.hasNext()
        }
        define 'supersequence?',  funcc { ... args ->
            Iterator a = args[0].iterator()
            Iterator b = args[1].iterator()
            if (!b.hasNext()) return true
            def last = ++b
            while (a.hasNext() && b.hasNext()) {
                if (last == ++a) last = ++b
            }
            b.hasNext()
        }
        alias 'has_all?', 'superset?'
        alias 'inject', 'reduce', 'fold'
        alias 'collect', 'map'
        alias 'bottom_half', 'half'
        alias 'size', 'length'
        alias 'find_all', 'select', 'filter'
        alias 'next', 'succ'
        alias 'every?', 'all?'
        alias 'any?', 'find?', 'some?'
        alias 'list', '&'
        alias 'set', '#'
        alias 'tuple', '$'
        alias 'concat_list', '&/'
        alias 'concat_set', '#/'
        alias 'concat_tuple', '$/'
        alias 'indexed', 'with_index'
        define 'times_do',  func { IKismetObject... args ->
            def n = (Number) args[0].inner()
            def l = new ArrayList(n.intValue())
            for (def i = n.minus(n); i < n; i += 1) l.add(((Function) args[1]).call(NumberType.from(i).instantiate(i)))
            l
        }
        define 'sum_range',  funcc { ... args ->
            Range r = args[0] as Range
            def to = r.to as Number, from = r.from as Number
            Number x = to.minus(from).next()
            x.multiply(from.plus(x)).intdiv(2)
        }
        define 'sum_range_with_step',  funcc { ... args ->
            Range r = args[0] as Range
            Number step = args[1] as Number
            def to = (r.to as Number).next(), from = r.from as Number
            to.minus(from).intdiv(step).multiply(from.plus(to.minus(step))).intdiv(2)
        }
        define 'sum',  funcc(true) { ... args -> args[0].invokeMethod('sum', null) }
        define 'product',  funcc(true) { ... args -> args[0].inject { a, b -> a.invokeMethod('multiply', [b] as Object[]) } }
        alias 'sum', '+/'
        alias 'product', '*/'
    }
}
