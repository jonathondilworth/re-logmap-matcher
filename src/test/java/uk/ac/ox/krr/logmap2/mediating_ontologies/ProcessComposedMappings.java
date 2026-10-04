package uk.ac.ox.krr.logmap2.mediating_ontologies;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.ox.krr.logmap2.mappings.objects.MappingObjectStr;
import uk.ac.ox.krr.logmap2.oaei.reader.FlatAlignmentReader;

public class ProcessComposedMappings{
	/**
	 * Performs set subtraction between sets of mappings. Works for any two sets of mappings. However,
	 * in this class, we care about removing the mappings originally provided by logmap
	 * from the set of mappings obtained via the mediating ontology.
	 * @param mediatingComposedMappings
	 * @param logmapMappings
	 * @return mapSubtracted;
	 */
	public static Set<MappingObjectStr> mappingSetSubtraction(Set<MappingObjectStr> mediatingComposedMappings, Set<MappingObjectStr> logmapMappings){
		Set<MappingObjectStr> mapSubtracted = new HashSet<MappingObjectStr>();
		
		for(MappingObjectStr mapping: mediatingComposedMappings) {
			
			if(logmapMappings.contains(mapping)==false) {
				mapSubtracted.add(mapping);
			}
		}
		
		return mapSubtracted;
	}
	public static void main(String[] args) {
		//1. read from config file the parentPath
		RunMediatingOntologiesPipeline configReader = new RunMediatingOntologiesPipeline();
		configReader.getParentFolder(args);
		configReader.readConfigJSON();
		

		String parentPath = configReader.parentPath;
		String onto1_iri = "file:" + configReader.sourceOntoPath;
		String onto2_iri = "file:" + configReader.targetOntoPath;
		
		//2. check that there is a folder with composed mappings
		// Load source2target mappings as logmap original mappings
		String sourceTargetMappingsFile ="/home/valentina/git-repos/test-data/test-argparse/store-source-target/source2target.txt";
		try {
			
			FlatAlignmentReader mappingReader = new FlatAlignmentReader(sourceTargetMappingsFile);
			Set<MappingObjectStr> mapSource2Target = mappingReader.getMappingObjects();
			System.out.println("Original set of mappings contains " + mapSource2Target.size() + " mappings");
			//3. load one mapping file
			String moMappingsPath = parentPath +"store-composed-mappings/";
			String newMappingsPath = parentPath + "store-unique-mappings/";
			// To save new mappings later in the loop
			CreateMappingsBetweenTwoOntologies onto_mapper = new CreateMappingsBetweenTwoOntologies();
			
			// Load iteratively all ontologies in the folder
			File listPath = new File(moMappingsPath);
			File[] listOnto = listPath.listFiles();
			String moComposedMappingsPath = null;
			List<FlatAlignmentReader> readersArray = new ArrayList<FlatAlignmentReader>();
			Integer txtCounter = 0;
			for( File f: listOnto) {
				boolean is_txt = f.getName().endsWith(".txt");
				if(is_txt==false) {
					continue;
				}else {
					
					System.out.println("Filtering new mappings for " + f.getName());
					moComposedMappingsPath = moMappingsPath + f.getName();
					readersArray.add(new FlatAlignmentReader(moComposedMappingsPath));
					
					Set<MappingObjectStr> moComposedMappings = readersArray.get(txtCounter).getMappingObjects();
					System.out.println("Mediating ontology gave a total of " + moComposedMappings.size() + " mappings");
					Set<MappingObjectStr> newMappings = mappingSetSubtraction(moComposedMappings, mapSource2Target);
					System.out.println("Of which new mappings are " + newMappings.size());
					String newMapPath = newMappingsPath + f.getName().substring(0, f.getName().lastIndexOf('.'));
					onto_mapper.saveOntologyMappings(true, newMappings, newMapPath, onto1_iri, onto2_iri);

					//update iterator
					txtCounter++;
				}
				
			}
			/*
			 * String moComposedMappings = moMappingsPath + "OBA.txt"; FlatAlignmentReader
			 * mappingReader_i = new FlatAlignmentReader(moComposedMappings);
			 * Set<MappingObjectStr> composedMappings_i =
			 * mappingReader_i.getMappingObjects();
			 * System.out.println("Mediating ontology gave a total of " +
			 * composedMappings_i.size() + " mappings"); Set<MappingObjectStr> newMappings =
			 * mappingSetSubtraction(composedMappings_i, mapSource2Target);
			 * System.out.println("Of which new mappings are " + newMappings.size());
			 */

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//4. subtract the mappings that were already found by Logmap (source,target)

	}
	
	
}