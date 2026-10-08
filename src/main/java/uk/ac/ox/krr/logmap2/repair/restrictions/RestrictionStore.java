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
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLogicalAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;

import uk.ac.ox.krr.logmap2.indexing.IndexManager;
import uk.ac.ox.krr.logmap2.io.LogOutput;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;

/**
 * The restrictions of one ontology pair as propositions, with the Horn clauses that tie
 * them to named and fresh classes and the property inclusions, read from HermiT's
 * normalisation of each ontology. Propositions are ints: LogMap's own identifiers for
 * named classes, and identifiers above every LogMap identifier for TOP, the data top,
 * fresh classes, nominals, unions, data ranges and restrictions, so that they can enter
 * Dowling–Gallier's propositional theory unchanged.
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

    private final Map<String, Integer> identifierOfNominal = new HashMap<>();
    private final SortedMap<Integer, String> nominalOfIdentifier = new TreeMap<>();

    private final Map<List<Integer>, Integer> identifierOfUnion = new HashMap<>();
    private final SortedMap<Integer, List<Integer>> membersOfUnion = new TreeMap<>();

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

        new NormalisedClauseReader(this, index, ontologyNumber).read(normalFormOf(ontology));
    }


    /**
     * HermiT's normal form of the ontology and of what it imports, in one invocation of the
     * normaliser (its fresh-class numbering restarts with each): the index reads the imports
     * closure too, so an axiom of an imported ontology must reach the rules as its own do.
     * The normaliser's own entry point would read the ontology's own axioms alone. The
     * normaliser rejects some axioms by throwing (a SWRL rule with a built-in atom, an
     * anonymous individual in a same-individual axiom and others). The ontology is then
     * normalised without the axioms that are rejected when taken one by one, and each of
     * those is reported as dropped. If what is left is rejected as well, nothing of the
     * ontology is read. Either way the theory is weaker, never wrong.
     */
    private OWLAxiomsAdapted normalFormOf(OWLOntology ontology) {
        try {
            OWLAxiomsAdapted normalised = new OWLAxiomsAdapted();
            normaliserInto(normalised).processAxioms(logicalAxiomsWithImports(ontology));
            return normalised;
        } catch (IllegalArgumentException rejection) {
            return normalFormOfTheAcceptedAxioms(ontology);
        }
    }


    private OWLAxiomsAdapted normalFormOfTheAcceptedAxioms(OWLOntology ontology) {
        List<OWLAxiom> accepted = new ArrayList<>();
        int rejected = 0;

        for (OWLAxiom axiom : logicalAxiomsWithImports(ontology)) {
            try {
                normaliserInto(new OWLAxiomsAdapted()).processAxioms(List.of(axiom));
                accepted.add(axiom);
            } catch (IllegalArgumentException rejection) {
                drop(DroppedClause.Reason.REJECTED_AXIOM, axiom + ": " + rejection.getMessage());
                rejected++;
            }
        }
        LogOutput.printAlways("HermiT's normaliser rejects " + rejected + " axiom(s) of "
                + nameOf(ontology) + "; the restriction reasoning goes on without them");

        try {
            OWLAxiomsAdapted normalised = new OWLAxiomsAdapted();
            normaliserInto(normalised).processAxioms(accepted);
            return normalised;
        } catch (IllegalArgumentException rejection) {
            drop(DroppedClause.Reason.REJECTED_ONTOLOGY, nameOf(ontology) + ": "
                    + rejection.getMessage());
            return new OWLAxiomsAdapted();
        }
    }

    
    private static Set<OWLLogicalAxiom> logicalAxiomsWithImports(OWLOntology ontology) {
        return ontology.getLogicalAxioms(Imports.INCLUDED);
    }


    private static String nameOf(OWLOntology ontology) {
        if (ontology.getOntologyID().getOntologyIRI().isPresent()) {
            return ontology.getOntologyID().getOntologyIRI().get().toString();
        }
        return "an ontology without an IRI";
    }


    private static OWLNormalizationAdapted normaliserInto(OWLAxiomsAdapted normalised) {
        return new OWLNormalizationAdapted(OWLManager.getOWLDataFactory(), normalised, 0);
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

    /** A nominal `{a}`, the filler HermiT gives a hasValue. */
    public boolean isNominal(int proposition) {
        return nominalOfIdentifier.containsKey(proposition);
    }

    /** A union of class propositions, the proposition a clause with several heads points to. */
    public boolean isUnion(int proposition) {
        return membersOfUnion.containsKey(proposition);
    }

    /** Every union proposition, in identifier order. */
    public SortedSet<Integer> unions() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(membersOfUnion.keySet()));
    }

    /** The members of a union, in identifier order. */
    public List<Integer> membersOf(int union) {
        List<Integer> members = membersOfUnion.get(union);
        if (members == null) {
            throw new IllegalArgumentException(union + " is not a union proposition");
        }
        return members;
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

    /**
     * The proposition of a fresh class, which HermiT names per ontology, allocated on first sight.
     */
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

    /**
     * The proposition of a nominal, allocated on first sight and keyed by the individual's
     * IRI alone: an individual is one thing whichever ontology mentions it.
     */
    int internNominal(String individualIri) {
        Integer known = identifierOfNominal.get(individualIri);
        if (known != null) {
            return known;
        }
        int identifier = nextIdentifier++;
        identifierOfNominal.put(individualIri, identifier);
        nominalOfIdentifier.put(identifier, individualIri);
        return identifier;
    }

    /**
     * The proposition of a union, allocated on first sight and keyed by its member set,
     * which two ontologies may share; the reader adds the edges from the members.
     */
    int internUnion(Collection<Integer> members) {
        List<Integer> key = new ArrayList<>(new TreeSet<>(members));
        Integer known = identifierOfUnion.get(key);
        if (known != null) {
            return known;
        }
        int identifier = nextIdentifier++;
        identifierOfUnion.put(key, identifier);
        membersOfUnion.put(identifier, Collections.unmodifiableList(key));
        return identifier;
    }


    /** The nominal of an individual some hasValue has mentioned, or null. */
    Integer nominalOf(String individualIri) {
        return identifierOfNominal.get(individualIri);
    }


    /**
     * The proposition of a data range, allocated on first sight; `rdfs:Literal` is the data top.
     */
    int internDataRange(DataRange dataRange) {
        if (dataRange.isTopDatatype()) {
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

    /**
     * `sub ⊑ super` between signed properties (SignedProperties); a data property is always its
     * outgoing token.
     */
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

    /** The asserted super-properties of a signed property of that kind, as signed properties. */
    public SortedSet<Integer> superPropertiesOf(PropertyKind kind, int property) {
        SortedSet<Integer> superProperties = superPropertiesOf.get(kind).get(property);
        if (superProperties == null) {
            return Collections.emptySortedSet();
        }
        return Collections.unmodifiableSortedSet(superProperties);
    }

    public SortedSet<Integer> propertiesWithSuperProperties(PropertyKind kind) {
        SortedSet<Integer> properties = new TreeSet<>(superPropertiesOf.get(kind).keySet());
        return Collections.unmodifiableSortedSet(properties);
    }

    public List<DroppedClause> dropped() {
        return Collections.unmodifiableList(dropped);
    }

    // description

    /**
     * A proposition in words: `o1:A1`, `o2:def#0`, `{a}`, `union(o1:B1, o1:C1)`, `TOP`,
     * `xsd:integer`, `some(o1:p1, o1:C1)`.
     */
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
        if (isNominal(proposition)) {
            return "{" + localName(nominalOfIdentifier.get(proposition)) + "}";
        }
        if (isUnion(proposition)) {
            List<String> members = new ArrayList<>();
            for (int member : membersOf(proposition)) {
                members.add(describe(member));
            }
            Collections.sort(members);
            return "union(" + String.join(", ", members) + ")";
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
        String property = describeSignedProperty(restriction.propertyKind(), restriction.propertyToken());
        if (restriction.kind() == RestrictionKind.SELF) {
            return "self(" + property + ")";
        }
        String filler = describe(restriction.filler());
        if (restriction.kind() == RestrictionKind.SOME || restriction.kind() == RestrictionKind.ONLY) {
            return restriction.kind().displayName() + "(" + property + ", " + filler + ")";
        }
        return restriction.kind().displayName() + "(" + restriction.cardinality() + ", " + property + ", " + filler + ")";
    }

    // public String describeProperty(int objectProperty) {
    //     return describeProperty(PropertyKind.OBJECT, objectProperty);
    // }

    /** A signed property in words: `o1:p1`, or `inv(o1:p1)` for its inverse. */
    public String describeSignedProperty(PropertyKind kind, int token) {
        String property = describeProperty(kind, SignedProperties.identifier(token));
        return SignedProperties.isIncoming(token) ? "inv(" + property + ")" : property;
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
                + nominalOfIdentifier.size() + " nominals, " + membersOfUnion.size() + " unions, "
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