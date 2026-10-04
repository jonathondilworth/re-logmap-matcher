package uk.ac.ox.krr.logmap2.mediating_ontologies;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import uk.ac.ox.krr.logmap2.LogMap2_Matcher;
import uk.ac.ox.krr.logmap2.bioportal.MediatingOntologyExtractor;

public class FetchMediatingOntologies {

	public FetchMediatingOntologies() {
	}

	public List<String> extractMediatingOntologyList(LogMap2_Matcher onto_mappings) {
		
		Set<String> representative_labels = onto_mappings.getRepresentativeLabelsForMappings();
	
		MediatingOntologyExtractor mo_extract = new MediatingOntologyExtractor(representative_labels);
	
		List<String> mediating_ontologies = mo_extract.getSelectedMediatingOntologies();
		return mediating_ontologies;
	}
	
	public void printMediatingOntologies(List<String> selectedMediatingOntologies) {

		if (selectedMediatingOntologies.size() < 1) {
			System.out.println("No mediating ontologies found");

		} else {
			for (int i = 0; i < selectedMediatingOntologies.size(); i++) {
				System.out.println(selectedMediatingOntologies.get(i));
			}
		}

	}
	
	public void saveListMediatingOntolgies(boolean saveList, List<String> selectedMediatingOntologies, 
			String filePath){
		if (selectedMediatingOntologies.size() < 1) {
			System.out.println("No mediating ontologies found");

		} else {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) 
	        {
	            for (int i = 0; i < selectedMediatingOntologies.size(); i++) {
	                writer.write(selectedMediatingOntologies.get(i));
	                writer.newLine(); 
	            }
	            System.out.println("ArrayList written to file successfully.");
	        } catch (IOException e) {
	            e.printStackTrace();
			
			}
		}
	}

	public static void main(String[] args) {
		// The parent folder should already exist - don't forget the last "/" !
		
		String parentPath = "/home/valentina/git-repos/test-data/test_onto_output/test-mediating-store/";
		String filePath = parentPath + "logmap_top10_mediating_ontologies.txt";
		System.out.println("Fetching");

		// TODO Load mappings from file
		
		System.out.println("Prepare for mediating ontologies");
		//Test mappings created
		String onto1_iri = "file:/home/valentina/Downloads/anatomy-dataset/human.owl"; 
		String onto2_iri = "file:/home/valentina/Downloads/anatomy-dataset/mouse.owl";
		CreateMappingsBetweenTwoOntologies myLogMap = new CreateMappingsBetweenTwoOntologies();
		LogMap2_Matcher onto_mappings = myLogMap.createMappings(onto1_iri, onto2_iri);
		
		long startTime = System.nanoTime();
		FetchMediatingOntologies mo_fetcher = new FetchMediatingOntologies();
		System.out.println("Extracting mediating ontologies");
		List<String> mediating_ontologies = mo_fetcher.extractMediatingOntologyList(onto_mappings);
		long endTime = System.nanoTime();
		System.out.println(
				"Extracted mediating ontologies.\t" + Math.floor((endTime - startTime) / 10e9) + " seconds elapsed");
		
		mo_fetcher.printMediatingOntologies(mediating_ontologies);
		mo_fetcher.saveListMediatingOntolgies(true, mediating_ontologies, filePath);

	}

}