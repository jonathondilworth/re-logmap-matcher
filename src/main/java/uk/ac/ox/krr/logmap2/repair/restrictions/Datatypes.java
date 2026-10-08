package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

/**
 * Containment and disjointness of data ranges, from the built-in datatypes alone: a
 * datatype is below itself, below `rdfs:Literal`, and below its ancestors in the XML
 * Schema towers (the integer types up to `decimal`, the string types up to `string`,
 * `dateTimeStamp` below `dateTime`); a facet restriction is below its base; a single
 * literal is below its datatype and its ancestors. Two ranges are disjoint when their
 * datatypes belong to different value spaces (numbers, `float`, `double`, strings,
 * booleans, times, URIs, binary), and two single literals of one value space when their
 * values are determinately different (the spec's `distinctLit`: numbers and booleans by
 * value; never strings, times, URIs or binary data). No facet arithmetic: two facet
 * restrictions of one base, or an unknown datatype against anything, are never disjoint
 * here, a sound omission. Design spec §6.4, OWL 2 datatype map.
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
        if (sup.isTopDatatype() || sub.equals(sup)) {
            return true;
        }
        if (sup.isFacetRestriction() || sup.isSingleton()) {
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
        if (firstFamily == null || secondFamily == null) {
            return false;
        }
        if (!firstFamily.equals(secondFamily)) {
            return true;
        }
        return first.isSingleton() && second.isSingleton() && haveDistinctValues(first, second, firstFamily);
    }

    /** Two literals of one value space with determinately different values; a literal that does not parse, or is not finite, decides nothing. */
    private static boolean haveDistinctValues(DataRange first, DataRange second, String family) {
        try {
            if (family.equals("number")) {
                BigDecimal firstValue = new BigDecimal(first.literal().trim());
                BigDecimal secondValue = new BigDecimal(second.literal().trim());
                return firstValue.compareTo(secondValue) != 0;
            }
            if (family.equals(XSD + "float") || family.equals(XSD + "double")) {
                double firstValue = Double.parseDouble(first.literal().trim());
                double secondValue = Double.parseDouble(second.literal().trim());
                return Double.isFinite(firstValue) && Double.isFinite(secondValue) && firstValue != secondValue;
            }
            if (family.equals("boolean")) {
                Boolean firstValue = booleanValue(first.literal());
                Boolean secondValue = booleanValue(second.literal());
                return firstValue != null && secondValue != null && !firstValue.equals(secondValue);
            }
        } catch (NumberFormatException notANumber) {
            return false;
        }
        return false;
    }

    private static Boolean booleanValue(String lexicalForm) {
        switch (lexicalForm.trim()) {
            case "true":
            case "1":
                return Boolean.TRUE;
            case "false":
            case "0":
                return Boolean.FALSE;
            default:
                return null;
        }
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