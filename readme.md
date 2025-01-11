
# ZAML
Zero Dependency Yaml Parser

##Use Case
I need a simple way to merge Pod manifests for my Jenkins shared library. Ideas were taken from https://github.com/liejuntao001/jenkins-k8sagent-lib. I don't want to use SnakeYAML, so I wrote this super simple YAML merge tool.

## Usecase

look at ZamlParserTest

```
   def yaml = '''
   spec:
     containers:
     - name: jnlp
       resources:
         limits:
           memory: 2Gi
         requests:
           memory: 1Gi
   '''
   
   def node = ZamlParse.parse(yaml)
   println ZamlParser.toYaml(node)
   node = ZamlParser.merge(node, other_node_to_be_parsed)
   println ZamlParser.toYaml(node)
```

     

## Limits
This is not a parser but relies on valid YAML as input , and you have to use KNOWN YAMLs to merge .
No anchors, multiline comments, and so on .

The data structure is straightforward, but the merge is just a quick hack for my use case.

