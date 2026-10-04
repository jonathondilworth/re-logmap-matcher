

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.apibinding.OWLManager;


import uk.ac.ox.krr.logmap2.LogMap2_Matcher;
import uk.ac.ox.krr.logmap2.mappings.objects.MappingObjectStr;

import java.util.Set;


/**
 * 
 * Example of using LogMap's matching facility 
 *  * 
 * @author Ernesto
 *
 */
public class UsingLogMapMatcher {

	
	/*
	 * OWLOntology onto1; OWLOntology onto2;
	 * 
	 * OWLOntologyManager onto_manager;
	 * 
	 */
	
	public UsingLogMapMatcher() {
		
	}

	public Set<MappingObjectStr> createMappings(String onto1_iri, String onto2_iri){
		
		try{
			
			//String onto1_iri = "file:/home/valentina/MyData/onto_bioportal/CVRG_EPOntology.owl";
			//String onto2_iri = "file:/home/valentina/MyData/onto_bioportal/MIO.owl";
			//String onto1_iri = "file:/home/ernesto/oaei_2012/fma2nci/oaei2012_FMA_small_overlapping_nci.owl";
			//String onto2_iri = "file:/home/ernesto/oaei_2012/fma2nci/oaei2012_NCI_small_overlapping_fma.owl";
			
			OWLOntologyManager onto_manager = OWLManager.createOWLOntologyManager();
			
			//In case an import is broken
			OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
			
			//Important to reassign value, see https://github.com/owlcs/owlapi/issues/503
			config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
			onto_manager.setOntologyLoaderConfiguration(config);
			System.out.println("Loading the first ontology " + onto1_iri);
			OWLOntology onto1 = onto_manager.loadOntology(IRI.create(onto1_iri));
			System.out.println("Loading the second ontology " + onto2_iri);
			OWLOntology onto2 = onto_manager.loadOntology(IRI.create(onto2_iri));
			
			System.out.println("Starting the matching task using LogMap2_Matcher");
			LogMap2_Matcher logmap2 = new LogMap2_Matcher(onto1, onto2);
			//Optionally LogMap also accepts the IRI strings as input 
			//LogMap2_Matcher logmap2 = new LogMap2_Matcher(onto1_iri, onto2_iri);
			System.out.println("Completing the matching task using LogMap2_Matcher");
			//Set of mappings computed my LogMap
			Set<MappingObjectStr> logmap2_mappings = logmap2.getLogmap2_Mappings();
			
			System.out.println("Number of mappings computed by LogMap: " + logmap2_mappings.size());
			return logmap2_mappings;
		}
		catch (Exception e){
			e.printStackTrace();
			
		}
		return null;
		
		
	}
	
public static void printOntologyMappings(Set<MappingObjectStr> onto_mapping){
	
	for (MappingObjectStr mapping: onto_mapping){
		System.out.println("\t Mapping: ");
		System.out.println("Entity from ontology 1 \t"+ mapping.getIRIStrEnt1());
		System.out.println("Entity from ontology 2 \t"+ mapping.getIRIStrEnt2());
		System.out.println("Confidence in the mapping \t"+ mapping.getConfidence());
		
		//MappingObjectStr.EQ or MappingObjectStr.SUB or MappingObjectStr.SUP
		System.out.println("Mapping direction \t"+ mapping.getMappingDirection()); //Utilities.EQ;
		
		//MappingObjectStr.CLASSES or MappingObjectStr.OBJECTPROPERTIES or MappingObjectStr.DATAPROPERTIES or MappingObjectStr.INSTANCES
		System.out.println("Mapping type \t"+ mapping.getTypeOfMapping());
	
		}
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		long startTime = System.nanoTime();
		//String onto1_iri = "file:/home/valentina/MyData/onto_bioportal/CVRG_EPOntology.owl";
		//String onto2_iri = "file:/home/valentina/MyData/onto_bioportal/MIO.owl";
		
		String onto1_iri = "file:/home/valentina/git-repos/test-data/test_onto_input/HPI.owl";
		String onto2_iri = "file:/home/valentina/git-repos/test-data/test_onto_input/PDO_ver0.7.owl";
		UsingLogMapMatcher myLogMap = new UsingLogMapMatcher();
		
		Set<MappingObjectStr> onto_mappings = myLogMap.createMappings(onto1_iri,onto2_iri);
		printOntologyMappings(onto_mappings);
		long endTime = System.nanoTime();
		System.out.println("Map matching task completed.\t" + Math.floor((endTime-startTime)/10e9) + " seconds elapsed");

	}

}
