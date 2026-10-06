package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.HashMap;
import java.util.Map;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

/**
 * Containment and disjointness of data ranges, from the built-in datatypes alone: a
 * datatype is below itself, below `rdfs:Literal`, and below its ancestors in the XML
 * Schema towers (the integer types up to `decimal`, the string types up to `string`,
 * `dateTimeStamp` below `dateTime`); a facet restriction is below its base. Two ranges
 * are disjoint when their datatypes belong to different value spaces (numbers, `float`,
 * `double`, strings, booleans, times, URIs, binary). No literal or facet arithmetic:
 * two facet restrictions of one base, or an unknown datatype against anything, are never
 * disjoint here, a sound omission. Design spec §6.4, OWL 2 datatype map.
 */
final class Datatypes {

    private static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    private static final Map<String, String> PARENT = new HashMap<>();

    static {
        parent("byte", "short");
        parent("short", "int");
        parent("int", "long");
        parent("long", "integer");
        parent("unsignedByte", "unsignedShort");
        parent("unsignedShort", "unsignedInt");
        parent("unsignedInt", "unsignedLong");
        parent("unsignedLong", "nonNegativeInteger");
        parent("positiveInteger", "nonNegativeInteger");
        parent("nonNegativeInteger", "integer");
        parent("negativeInteger", "nonPositiveInteger");
        parent("nonPositiveInteger", "integer");
        parent("integer", "decimal");
        parent("NMTOKEN", "token");
        parent("Name", "token");
        parent("NCName", "Name");
        parent("language", "token");
        parent("token", "normalizedString");
        parent("normalizedString", "string");
        parent("dateTimeStamp", "dateTime");
        PARENT.put(XSD + "string", "http://www.w3.org/1999/02/22-rdf-syntax-ns#PlainLiteral");
    }

    private Datatypes() {
    }

    static boolean contains(DataRange sub, DataRange sup) {
        if (sup.isLiteral() || sub.equals(sup)) {
            return true;
        }
        if (sup.isFacetRestriction()) {
            return false;
        }
        for (String ancestor = sub.datatype(); ancestor != null; ancestor = PARENT.get(ancestor)) {
            if (ancestor.equals(sup.datatype())) {
                return true;
            }
        }
        return false;
    }

    static boolean disjoint(DataRange first, DataRange second) {
        String firstFamily = family(first.datatype());
        String secondFamily = family(second.datatype());
        return firstFamily != null && secondFamily != null && !firstFamily.equals(secondFamily);
    }

    /** The value space a datatype belongs to, or null when it is not a built-in datatype. */
    private static String family(String datatype) {
        IRI iri = IRI.create(datatype);
        if (!OWL2Datatype.isBuiltIn(iri)) {
            return null;
        }
        if (datatype.equals(XSD + "float") || datatype.equals(XSD + "double")) {
            return datatype;
        }
        switch (OWL2Datatype.getDatatype(iri).getCategory()) {
            case CAT_NUMBER: return "number";
            case CAT_STRING_WITH_LANGUAGE_TAG:
            case CAT_STRING_WITHOUT_LANGUAGE_TAG: return "string";
            case CAT_BOOLEAN: return "boolean";
            case CAT_TIME: return "time";
            case CAT_URI: return "uri";
            case CAT_BINARY: return "binary";
            default: return null;
        }
    }

    private static void parent(String child, String parent) {
        PARENT.put(XSD + child, XSD + parent);
    }
}