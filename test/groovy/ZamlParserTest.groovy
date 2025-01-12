package groovy

import com.github.mschwehl.zaml.ZamlParser

class ZamlParserTest {


	public static void main(String[] args) {

		def specList      = [
			"cypress",
			"tools",
			"node20",
			"mini",
			"nodeSelector"
		]


		def base = ZamlParser.parse("spec:")
		println "starting with \n" + ZamlParser.toYaml(base)

		Map substitutions = [:]
		substitutions.put('__TAG__', 'someTag')
		substitutions.put('__VALUE__', 'someValue')
		specList.each { fragment ->

			String yaml = new File('test/groovy/fragment/'+fragment+".yaml").text
			def node = ZamlParser.parse(yaml,substitutions)

			println "read \n" + ZamlParser.toYaml(node)
			base = ZamlParser.merge(base, node)

			println "merged:\n" + ZamlParser.toYaml(base)
		}
	}
}
