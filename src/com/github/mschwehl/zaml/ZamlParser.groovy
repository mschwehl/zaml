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

		/* Jenkins does not like this
		public int hashCode(){
			key.hashCode()
		}
		*/

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
	
	/**
	 * Parse a YAML string into a YamlNode object.
	 * Do substitutions if needed
	 * @param yaml
	 * @param substitutions
	 * @return
	 */

	static YamlNode parse(String yaml, Map substitutions = [:]) {

		// ignore empty lines and comments
		def lines = yaml.readLines().  findAll { ! (it.trim().isEmpty() )}.collect { it }
		def rootNode = new YamlNode(key: "root", value: [:])
		def stack = [rootNode]

		while (!lines.isEmpty()) {
			def unformattedLine = lines.remove(0)
			// no tabs
			assert !unformattedLine.contains('\t') : unformattedLine
			
			// ident even
			int currentIndent = countIndent(unformattedLine)
			assert currentIndent %2 == 0 : unformattedLine
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
				assert !value.trim().startsWith("#") && !value.trim().startsWith("&") : "comment or acnchor after colon"
				String template = parseValue(value)
				substitutions.each { a, b ->
					template = template.replace(a,b)
				}
				topNode.addElement(key,template)
				continue
			}
			def nextLine = lines.size() > 0 ? lines.first() : null
			
			// starting a list
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

		return yaml.toString().trim()
	}


	static YamlNode merge(YamlNode base, YamlNode overlay) {
		def mergedValue = deepCopy(base.value)
		def mergedNode = new YamlNode(key: base.key, value: mergedValue)
		mergeValues(mergedNode.value, overlay.value)
		return mergedNode
	}

	private static def mergeValues(base, overlay) {
		if (base instanceof Map && overlay instanceof Map) {
			overlay.each { key, overlayValue ->
				if (base.containsKey(key)) {
					base[key] = mergeValues(base[key], overlayValue)
				} else {
					base[key] = deepCopy(overlayValue)
				}
			}
			return base
		} else if (base instanceof List && overlay instanceof List) {
			overlay.each { overlayItem ->
				if (overlayItem instanceof Map && overlayItem.name) {
					def baseItem = base.find { it instanceof Map && it.name == overlayItem.name }
					if (baseItem) {
						mergeValues(baseItem, overlayItem)
					} else {
						base.add(deepCopy(overlayItem))
					}
				} else if (!base.contains(overlayItem)) {
					base.add(deepCopy(overlayItem))
				}
			}
			return base
		} else if (base instanceof YamlNode && overlay instanceof YamlNode) {
			return merge(base, overlay)
		} else {
			return deepCopy(overlay)
		}
	}

	private static def deepCopy(Object original) {
		if (original instanceof YamlNode) {
			return new YamlNode(key: original.key, value: deepCopy(original.value), listNode: original.listNode)
		} else if (original instanceof Map) {
			return original.collectEntries { k, v -> [k, deepCopy(v)] }
		} else if (original instanceof List) {
			return original.collect { v -> deepCopy(v) }
		} else {
			return original
		}
	}
}