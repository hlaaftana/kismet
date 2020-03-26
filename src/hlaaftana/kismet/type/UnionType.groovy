package hlaaftana.kismet.type

import groovy.transform.CompileStatic

@CompileStatic
class UnionType extends AbstractType {
	Set<Type> members

    UnionType(Set<Type> members) {
		this.members = members
	}

	String toString() {
		def res = new StringBuilder("Union[")
		for (int i = 0; i < members.size(); ++i) {
			if (i != 0) res.append(', ')
			res.append(members[i].toString())
		}
		res.append((char) ']').toString()
	}

	TypeRelation weakRelation(Type other) {
		if (other instanceof UnionType) {
			final inter = members.intersect(other.members)
			final ours = members - inter, theirs = other.members - inter
			if (ours.empty) return theirs.empty ? TypeRelation.equal() : TypeRelation.subtype(theirs.size())
			else if (theirs.empty) return TypeRelation.supertype(ours.size())
			else {
				final iter = members.iterator()
				TypeRelation min = iter.next().relation(other)
				while (iter.hasNext()) {
					final rel = iter.next().relation(other)
					if (rel.none) return rel
					else if (rel.toSome() < min.toSome()) min = rel
				}
				min
			}
		} else if (members.size() == 0) TypeRelation.subtype(Integer.MAX_VALUE)
		else {
			final iter = members.iterator()
			TypeRelation max = iter.next().relation(other)
			while (iter.hasNext()) {
				final rel = iter.next().relation(other)
				if (!rel.none && rel.toSome() > max.toSome()) max = rel
			}
			max
		}
	}

	boolean equals(other) { other instanceof UnionType && members == other.members }

	int size() { members.size() }
}
