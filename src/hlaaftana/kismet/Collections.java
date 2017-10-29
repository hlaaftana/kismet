package hlaaftana.kismet;

import java.util.HashMap;
import java.util.Map;

public class Collections {
	public static <K, V> Map<V, K> flip(Map<K, V> map) {
		Map<V, K> newMap = new HashMap<>();
		for (Map.Entry<K, V> e : map.entrySet()) {
			newMap.put(e.getValue(), e.getKey());
		}
		return newMap;
	}
}
