package hlaaftana.kismet.scope

import groovy.transform.CompileStatic

@CompileStatic
class StringEscaper {
	private static final char[] from = [(char) '\\', (char) '"', (char) '\'', (char) '\b', (char) '\n',
			(char) '\t', (char) '\f', (char) '\r', (char) '\u000b', (char) '\7', (char) '\u001b']
	private static final char[] to = [(char) '\\', (char) '"', (char) '\'', (char) 'b', (char) 'n',
			(char) 't', (char) 'f', (char) 'r', (char) 'v', (char) 'a', (char) 'e']

	static int indexOf(char[] arr, char ch) {
		for (int i = 0; i < arr.length; ++i) {
			if (arr[i] == ch) return i
		}
		-1
	}

	static String escape(String str) {
		StringBuilder builder = new StringBuilder()
		for (int i : str.codePoints().toArray()) {
			int index
			if (i < 93 && (index = indexOf(from, (char) i)) != -1) builder.append(to[index])
			else if (i > 255) builder.append("\\u{").append(Integer.toString(i, 16)).append('}')
			else builder.appendCodePoint(i)
		}
		builder.toString()
	}

	static String unescape(String str) throws NumberFormatException {
		StringBuilder builder = new StringBuilder()
		boolean escaped, recordU
		int uBase = 0
		StringBuilder u
		for (char c : str.toCharArray()) {
			if (escaped) {
				if (null != u) {
					if (recordU)
						if (c == ((char) '}')) {
							recordU = false
							builder.appendCodePoint(Integer.parseInt(u.toString(), uBase))
							u = null
						} else u.append(c)
					else if (c == ((char) 'x') || c == ((char) 'X')) uBase = 16
					else if (c == ((char) 'o') || c == ((char) 'O')) uBase = 8
					else if (c == ((char) 'd') || c == ((char) 'D')) uBase = 10
					else if (c == ((char) 'b') || c == ((char) 'B')) uBase = 2
					else if (c == ((char) '{')) {
						if (uBase < 2) uBase = 16
						recordU = true
					} else {
						recordU = false
						builder.append('\\').append('u').append(c)
						u = null
					}
					continue
				} else {
					if (c == ((char) 'u')) {
						u = new StringBuilder()
						continue
					} else {
						int index = indexOf(to, c)
						if (index != -1) {
							builder.deleteCharAt(builder.length() - 1)
							builder.append(from[index])
						} else builder.append(c)
					}
				}
			} else builder.append(c)
			escaped = c == ((char) '\\')
		}
		builder.toString()
	}
}
