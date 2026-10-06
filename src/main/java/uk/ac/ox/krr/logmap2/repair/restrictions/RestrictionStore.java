package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.semanticweb.HermiT.structural.OWLAxiomsAdapted;
import org.semanticweb.HermiT.structural.OWLNormalizationAdapted;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;

import uk.ac.ox.krr.logmap2.indexing.IndexManager;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;

/**
 * The restrictions of one ontology pair as propositions, with the Horn clauses that tie
 * them to named and fresh classes and the property inclusions, read from HermiT's
 * normalisation of each ontology. Propositions are ints: LogMap's own identifiers for
 * named classes, and identifiers above every LogMap identifier for TOP, the data top,
 * fresh classes, data ranges and restrictions, so that they can enter Dowling–Gallier's
 * propositional theory unchanged.
 * The store is filled once per ontology while the ontology is alive, and read at every
 * repair.
 */
public final class RestrictionStore {

    private final IndexManager index;
    private final int top;
    private final int dataTop;
    
    private int nextIdentifier;

    private final Map<Restriction, Integer> identifierOfRestriction = new HashMap<>();
    private final SortedMap<Integer, Restriction> restrictionOfIdentifier = new TreeMap<>();

    private final Map<String, Integer> identifierOfFreshClass = new HashMap<>();
    private final SortedMap<Integer, String> freshClassOfIdentifier = new TreeMap<>();

    private final Map<DataRange, Integer> identifierOfDataRange = new HashMap<>();
    private final SortedMap<Integer, DataRange> dataRangeOfIdentifier = new TreeMap<>();

    private final Set<HornInclusion> inclusions = new LinkedHashSet<>();
    private final Map<PropertyKind, SortedMap<Integer, SortedSet<Integer>>> superPropertiesOf = new EnumMap<>(PropertyKind.class);

    private final List<DroppedClause> dropped = new ArrayList<>();
    
    private int clausesCarriedByIndex = 0;


    private RestrictionStore(IndexManager index) {
        this.index = index;
        this.top = index.getLargestAllocatedIdentifier() + 1;
        this.dataTop = top + 1;
        this.nextIdentifier = dataTop + 1;
        for (PropertyKind kind : PropertyKind.values()) {
            superPropertiesOf.put(kind, new TreeMap<>());
        }
    }


    /**
     * A store whose propositions start right above the index's largest identifier. Both
     * ontologies' lexicons, which allocate every class, property and individual
     * identifier, run before the store is first filled (LogMap2Core.IndexLexiconAndStructure,
     * LogMap2_RepairFacility.setUpStructures); {@link #addOntology} checks that.
     */
    public static RestrictionStore above(IndexManager index) {
        return new RestrictionStore(index);
    }

    
    /** Normalises the ontology with HermiT and reads every clause into the store. */
    public void addOntology(int ontologyNumber, OWLOntology ontology) {
        if (index.getLargestAllocatedIdentifier() >= top) {
            throw new IllegalStateException("the index allocated identifiers after the restriction store"
                    + " reserved its range at " + top + "; the store must be filled after both lexicons");
        }

        OWLAxiomsAdapted normalised = new OWLAxiomsAdapted();
        OWLNormalizationAdapted normaliser = new OWLNormalizationAdapted(OWLManager.getOWLDataFactory(), normalised, 0);
        normaliser.processOntology(ontology);

        new NormalisedClauseReader(this, index, ontologyNumber).read(normalised);
    }

    // propositions

    public int top() {
        return top;
    }

    public boolean isTop(int proposition) {
        return proposition == top;
    }

    /** The top of the data ranges, `rdfs:Literal`: every data value belongs to it. */
    public int dataTop() {
        return dataTop;
    }

    public boolean isDataTop(int proposition) {
        return proposition == dataTop;
    }

    public boolean isDataRange(int proposition) {
        return dataRangeOfIdentifier.containsKey(proposition);
    }

    /** The top filler of restrictions over this kind of property. */
    public boolean isTopFillerFor(PropertyKind kind, int filler) {
        return kind == PropertyKind.OBJECT ? isTop(filler) : isDataTop(filler);
    }

    public boolean isRestriction(int proposition) {
        return restrictionOfIdentifier.containsKey(proposition);
    }

    public boolean isFreshClass(int proposition) {
        return freshClassOfIdentifier.containsKey(proposition);
    }

    /** The proposition of a restriction, allocated on first sight. */
    int intern(Restriction restriction) {
        Integer known = identifierOfRestriction.get(restriction);
        if (known != null) {
            return known;
        }
        int identifier = nextIdentifier++;
        identifierOfRestriction.put(restriction, identifier);
        restrictionOfIdentifier.put(identifier, restriction);
        return identifier;
    }

    /** The proposition of a fresh class, which HermiT names per ontology, allocated on first sight. */
    int internFreshClass(int ontologyNumber, String iri) {
        String key = ontologyNumber + " " + iri;
        Integer known = identifierOfFreshClass.get(key);
        if (known != null) {
            return known;
        }
        int identifier = nextIdentifier++;
        identifierOfFreshClass.put(key, identifier);
        freshClassOfIdentifier.put(identifier, key);
        return identifier;
    }

    /** The proposition of a data range, allocated on first sight; `rdfs:Literal` is the data top. */
    int internDataRange(DataRange dataRange) {
        if (dataRange.isLiteral()) {
            return dataTop;
        }
        Integer known = identifierOfDataRange.get(dataRange);
        if (known != null) {
            return known;
        }
        int identifier = nextIdentifier++;
        identifierOfDataRange.put(dataRange, identifier);
        dataRangeOfIdentifier.put(identifier, dataRange);
        return identifier;
    }

    public DataRange dataRange(int proposition) {
        DataRange dataRange = dataRangeOfIdentifier.get(proposition);
        if (dataRange == null) {
            throw new IllegalArgumentException(proposition + " is not a data range proposition");
        }
        return dataRange;
    }


    public Restriction restriction(int proposition) {
        Restriction restriction = restrictionOfIdentifier.get(proposition);
        if (restriction == null) {
            throw new IllegalArgumentException(proposition + " is not a restriction proposition");
        }
        return restriction;
    }

    public Collection<Restriction> restrictions() {
        return Collections.unmodifiableCollection(restrictionOfIdentifier.values());
    }

    /** The restrictions over properties of one kind, in identifier order. */
    public List<Restriction> restrictions(PropertyKind kind) {
        List<Restriction> ofKind = new ArrayList<>();
        for (Restriction restriction : restrictionOfIdentifier.values()) {
            if (restriction.propertyKind() == kind) {
                ofKind.add(restriction);
            }
        }
        return ofKind;
    }


    public int identifierOf(Restriction restriction) {
        Integer identifier = identifierOfRestriction.get(restriction);
        if (identifier == null) {
            throw new IllegalArgumentException("unknown restriction " + restriction);
        }
        return identifier;
    }

    // clauses and property facts

    void add(HornInclusion inclusion) {
        inclusions.add(inclusion);
    }

    void addSubProperty(PropertyKind kind, int subProperty, int superProperty) {
        superPropertiesOf.get(kind).computeIfAbsent(subProperty, property -> new TreeSet<>()).add(superProperty);
    }

    void countCarriedByIndex() {
        clausesCarriedByIndex++;
    }

    void drop(DroppedClause.Reason reason, String description) {
        dropped.add(new DroppedClause(reason, description));
    }

    public Collection<HornInclusion> inclusions() {
        return Collections.unmodifiableCollection(inclusions);
    }

    /** The asserted super-properties of a property of that kind (named properties only). */
    public SortedSet<Integer> superPropertiesOf(PropertyKind kind, int property) {
        SortedSet<Integer> superProperties = superPropertiesOf.get(kind).get(property);
        if (superProperties == null) {
            return Collections.emptySortedSet();
        }
        return Collections.unmodifiableSortedSet(superProperties);
    }

    public SortedSet<Integer> propertiesWithSuperProperties(PropertyKind kind) {
        return Collections.unmodifiableSortedSet(new TreeSet<>(superPropertiesOf.get(kind).keySet()));
    }

    public List<DroppedClause> dropped() {
        return Collections.unmodifiableList(dropped);
    }

    // description

    /** A proposition in words: `o1:A1`, `o2:def#0`, `TOP`, `xsd:integer`, `some(o1:p1, o1:C1)`. */
    public String describe(int proposition) {
        if (proposition == HornInclusion.FALSE) {
            return "FALSE";
        }
        if (proposition == top) {
            return "TOP";
        }
        if (proposition == dataTop) {
            return "rdfs:Literal";
        }
        if (isDataRange(proposition)) {
            return dataRange(proposition).rendering();
        }
        if (isRestriction(proposition)) {
            return describe(restriction(proposition));
        }
        if (isFreshClass(proposition)) {
            String[] ontologyAndIri = freshClassOfIdentifier.get(proposition).split(" ", 2);
            int ontologyNumber = Integer.parseInt(ontologyAndIri[0]);
            String freshName = ontologyAndIri[1].substring(ontologyAndIri[1].indexOf(':') + 1);
            return "o" + (ontologyNumber + 1) + ":" + freshName;
        }
        int ontologyNumber = index.getClassIndex(proposition).getOntologyId();
        return "o" + (ontologyNumber + 1) + ":" + localName(index.getIRIStr4ConceptIndex(proposition));
    }

    public String describe(Restriction restriction) {
        String property = describeProperty(restriction.propertyKind(), restriction.property());
        String filler = describe(restriction.filler());
        if (restriction.kind() == RestrictionKind.SOME || restriction.kind() == RestrictionKind.ONLY) {
            return restriction.kind().displayName() + "(" + property + ", " + filler + ")";
        }
        return restriction.kind().displayName() + "(" + restriction.cardinality() + ", " + property + ", " + filler + ")";
    }

    public String describeProperty(int objectProperty) {
        return describeProperty(PropertyKind.OBJECT, objectProperty);
    }

    public String describeProperty(PropertyKind kind, int property) {
        if (kind == PropertyKind.DATA) {
            int ontologyNumber = index.getDataPropertyIndex(property).getOntologyId();
            return "o" + (ontologyNumber + 1) + ":" + localName(index.getIRIStr4DataPropIndex(property));
        }
        int ontologyNumber = index.getObjectPropertyIndex(property).getOntologyId();
        return "o" + (ontologyNumber + 1) + ":" + localName(index.getIRIStr4ObjPropIndex(property));
    }

    /** The clause in words, body atoms in alphabetical order for reading. */
    public String describe(HornInclusion inclusion) {
        List<String> body = new ArrayList<>();
        for (int proposition : inclusion.body()) {
            body.add(describe(proposition));
        }
        Collections.sort(body);
        return String.join(" ^ ", body) + " -> " + describe(inclusion.head());
    }

    /** The report of what was read and what was not, one line per item. */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add(restrictions().size() + " restrictions, " + dataRangeOfIdentifier.size() + " data ranges, "
                + inclusions.size() + " clauses, "
                + superPropertiesOf.get(PropertyKind.OBJECT).size() + " object properties and "
                + superPropertiesOf.get(PropertyKind.DATA).size() + " data properties with super-properties, "
                + clausesCarriedByIndex + " named-only clauses left to the index, "
                + dropped.size() + " dropped");

        Map<DroppedClause.Reason, Integer> countByReason = new EnumMap<>(DroppedClause.Reason.class);
        for (DroppedClause clause : dropped) {
            countByReason.merge(clause.reason(), 1, Integer::sum);
        }
        for (Map.Entry<DroppedClause.Reason, Integer> count : countByReason.entrySet()) {
            lines.add("dropped " + count.getValue() + ": " + count.getKey().explanation());
        }
        for (DroppedClause clause : dropped) {
            lines.add("    " + clause.reason() + " " + clause.description());
        }
        return lines;
    }

    private static String localName(String iri) {
        int hash = iri.lastIndexOf('#');
        int slash = iri.lastIndexOf('/');
        return iri.substring(Math.max(hash, slash) + 1);
    }
}