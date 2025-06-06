package groovy
import java.util.Date
import com.github.mschwehl.zaml.ZamlParser

/*
  agent {
    kubernetes(k8sagent(project:'tut', agent:'base', containers:'tools cypress:13', spec:'mini node:someNode' , debug:'true'))
  }
*/

def libraryResource(path) {
	
	
	def neu
	
	if (path.contains("spec")) {
		neu = path.substring("spec/".length())
	}
	
	if (path.contains("container")) {
		neu = path.substring("container/".length())
	}

	return new File('test/groovy/fragment/' + neu).text
}


def call(Map opts = [:]) {
	
	// 
	def env = [:]
	
	
    println("===> Configuring k8s... using groovy " + GroovySystem.version)
    String project = opts.get('project', 'unknown')
    String showRawYaml = opts.get('showRawYaml', false)
    String agent = opts.get('agent', 'rocky-9-jdk21')
	
    // agent via mapping table, e.g: jdk21 -> rocky-9-jdk21
    if (opts.containsKey('agent')) {
        agent = getAgentName(agent)
    }
    String cloud = opts.get('cloud', getCloudForProject(project))

    // store build-cloud
    env['cloud'] = cloud


    def ret = [:]
    ret['cloud'] = cloud
    ret['inheritFrom'] = agent
    ret['defaultContainer'] = 'jnlp'
    ret['showRawYaml'] = showRawYaml

    def spec = ZamlParser.parse("spec:")

    if (opts.get('containers', null) != null) {
        spec = mergeContainer(spec, opts.get('containers'))
    }
    
    if (opts.get('spec', null) != null) {
        spec = mergeSpec(spec, opts.get('spec')) 
    }

    String additonalYAML = ZamlParser.toYaml(spec)

    if (additonalYAML.trim().length() > "spec:".length()) {
        ret['yaml'] = additonalYAML.trim()
    }


    // Debugging
    if (opts.get('debug', false)) {
        ret.each {
            k, v -> if (k.equals('yaml')) {
                // Console deletes leading spaces
                println "${k}:${v}"
            } else {
                println "${k}:${v}"
            }
        }
    }
    return ret
}

// containers:'tools cypress:13'
def mergeContainer(spec, line) {
	
    def elementList = line.trim().split('\\s+').toList()
    String template
    for (element in elementList) {

        def (fragment, tag) = element.tokenize( ':' )
        // load template
        template = libraryResource 'container/' + fragment + '.yaml'
		def substitutions = [:]
		substitutions.put('${TAG}', (tag == null ? 'latest' : tag))
        def toMerge = ZamlParser.parse(template,substitutions)
        spec = ZamlParser.merge(spec, toMerge)
    }

    return spec
}


// spec:'node:worker05'
def mergeSpec(spec, line) {
    def elementList = line.trim().split('\\s+').toList()
    String template
    for (element in elementList) {

        def (fragment, value) = element.tokenize( ':' )
        // load template
        template = libraryResource 'spec/' + fragment + '.yaml'
		def substitutions = [:]
		substitutions.put('${VALUE}', (value == null ? '' : value))

        def toMerge = ZamlParser.parse(template,substitutions)
        spec = ZamlParser.merge(spec, toMerge)
    }

    return spec
}



/**
 * get AgentName by key from agents.json
 */
def getAgentName(key) {
	
	/*
    def agents = libraryResource 'agents.json'
    def slurp = new groovy.json.JsonSlurper()
    return slurp.parseText(agents).get(key).agent.toString()
    */
	
	return "rocky-9-jkd21"
}

/** 
  * Reads active clouds from Environment 
  */
def getActiveClouds() {
    // cloud-1=true ; cloud-2=false

	/*	 
    Set < String > activeClouds = []
    String cloudbalance = Jenkins.get().getGlobalNodeProperties()[0].getEnvVars()['KUBERNETES_CLOUD_BALANCING'];
    if (cloudbalance == null) {
        throw new Exception ("NO ENVIRONMENT KUBERNETES_CLOUD_BALANCING")
    }
    // find and store the active clouds
    cloudbalance.split(';')
        .each {
        e -> def c = e.trim().split('=')
        if (c[1].trim().equals('true')) {
            activeClouds.add(c[0].trim())
        }
    }
    println("===> Configuring k8s - KUBERNETES_CLOUD_BALANCING: " + cloudbalance + " active: " 
        + activeClouds.join(", "))
    return activeClouds
    
    */
	
	return ["cloud-1"]
	
}

/**
 * Get cloud for Jenkins-Project
 */
def getCloudForProject(user_project) {
	
    Set < String > activeClouds = getActiveClouds()
	
	/*
    // read the project settings
    def projectProperties = new Properties()
    Jenkins.get().getItems(com.cloudbees.hudson.plugins.folder.Folder. class).findAll {
        it.parent.getClass() != Folder. class
    }.findAll {
        it.getName() == user_project
    }.each {
        it.getProperties().get(org.jenkinsci.plugins.configfiles.folder.FolderConfigFileProperty. class)
            .getConfigs().findAll {
            it.id == 'jenkins-cloud'
        }.each {
            e -> projectProperties.load(new StringReader(e.content));
        }
    }
    // read the project preference
    String candidate = projectProperties['cloud']
    if (candidate != null && activeClouds.contains(candidate)) {
        println("===> Configuring k8s - USING PROJECT SETTING: " + candidate)
        return candidate
    }
    // using first defined cloud
    if (activeClouds.size() > 0) {
        println("===> Configuring k8s - USING FALLBACK: " + activeClouds[0] + " over " + (candidate != null ? candidate: "undefined"))
        return activeClouds[0]
    }
    // no active cloud
    throw new Exception("NO NO ACTIVE CLOUD FOR CANDIDATE IN FOLDER " + candidate + " -> " + activeClouds.join(", "))
    
    */
	
	return "cloud-1"
}

call(project:'tut', agent:'base', containers:'tools cypress:13', spec:'mini nodeSelector:someNode' , debug:'true')

