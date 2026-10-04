package uk.ac.ox.krr.logmap2.mediating_ontologies;
// import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.ox.krr.logmap2.LogMap2_Matcher;
import uk.ac.ox.krr.logmap2.io.OutPutFilesManager;
// import uk.ac.ox.krr.logmap2.LogMap2_OAEI_BioPortal;
import uk.ac.ox.krr.logmap2.mappings.objects.MappingObjectStr;

public class CreateComposedMappings{
	// private final Set<MappingObjectStr>  s2mMaps;
	// private final Set<MappingObjectStr>  m2tMaps;

	public CreateComposedMappings() {
		// this.s2mMaps = null;
		// this.m2tMaps = null;
	}
	
//	private CreateComposedMappings(Set<MappingObjectStr>  s2mMaps, Set<MappingObjectStr>  m2tMaps ) {
//		this.s2mMaps = s2mMaps;
//		this.m2tMaps = m2tMaps;
//	}
//	
//	
//	public Set<MappingObjectStr> getSource2MediumMappings(){
//		return s2mMaps;
//	}
//	
//	public Set<MappingObjectStr> getMedium2TargetMappings(){
//		return m2tMaps;
//	}
	
	
	/**
	 * Takes source, target and one mediating ontologies and finds all mappings
	 * for (source, medium) and (medium, target)
	 * @param source OWLOntology the source ontology
	 * @param target OWLOntology the target ontology
	 * @param medium OWLOntology the mediating ontology
	 * @return CreateCompoSedMappings new instance of CreateComposedMappings
	 */
	public Set<MappingObjectStr> triangulateOntologies(OWLOntology source, OWLOntology target, OWLOntology medium) {
		
	   System.out.println("Starting the matching task (source, medium)");
		LogMap2_Matcher sourceMapMedium= new LogMap2_Matcher(source, medium);
		Set<MappingObjectStr>  s2mMaps = sourceMapMedium.getLogmap2_Mappings();
		// sourceMapMedium.clearIndexStructures();
		sourceMapMedium = null;
		System.out.println("Completing the matching task (source, medium). Mappipngs count " +s2mMaps.size());

		System.out.println("Starting the matching task ( mo_i, target)");
		LogMap2_Matcher mediumMapTarget = new LogMap2_Matcher(medium, target);
		Set<MappingObjectStr>  m2tMaps = mediumMapTarget.getLogmap2_Mappings();
		// mediumMapTarget.clearIndexStructures();
		mediumMapTarget = null;
		System.out.println("Completing the matching task ( medium, target). Mappings count " + m2tMaps.size());
		Set<MappingObjectStr>   composedMappings =  aggregateComposedMapping(s2mMaps, m2tMaps);
		// return new CreateComposedMappings(s2mMaps, m2tMaps);
		return composedMappings;
	}
	
	public Set<MappingObjectStr>  aggregateComposedMapping(Set<MappingObjectStr> source2mo, Set<MappingObjectStr> mo2target){
		
		Set<MappingObjectStr>   composedMappings = new HashSet<MappingObjectStr>();
		
		for (MappingObjectStr map_mo1 : source2mo){
			//TODO why only ClassMappings?
			if (!map_mo1.isClassMapping())
				continue;
			for (MappingObjectStr map_mo2 : mo2target){
				
				if (!map_mo2.isClassMapping())
					continue;
				
				if (map_mo1.getIRIStrEnt2().equals(map_mo2.getIRIStrEnt1())){
		
					
					MappingObjectStr mapping = new MappingObjectStr(
							map_mo1.getIRIStrEnt1(), 
							map_mo2.getIRIStrEnt2(), 
							(map_mo1.getConfidence()+map_mo2.getConfidence())/2.0, MappingObjectStr.EQ, MappingObjectStr.CLASSES);
					
					composedMappings.add(mapping);
					//TODO addVote as part of statistics
				}
				}					
		} 
		
		
		return composedMappings;
	}
	
	
	public static void main(String[] args) {
		RunMediatingOntologiesPipeline configReader = new RunMediatingOntologiesPipeline();
		configReader.getParentFolder(args);
		configReader.readConfigJSON();
		

		String parentPath = configReader.parentPath;
		String onto1_iri = "file:" + configReader.sourceOntoPath;
		String onto2_iri = "file:" + configReader.targetOntoPath;
		
		//String onto1_iri = "file:/home/valentina/git-repos/test-data/test_onto_input/human.owl";
		//String onto2_iri = "file:/home/valentina//git-repos/test-data/test_onto_input/mouse.owl";
		String basePath = parentPath + "store-mediating-ontologies/";
		String outPath = parentPath + "test-mappings/";
		String listFile = parentPath + "/logmap_top10_mediating_ontologies.txt";

		
		// String mo_i_iri = "file:" + "/home/valentina/git-repos/test-data/test_onto_output/test-mediating-store/MIO.owl";
		// String parentPath = "/home/valentina/git-repos/test-data/test_onto_output/test-mediating-store/";
		// String filePath = parentPath + "logmap_top10_mediating_ontologies.txt";
		/*
		 * 1. Get  the two original ontologies and at least one mediating ontology
		 */
		OWLOntologyManager onto_manager = OWLManager.createOWLOntologyManager();

		// In case an import is broken
		OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();

		// Important to reassign value, see https://github.com/owlcs/owlapi/issues/503
		config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
		onto_manager.setOntologyLoaderConfiguration(config);
		
		
		
//		String ontoLabel = "MIO";
			StoreMediatingOntologies moStorer = new StoreMediatingOntologies();
//		OWLOntology moDownload = moStorer.CallBioPortal(ontoLabel, basePath);
//		moStorer.saveOntology(ontoLabel, moDownload, basePath);
		
			
		/*
		 * Loop through the list of ontologies and load them one by one to then perform composed mapping	
		 */
		try {
			
			// Load source and target
			System.out.println("Loading the first ontology " + onto1_iri);
			OWLOntology onto1 = onto_manager.loadOntology(IRI.create(onto1_iri));
			System.out.println("Loading the second ontology " + onto2_iri);
			OWLOntology onto2 = onto_manager.loadOntology(IRI.create(onto2_iri));
			
			// Load mediating ontologies
			List<String> moList = moStorer.getOntologyListFromFile(listFile);
			int countOnto = moList.size();
			System.out.println("There are " + countOnto + "mediating ontologies in the list");
			
			int counter = 1;
			for (String ontoStr: moList) {
				OWLOntology mo_i = null;
				System.out.println("Fetching ontology No.  " + counter + " label:  " +  ontoStr);

				// TODO if file exists, skip
				System.out.println(basePath + ontoStr + ".owl");
				boolean isOntoThere = moStorer.checkOntoPath(ontoStr, basePath, ".owl");
				boolean isMappingThere = moStorer.checkOntoPath(ontoStr, outPath,".txt");
				if (isOntoThere == true && isMappingThere== false) {
					String ontoPath = "file:" + basePath + ontoStr + ".owl";
					System.out.println("Loading the mediating ontology " + ontoPath);
					mo_i = onto_manager.loadOntology(IRI.create(ontoPath));
					
				}else {
					System.out.println("Ontology " +  ontoStr + " not found, skipping");
					
					continue;
				}
				// Create composed mapping for mediating ontology mo_i
				

			
				CreateComposedMappings mapComposer = new CreateComposedMappings();
				Set<MappingObjectStr>   composedMappings = mapComposer.triangulateOntologies(onto1, onto2, mo_i);
				// Set<MappingObjectStr> s2m = mapComposer.s2mMaps;
				// Set<MappingObjectStr> m2t = mapComposer.m2tMaps;
				// Set<MappingObjectStr>   composedMappings =  mapComposer.aggregateComposedMapping(s2m, m2t);
				System.out.println("There are " + composedMappings.size() + " composed mappings");
				// Save these mappings somewhere
				OutPutFilesManager mapSaver = new OutPutFilesManager();
				// 5 = AllFlatFormats
				mapSaver.createOutFiles(outPath + ontoStr, 5, onto1_iri, onto2_iri);
				mapSaver.addMappings(composedMappings);
				mapSaver.closeAndSaveFiles();
				composedMappings = null;
				counter+=1;
			}
			
		}catch( Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("All complete");
		/*
		 * 2. Run logmap to get mappings
		 * 3. Compose mappings
		 * 4. Store mappings
		 * 
		 * 
		 */
		
		
	}
		
		
	}
	
