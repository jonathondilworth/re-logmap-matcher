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

/**
 * The restrictions of one ontology pair as propositions, with the Horn clauses that tie
 * them to named and fresh classes and the object-property inclusions, read from HermiT's
 * normalisation of each ontology. Propositions are ints: LogMap's own identifiers for
 * named classes, and identifiers above every LogMap identifier for TOP, fresh classes and
 * restrictions, so that they can enter Dowling–Gallier's propositional theory unchanged.
 * The store is filled once per ontology while the ontology is alive, and read at every
 * repair.
 */
public final class RestrictionStore {

    private final IndexManager index;
    private final int top;
    
    private int nextIdentifier;

    private final Map<Restriction, Integer> identifierOfRestriction = new HashMap<>();
    private final SortedMap<Integer, Restriction> restrictionOfIdentifier = new TreeMap<>();

    private final Map<String, Integer> identifierOfFreshClass = new HashMap<>();
    private final SortedMap<Integer, String> freshClassOfIdentifier = new TreeMap<>();

    private final Set<HornInclusion> inclusions = new LinkedHashSet<>();
    private final SortedMap<Integer, SortedSet<Integer>> superPropertiesOf = new TreeMap<>();

    private final List<DroppedClause> dropped = new ArrayList<>();
    
    private int clausesCarriedByIndex = 0;


    private RestrictionStore(IndexManager index) {
        this.index = index;
        this.top = index.getLargestAllocatedIdentifier() + 1;
        this.nextIdentifier = top + 1;
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
        OWLNormalizationAdapted normaliser = new OWLNormalizationAdapted(
                OWLManager.getOWLDataFactory(), normalised, 0);
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

    void addSubProperty(int subProperty, int superProperty) {
        superPropertiesOf.computeIfAbsent(subProperty, property -> new TreeSet<>()).add(superProperty);
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

    /** The asserted super-properties of a property (named properties only). */
    public SortedSet<Integer> superPropertiesOf(int property) {
        SortedSet<Integer> superProperties = superPropertiesOf.get(property);
        if (superProperties == null) {
            return Collections.emptySortedSet();
        }
        return Collections.unmodifiableSortedSet(superProperties);
    }

    public SortedSet<Integer> propertiesWithSuperProperties() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(superPropertiesOf.keySet()));
    }

    public List<DroppedClause> dropped() {
        return Collections.unmodifiableList(dropped);
    }

    // description

    /** A proposition in words: `o1:A1`, `o2:def#0`, `TOP`, `some(o1:p1, o1:C1)`. */
    public String describe(int proposition) {
        if (proposition == HornInclusion.FALSE) {
            return "FALSE";
        }
        if (proposition == top) {
            return "TOP";
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
        String property = describeProperty(restriction.property());
        String filler = describe(restriction.filler());
        if (restriction.kind() == RestrictionKind.SOME || restriction.kind() == RestrictionKind.ONLY) {
            return restriction.kind().displayName() + "(" + property + ", " + filler + ")";
        }
        return restriction.kind().displayName() + "(" + restriction.cardinality() + ", " + property + ", " + filler + ")";
    }

    public String describeProperty(int property) {
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
        lines.add(restrictions().size() + " restrictions, " + inclusions.size() + " clauses, "
                + superPropertiesOf.size() + " properties with super-properties, "
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