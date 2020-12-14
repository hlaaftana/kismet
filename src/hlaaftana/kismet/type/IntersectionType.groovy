package hlaaftana.kismet.type

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode
class IntersectionType extends AbstractType {
	List<Type> members

    IntersectionType(List<Type> members) {
		this.members = members
	}

	IntersectionType(Type... members) {
		this.members = members.toList()
	}

	IntersectionType reduced() {
		def newMems = new ArrayList<Type>()
		outer: for (mem in members) {
			for (newMem in newMems) {
				if (mem.relation(newMem).assignableFrom) {
					break outer
				}
			}
			newMems.add(mem)
		}
		new IntersectionType(newMems)
	}

	String toString() {
		def res = new StringBuilder("Intersection[")
		for (int i = 0; i < members.size(); ++i) {
			if (i != 0) res.append(', ')
			res.append(members[i].toString())
		}
		res.append((char) ']').toString()
	}

	TypeRelation weakRelation(Type other) {
		if (other instanceof IntersectionType) {
			final inter = members.intersect(other.members)
			final ours = members - inter, theirs = other.members - inter
			if (ours.empty) return theirs.empty ? TypeRelation.equal() : TypeRelation.supertype(theirs.size())
			else if (theirs.empty) return TypeRelation.subtype(ours.size())
			else {
				final iter = members.iterator()
				def max = iter.next().relation((Type) other)
				while (iter.hasNext()) {
					final rel = iter.next().relation((Type) other)
					if (max.none || (!rel.none && (rel.equal || rel.toSome() > max.toSome()))) max = rel
				}
				max
			}
		} else if (members.size() == 0) TypeRelation.supertype(Integer.MAX_VALUE)
		else {
			final iter = members.iterator()
			def min = iter.next().relation(other)
			while (iter.hasNext()) {
				final rel = iter.next().relation(other)
				if (rel.none) return rel
				else if (!rel.equal && rel.toSome() > min.toSome()) min = rel
			}
			min
		}
	}

	int size() { members.size() }
}
