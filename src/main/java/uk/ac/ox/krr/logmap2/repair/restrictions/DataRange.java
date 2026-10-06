package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLDatatypeRestriction;
import org.semanticweb.owlapi.model.OWLFacetRestriction;

/**
 * A data range the store can name: a datatype, or a facet restriction of one. `datatype`
 * is the IRI of the datatype itself or of the restriction's base; `rendering` is the
 * whole range in words and is what makes two ranges the same proposition. Anything else
 * HermiT can emit (enumerations, unions, complements as fillers) is dropped by the reader.
 * Design spec §3.6.
 */
public record DataRange(String datatype, boolean isFacetRestriction, String rendering) {

    public static final String LITERAL = "http://www.w3.org/2000/01/rdf-schema#Literal";

    public static DataRange of(OWLDatatype datatype) {
        String iri = datatype.getIRI().toString();
        return new DataRange(iri, false, shorten(iri));
    }

    public static DataRange of(OWLDatatypeRestriction restriction) {
        String base = restriction.getDatatype().getIRI().toString();
        List<String> facets = new ArrayList<>();
        for (OWLFacetRestriction facet : restriction.getFacetRestrictions()) {
            facets.add(facet.getFacet().getShortForm() + " " + facet.getFacetValue().getLiteral());
        }
        return new DataRange(base, true, shorten(base) + "[" + String.join(", ", facets) + "]");
    }

    public boolean isLiteral() {
        return datatype.equals(LITERAL) && !isFacetRestriction;
    }

    private static String shorten(String iri) {
        return iri.replace("http://www.w3.org/2001/XMLSchema#", "xsd:")
                .replace("http://www.w3.org/2000/01/rdf-schema#", "rdfs:")
                .replace("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:")
                .replace("http://www.w3.org/2002/07/owl#", "owl:");
    }
}