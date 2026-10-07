package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLDatatypeRestriction;
import org.semanticweb.owlapi.model.OWLFacetRestriction;
import org.semanticweb.owlapi.model.OWLLiteral;

/**
 * A data range the store can name: a datatype, a facet restriction of one, or a single
 * literal (`{"x"^^xsd:string}`, HermiT's form of a data hasValue). `datatype` is the IRI
 * of the datatype itself, of the restriction's base or of the literal's datatype;
 * `literal` is the lexical form of a singleton and null otherwise; `rendering` is the
 * whole range in words and is what makes two ranges the same proposition. Anything else
 * HermiT can emit (enumerations of several literals, unions, complements as fillers) is
 * dropped by the reader.
 */
public record DataRange(String datatype, boolean isFacetRestriction, String literal, String rendering) {

    public static final String LITERAL = "http://www.w3.org/2000/01/rdf-schema#Literal";

    public static DataRange of(OWLDatatype datatype) {
        String iri = datatype.getIRI().toString();
        return new DataRange(iri, false, null, shorten(iri));
    }

    public static DataRange of(OWLDatatypeRestriction restriction) {
        String base = restriction.getDatatype().getIRI().toString();
        List<String> facets = new ArrayList<>();
        for (OWLFacetRestriction facet : restriction.getFacetRestrictions()) {
            facets.add(facet.getFacet().getShortForm() + " " + facet.getFacetValue().getLiteral());
        }
        return new DataRange(base, true, null, shorten(base) + "[" + String.join(", ", facets) + "]");
    }

    public static DataRange of(OWLLiteral literal) {
        String datatype = literal.getDatatype().getIRI().toString();
        String tag = literal.hasLang() ? "@" + literal.getLang() : "^^" + shorten(datatype);
        return new DataRange(datatype, false, literal.getLiteral(), "{\"" + literal.getLiteral() + "\"" + tag + "}");
    }

    public boolean isSingleton() {
        return literal != null;
    }

    /** `rdfs:Literal`, the data top. */
    public boolean isLiteral() {
        return datatype.equals(LITERAL) && !isFacetRestriction && !isSingleton();
    }

    private static String shorten(String iri) {
        return iri.replace("http://www.w3.org/2001/XMLSchema#", "xsd:")
                .replace("http://www.w3.org/2000/01/rdf-schema#", "rdfs:")
                .replace("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf:")
                .replace("http://www.w3.org/2002/07/owl#", "owl:");
    }
}