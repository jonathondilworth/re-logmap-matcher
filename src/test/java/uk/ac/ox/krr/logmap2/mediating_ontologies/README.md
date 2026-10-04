# How to use the mediating ontologies package

## Initial setup

Before you run the programs in this package...

### Get your input data
Our default example starts with the human and mouse ontology
You can download them manually at OAEI
human.owl
mouse.owl

### Structure your parent folder

Please create the following folder structure prior to running the programs. The name of the two sub-directories and the config file matters.

```
<parent folder>/
├── store-mediating-ontologies/
├── store-composed-mappings/
└── config.json
```

### Write your config file

The config file is very simple

```
{
  "sourceOntologyFullPath": "/home/valentina/git-repos/test-data/test_onto_input/human.owl",
  "targetOntologyFullPath": "/home/valentina//git-repos/test-data/test_onto_input/mouse.owl"
}

```

## Set up for command line
You will need to run from terminal the following to create the packages you need

```
<your-machine>:~/git-repos/logmap-matcher$ mvn -e clean install
```

Among all the others, this will give you two jar files in the target folder 

* `logmap-matcher-4.0.jar`
* `logmap-matcher-4.0-tests.jar`

This is what you will need to run from command line.

## Steps

### 1. RunMediatingOntologies

This gets the top 10 mediating ontologies for the source and target ontology
To run from command line type in terminal (provided you have built two separate targets for logmap and its tests) with the correct full path to the folder you have created earlier.

```
java -Xms500M -Xmx25G -DentityExpansionLimit=10000000 --add-opens=java.base/java.lang=ALL-UNNAMED
      -cp logmap-matcher-4.0-tests.jar:logmap-matcher-4.0.jar 
          uk.ac.ox.krr.logmap2.mediating_ontologies.RunMediatingOntologiesPipeline <parent folder>

```

### 2. CreateComposedMappings

This is a memory-heavy program, we highly recommend to run from terminal. Do keep the system monitor open while you run to check that memory is not filling up. 

```
java -Xms500M -Xmx25G -DentityExpansionLimit=10000000 --add-opens=java.base/java.lang=ALL-UNNAMED
     -cp logmap-matcher-4.0-tests.jar:logmap-matcher-4.0.jar 
          uk.ac.ox.krr.logmap2.mediating_ontologies.CreateComposedMappings <parent folder>

```
### 3. ProcessComposedMappings

Where we take the mappings obtained via each mediating ontology and we subtract those that logmap had already found when aligning the source and target.

## TODO

* Make naming and structure of directory for storing less constricting.
* Expand on config file structure and content
* (About CreateComposedMappings) The code should be written so that it can be resumed when run a second time, so you don't get to redo everything.It also need a good cleanup.
* Reduce the number of classes, I don't think I need that many