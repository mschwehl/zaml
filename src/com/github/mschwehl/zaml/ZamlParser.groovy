package com.github.mschwehl.zaml

class ZamlParser {
	static class YamlNode {

		boolean listNode = false
		String key
		def value // Can be String, Map, List, or YamlNode

		boolean isNode() {
			return value instanceof YamlNode
		}

		boolean isList() {
			return value instanceof List
		}

		boolean isMap() {
			return value instanceof Map
		}

		public int hashCode(){
			key.hashCode()
		}

		String toString() {
			return "YamlNode(key: $key, value: $value)"
		}

		void addElement(String key, Object value) {
			if (isNode()) {
				((YamlNode) this.value).addElement(key, value)
			}
			if (isMap()) {
				((Map) this.value).putAt(key, value)
			}
			if (isList()) {
				((Map) ((List) this.value).getLast()).put(key, value)
			}
		}
	}

	static YamlNode parse(String yaml) {

		// ignore empty lines and comments
		def lines = yaml.readLines().  findAll { ! (it.trim().isEmpty() )}.collect { it }
		def rootNode = new YamlNode(key: "root", value: [:])
		def stack = [rootNode]

		while (!lines.isEmpty()) {

			def unformattedLine = lines.remove(0)
			assert !unformattedLine.contains('\t') : "using Tab-Indentation at: ${unformattedLine} read yaml manual"
			assert !unformattedLine.contains('#') :  "using comments: ${unformattedLine} this is not supported"
			assert !unformattedLine.contains('&') :  "using anchors: ${unformattedLine} this is not supported"
			int currentIndent = countIndent(unformattedLine)
			assert currentIndent %2 == 0 : "uneven indentation at: ${unformattedLine} read yaml manual"

			def line = unformattedLine.trim()
			// go to correct position
			while( ( stack.size() * 2) > currentIndent + 2) {
				stack.removeLast()
			}


			def topNode = stack[-1]

			def (key, value) = line.split(":", 2).collect { it.trim() }

			if (key.trim().startsWith("-")) {
				def (listKey, listVal) = line.substring(1).trim().split(":", 2).collect { it.trim() }
				def map = [:]
				map.put(listKey, listVal)
				topNode.value.add(map)
				continue
			}

			if (value) {
				topNode.addElement(key, parseValue(value))
				continue
			}

			def nextLine = lines.size() > 0 ? lines.first() : null
			
			// staring a list
			if (nextLine && nextLine.trim().startsWith("-")) {
				def node = new YamlNode(key: key, value: [])
				topNode.addElement(key, node)
				stack << node
			} else {
				def node = new YamlNode(key: key, value: [:])
				topNode.addElement(key, node)
				stack << node
			}
		
		}

		return rootNode
	}

	private static int countIndent(String line) {
		int intermediate = line.takeWhile { it == ' ' }.length()
		if (line.trim().startsWith("-")) {
			intermediate += 2
		}
		return intermediate
	}

	private static def parseValue(String value) {
		return value != null ? value : ""
	}

	private static def toValue(Object value) {
		return value != null ? value : ""
	}

	static String toYaml(YamlNode node, int level = 0) {
		StringBuilder yaml = new StringBuilder()

		String indent = "  " * level

		if (node.isMap()) {
			node.value.eachWithIndex { k, v, index  ->

				if(node.listNode && index == 0) {
					yaml.append(" ${k}${v != null ? ":" : ""}")
				} else {
					yaml.append("${indent}${k}${v != null ? ":" : ""}")
				}

				if (v instanceof YamlNode) {
					yaml.append("\n").append(toYaml(v, level + 1))
				} else if (v instanceof Map) {
					yaml.append("\n").append(toYaml(new YamlNode(value: v), level + 1))
				} else {
					yaml.append(" ${toValue(v)}\n")
				}
			}
		} else if (node.isList()) {
			node.value.each { item ->
				def listIdent = "  " * (level -1)
				yaml.append("${listIdent}-")
				if (item instanceof Map) {
					def listNode = new YamlNode(value: item, listNode:true)
					yaml.append(toYaml(listNode,level))
				} else {
					yaml.append(" ${item}\n")
				}
			}
		}

		return yaml.toString()
	}


	static YamlNode merge(YamlNode base, YamlNode overlay) {
		def mergedNode = new YamlNode(key: base.key, value: base.value instanceof List ? [] : [:])

		if (base.isMap() && overlay.isMap()) {
			// Merge both maps without overriding base values
			base.value.each { k, v ->
				def overlayValue = overlay.value[k]
				if (overlayValue != null) {
					if (v instanceof YamlNode && overlayValue instanceof YamlNode) {
						// Recursively merge nested YamlNodes, appending overlay values
						mergedNode.value[k] = merge(v, overlayValue)
					} else if (v instanceof List && overlayValue instanceof List) {
						// Merge lists by appending overlay values
						mergedNode.value[k] = appendLists(v, overlayValue)
					} else if (v instanceof Map && overlayValue instanceof Map) {
						// Merge maps by appending overlay values
						mergedNode.value[k] = appendMaps(v, overlayValue)
					} else {
						// Append overlay value to base value (if it's a simple value type)
						mergedNode.value[k] = [v, overlayValue].flatten()
					}
				} else {
					// If the key doesn't exist in overlay, append the base value
					mergedNode.value[k] = v
				}
			}

			// Add any new keys from overlay that are not in base
			overlay.value.each { k, v ->
				if (!base.value.containsKey(k)) {
					mergedNode.value[k] = v
				}
			}
		} else if (base.isList() && overlay.isList()) {
			// Merge lists, preserving the unique elements and matching by 'name'
			mergedNode.value = appendLists(base.value, overlay.value)
		} else {
			// For non-map, non-list types, append overlay value to base value
			mergedNode.value = base.value + overlay.value
		}

		return mergedNode
	}

	// Helper method to append lists while ensuring no duplicates based on 'name'
	private static List appendLists(List baseList, List overlayList) {
		def mergedList = []
		def baseNames = baseList.findAll { it instanceof Map }.collect { it["name"] }

		// First, add all base items
		mergedList.addAll(baseList)

		// Now add overlay items, avoiding duplicates based on 'name'
		overlayList.each { overlayItem ->
			if (overlayItem instanceof Map && overlayItem.containsKey("name")) {
				def match = baseList.find { it instanceof Map && it["name"] == overlayItem["name"] }
				if (match) {
					// Merge matching items
					match.putAll(overlayItem)
				} else {
					// Append new unique item
					mergedList.add(overlayItem)
				}
			} else {
				// Append non-map items
				mergedList.add(overlayItem)
			}
		}

		return mergedList
	}

	// Helper method to append maps by merging values without overriding
	private static Map appendMaps(Map baseMap, Map overlayMap) {
		def mergedMap = [:]

		// Append baseMap values first
		baseMap.each { k, v ->
			mergedMap[k] = v
		}

		// Append overlayMap values, ensuring we don't overwrite
		overlayMap.each { k, v ->
			if (mergedMap.containsKey(k)) {
				def mergedValue = mergedMap[k]
				// If the value is a list, append the overlay value
				if (mergedValue instanceof List && v instanceof List) {
					mergedMap[k] = mergedValue + v
				} else {
					mergedMap[k] = [mergedValue, v].flatten()  // Flatten to handle scalar and list merging
				}
			} else {
				mergedMap[k] = v
			}
		}

		return mergedMap
	}

}
