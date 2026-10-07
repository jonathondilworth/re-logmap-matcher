package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import org.semanticweb.HermiT.structural.OWLAxiomsAdapted;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataAllValuesFrom;
import org.semanticweb.owlapi.model.OWLDataComplementOf;
import org.semanticweb.owlapi.model.OWLDataMaxCardinality;
import org.semanticweb.owlapi.model.OWLDataMinCardinality;
import org.semanticweb.owlapi.model.OWLDataOneOf;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDataSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLDatatypeRestriction;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLIndividualAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectHasSelf;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;

import uk.ac.ox.krr.logmap2.indexing.IndexManager;
import uk.ac.ox.krr.logmap2.repair.hornSAT.HornInclusion;
import uk.ac.ox.krr.logmap2.utilities.Utilities;

/**
 * Reads HermiT's normalised clauses of one ontology into the store. Each concept
 * inclusion is a disjunction of literals; a negated class is a body atom, a restriction
 * that is the dual of an atom (`∀R.¬C`, `∀R.⊥`, `∃R.¬C`, `≤n`, `≥n`) is read as that atom
 * in the body, and what remains is the head. Several remaining heads that are all classes
 * (a union: a domain or range over a union, HermiT's definition of a union filler, a
 * plain `A ⊑ B ⊔ C`) become one union proposition above its members; a union with a
 * restriction among its heads is dropped. A clause over named classes only is left to
 * LogMap's index; everything else is dropped with a reason. Data restrictions are read the same way, with data ranges as
 * fillers and `rdfs:Literal` as their top. A hasValue arrives as an existential over a
 * nominal `{a}` (or a single literal) and is read as one; a hasSelf is a restriction of its
 * own with the companion `hasSelf(R) → ∃R.⊤`; reflexivity and irreflexivity are clauses on
 * that restriction; the types and the distinctness of the individuals under a hasValue
 * are clauses on their nominals. A restriction over an inverse (`∃R⁻.C`) is an incoming
 * restriction over R, an inclusion with an inverse side a fact between signed
 * properties, and a domain or range yields its twin read through the inverse (`∃R.⊤ ⊑ D`
 * is `⊤ ⊑ ∀R⁻.D`).
 */
final class NormalisedClauseReader {

    private static final String FRESH_CLASS_PREFIX = "internal:def#";

    private final RestrictionStore store;
    private final IndexManager index;
    private final int ontologyNumber;

    NormalisedClauseReader(RestrictionStore store, IndexManager index, int ontologyNumber) {
        this.store = store;
        this.index = index;
        this.ontologyNumber = ontologyNumber;
    }

    void read(OWLAxiomsAdapted normalised) {
        for (OWLClassExpression[] disjuncts : normalised.getNormalisedConceptInclusions()) {
            readConceptInclusion(disjuncts);
        }
        for (OWLObjectPropertyExpression[] inclusion : normalised.getSimpleObjectPropertyInclusions()) {
            readPropertyInclusion(inclusion[0], inclusion[1]);
        }
        for (OWLDataPropertyExpression[] inclusion : normalised.getDataPropertyInclusions()) {
            readDataPropertyInclusion(inclusion[0], inclusion[1]);
        }
        for (OWLObjectPropertyExpression property : normalised.getReflexiveObjectProperties()) {
            readReflexivity(property, false);
        }
        for (OWLObjectPropertyExpression property : normalised.getIrreflexiveObjectProperties()) {
            readReflexivity(property, true);
        }
        for (OWLIndividualAxiom fact : normalised.getFacts()) {
            readFact(fact);
        }
        dropAll(normalised.getComplexObjectPropertyInclusionsAsChains(), DroppedClause.Reason.PROPERTY_CHAIN);
        dropAll(normalised.getDisjointObjectProperties(), DroppedClause.Reason.DISJOINT_PROPERTIES);
        dropAll(normalised.getAsymmetricObjectProperties(), DroppedClause.Reason.PROPERTY_CHARACTERISTIC);
        dropAll(normalised.getDisjointDataProperties(), DroppedClause.Reason.DATA_AXIOM);
        dropAll(normalised.getDataRangeInclusions(), DroppedClause.Reason.DATA_AXIOM);
        dropAll(normalised.getHasKeys(), DroppedClause.Reason.KEY);
    }

    // CONCEPT INCLUSIONS

    /**
     * A literal's reading: a body atom, or a head that may still be read as its dual body
     * atom (`≤n` as `≥n+1`, `≥n` as `≤n-1`); the dual is interned only if the clause needs it.
     */
    private record Literal(int proposition, boolean inBody, Restriction dual) {

        static Literal body(int proposition) {
            return new Literal(proposition, true, null);
        }

        static Literal head(int proposition) {
            return new Literal(proposition, false, null);
        }

        static Literal headWithDual(int proposition, Restriction dual) {
            return new Literal(proposition, false, dual);
        }

        boolean hasDual() {
            return dual != null;
        }
    }


    private void readConceptInclusion(OWLClassExpression[] disjuncts) {
        List<Literal> literals = new ArrayList<>();
        boolean mentionsRestrictionOrFreshClass = false;

        for (OWLClassExpression disjunct : disjuncts) {
            if (isTautology(disjunct)) {
                return;
            }
            if (isContradiction(disjunct)) {
                continue;
            }
            Literal literal;
            try {
                literal = readLiteral(disjunct);
            } catch (Unreadable unreadable) {
                store.drop(unreadable.reason, describe(disjuncts));
                return;
            }
            literals.add(literal);
            mentionsRestrictionOrFreshClass |= isOfTheStore(literal);
        }

        List<Integer> body = new ArrayList<>();
        List<Literal> fixedHeads = new ArrayList<>();
        List<Literal> dualHeads = new ArrayList<>();

        for (Literal literal : literals) {
            if (literal.inBody()) {
                body.add(literal.proposition());
            } else if (literal.hasDual()) {
                dualHeads.add(literal);
            } else {
                fixedHeads.add(literal);
            }
        }

        int head;
        if (fixedHeads.size() > 1) {
            for (Literal member : fixedHeads) {
                if (store.isRestriction(member.proposition())) {
                    store.drop(DroppedClause.Reason.NOT_HORN, describe(disjuncts));
                    return;
                }
            }
            head = unionProposition(fixedHeads);
            mentionsRestrictionOrFreshClass = true;
        } else if (fixedHeads.size() == 1) {
            head = fixedHeads.get(0).proposition();
        } else if (!dualHeads.isEmpty()) {
            head = dualHeads.remove(0).proposition();
        } else {
            head = HornInclusion.FALSE;
        }
        for (Literal dualHead : dualHeads) {
            body.add(store.intern(dualHead.dual()));
            mentionsRestrictionOrFreshClass = true;
        }

        if (!mentionsRestrictionOrFreshClass) {
            store.countCarriedByIndex();
            return;
        }
        if (body.isEmpty()) {
            body.add(store.top());
        }
        store.add(HornInclusion.of(body, head));
        addOrientationTwin(body, head);
    }

    /**
     * A domain and a range are one fact read two ways: `∃R.⊤ ⊑ D` is `⊤ ⊑ ∀R⁻.D` and
     * `⊤ ⊑ ∀R.E` is `∃R⁻.⊤ ⊑ E`; each gets its twin, so that an incoming restriction meets
     * it through the ordinary rules (design spec §3.5a, the dom facts).
     */
    private void addOrientationTwin(List<Integer> body, int head) {
        if (body.size() != 1) {
            return;
        }
        int single = body.get(0);
        if (store.isRestriction(single) && head != HornInclusion.FALSE && !store.isRestriction(head)) {
            Restriction domain = store.restriction(single);
            if (domain.propertyKind() == PropertyKind.OBJECT && domain.kind() == RestrictionKind.SOME && store.isTop(domain.filler())) {
                store.add(HornInclusion.of(List.of(store.top()),
                        store.intern(Restriction.only(domain.property(), !domain.incoming(), head))));
            }
        } else if (store.isTop(single) && store.isRestriction(head)) {
            Restriction range = store.restriction(head);
            if (range.propertyKind() == PropertyKind.OBJECT && range.isUniversal()) {
                store.add(HornInclusion.of(
                        List.of(store.intern(Restriction.some(range.property(), !range.incoming(), store.top()))), range.filler()));
            }
        }
    }


    /** The union of several class heads, with the edge `member → union` for each, added on first sight. */
    private int unionProposition(List<Literal> members) {
        List<Integer> propositions = new ArrayList<>();
        for (Literal member : members) {
            propositions.add(member.proposition());
        }
        int union = store.internUnion(propositions);
        for (int member : store.membersOf(union)) {
            store.add(HornInclusion.of(List.of(member), union));
         }
        return union;
     }


    /** A proposition the index does not know: a restriction, a fresh class or a nominal. */
    private boolean isOfTheStore(Literal literal) {
        return store.isRestriction(literal.proposition()) || store.isFreshClass(literal.proposition())
                || store.isNominal(literal.proposition());
     }


    private Literal readLiteral(OWLClassExpression disjunct) {
        if (disjunct instanceof OWLClass owlClass) {
            return Literal.head(classProposition(owlClass));
        }
        if (disjunct instanceof OWLObjectComplementOf complement) {
            return readNegatedLiteral(complement.getOperand());
        }
        if (disjunct instanceof OWLObjectSomeValuesFrom some) {
            return readExistential(some.getProperty(), 1, some.getFiller());
        }
        if (disjunct instanceof OWLObjectMinCardinality atLeast) {
            return readExistential(atLeast.getProperty(), atLeast.getCardinality(), atLeast.getFiller());
        }
        if (disjunct instanceof OWLObjectAllValuesFrom only) {
            return readUniversal(only.getProperty(), only.getFiller());
        }
        if (disjunct instanceof OWLObjectMaxCardinality atMost) {
            return readAtMost(atMost.getProperty(), atMost.getCardinality(), atMost.getFiller());
        }
        if (disjunct instanceof OWLDataSomeValuesFrom some) {
            return readDataExistential(some.getProperty(), 1, some.getFiller());
        }
        if (disjunct instanceof OWLDataMinCardinality atLeast) {
            return readDataExistential(atLeast.getProperty(), atLeast.getCardinality(), atLeast.getFiller());
        }
        if (disjunct instanceof OWLDataAllValuesFrom only) {
            return readDataUniversal(only.getProperty(), only.getFiller());
        }
        if (disjunct instanceof OWLDataMaxCardinality atMost) {
            return readDataAtMost(atMost.getProperty(), atMost.getCardinality(), atMost.getFiller());
        }
        if (disjunct instanceof OWLObjectHasSelf self) {
            return Literal.head(selfProposition(self.getProperty()));
        }
        if (disjunct instanceof OWLObjectOneOf nominal) {
            return Literal.head(nominalProposition(nominal));
        }
        throw new Unreadable(reasonFor(disjunct));
    }


    private Literal readNegatedLiteral(OWLClassExpression operand) {
        if (operand instanceof OWLClass owlClass) {
            return Literal.body(classProposition(owlClass));
        }
        if (operand instanceof OWLObjectHasSelf self) {
            return Literal.body(selfProposition(self.getProperty()));
        }
        if (operand instanceof OWLObjectOneOf nominal) {
            return Literal.body(nominalProposition(nominal));
        }
        throw new Unreadable(reasonFor(operand));
    }


    /** `hasSelf(R)`, with its companion `hasSelf(R) → ∃R.⊤` added on first sight; `hasSelf(R⁻)` is the same self-edge. */
    private int selfProposition(OWLObjectPropertyExpression property) {
        int propertyIdentifier = propertyIdentifier(property);
        int self = store.intern(Restriction.self(propertyIdentifier, store.top()));
        store.add(HornInclusion.of(List.of(self), store.intern(Restriction.some(propertyIdentifier, store.top()))));
        return self;
    }

    /** `{a}`, a single named individual; several individuals are an enumeration, which is dropped. */
    private int nominalProposition(OWLObjectOneOf nominal) {
        if (nominal.getIndividuals().size() != 1) {
            throw new Unreadable(DroppedClause.Reason.ENUMERATION);
        }
        OWLIndividual individual = nominal.getIndividuals().iterator().next();
        if (individual.isAnonymous()) {
            throw new Unreadable(DroppedClause.Reason.OTHER);
        }
        return store.internNominal(individual.asOWLNamedIndividual().getIRI().toString());
    }



    /** `∃R.C` and `≥n R.C`, whose dual (a `≤n-1`) is an atom only for n ≥ 2. */
    private Literal readExistential(OWLObjectPropertyExpression property, int cardinality, OWLClassExpression filler) {
        int propertyIdentifier = propertyIdentifier(property);
        boolean incoming = property.isAnonymous();
        if (filler instanceof OWLObjectComplementOf complement) {
            if (cardinality != 1) {
                throw new Unreadable(DroppedClause.Reason.COMPLEMENT_FILLER);
            }
            return Literal.body(store.intern(Restriction.only(propertyIdentifier, incoming, fillerProposition(complement.getOperand()))));
        }
        
        int fillerIdentifier = fillerProposition(filler);
        int proposition = store.intern(Restriction.atLeast(cardinality, propertyIdentifier, incoming, fillerIdentifier));
        
        if (cardinality >= 2) {
            return Literal.headWithDual(proposition, Restriction.atMost(cardinality - 1, propertyIdentifier, incoming, fillerIdentifier));
        }
        
        return Literal.head(proposition);
    }


    /** `∀R.C`; `∀R.¬C` is the body atom `∃R.C`, `∀R.⊥` the body atom `∃R.⊤`. */
    private Literal readUniversal(OWLObjectPropertyExpression property, OWLClassExpression filler) {
        int propertyIdentifier = propertyIdentifier(property);
        boolean incoming = property.isAnonymous();
        if (filler instanceof OWLObjectComplementOf complement) {
            return Literal.body(store.intern(Restriction.some(propertyIdentifier, incoming, fillerProposition(complement.getOperand()))));
        }
        if (filler.isOWLNothing()) {
            return Literal.body(store.intern(Restriction.some(propertyIdentifier, incoming, store.top())));
        }
        return Literal.head(store.intern(Restriction.only(propertyIdentifier, incoming, fillerProposition(filler))));
    }

    /** `≤n R.C`, whose dual is the body atom `≥n+1 R.C`. */
    private Literal readAtMost(OWLObjectPropertyExpression property, int cardinality, OWLClassExpression filler) {
        int propertyIdentifier = propertyIdentifier(property);
        boolean incoming = property.isAnonymous();
        if (filler instanceof OWLObjectComplementOf) {
            throw new Unreadable(DroppedClause.Reason.COMPLEMENT_FILLER);
        }
        int fillerIdentifier = fillerProposition(filler);
        if (cardinality == 0) {
            return Literal.body(store.intern(Restriction.some(propertyIdentifier, incoming, fillerIdentifier)));
        }
        int proposition = store.intern(Restriction.atMost(cardinality, propertyIdentifier, incoming, fillerIdentifier));
        return Literal.headWithDual(proposition, Restriction.atLeast(cardinality + 1, propertyIdentifier, incoming, fillerIdentifier));
    }


    // the same readings over data properties, with data ranges as fillers

    private Literal readDataExistential(OWLDataPropertyExpression property, int cardinality, OWLDataRange filler) {
        int propertyIdentifier = dataPropertyIdentifier(property);
        if (filler instanceof OWLDataComplementOf complement) {
            if (cardinality != 1) {
                throw new Unreadable(DroppedClause.Reason.COMPLEMENT_FILLER);
            }
            return Literal.body(store.intern(Restriction.only(PropertyKind.DATA, propertyIdentifier, dataRangeProposition(complement.getDataRange()))));
        }
        int fillerIdentifier = dataRangeProposition(filler);
        int proposition = store.intern(Restriction.atLeast(PropertyKind.DATA, cardinality, propertyIdentifier, fillerIdentifier));
        if (cardinality >= 2) {
            return Literal.headWithDual(proposition, Restriction.atMost(PropertyKind.DATA, cardinality - 1, propertyIdentifier, fillerIdentifier));
        }
        return Literal.head(proposition);
    }


    private Literal readDataUniversal(OWLDataPropertyExpression property, OWLDataRange filler) {
        int propertyIdentifier = dataPropertyIdentifier(property);
        if (filler instanceof OWLDataComplementOf complement) {
            return Literal.body(store.intern(Restriction.some(PropertyKind.DATA, propertyIdentifier, dataRangeProposition(complement.getDataRange()))));
        }
        return Literal.head(store.intern(Restriction.only(PropertyKind.DATA, propertyIdentifier, dataRangeProposition(filler))));
    }


    private Literal readDataAtMost(OWLDataPropertyExpression property, int cardinality, OWLDataRange filler) {
        int propertyIdentifier = dataPropertyIdentifier(property);
        if (filler instanceof OWLDataComplementOf) {
            throw new Unreadable(DroppedClause.Reason.COMPLEMENT_FILLER);
        }
        int fillerIdentifier = dataRangeProposition(filler);
        if (cardinality == 0) {
            return Literal.body(store.intern(Restriction.some(PropertyKind.DATA, propertyIdentifier, fillerIdentifier)));
        }
        int proposition = store.intern(Restriction.atMost(PropertyKind.DATA, cardinality, propertyIdentifier, fillerIdentifier));
        return Literal.headWithDual(proposition, Restriction.atLeast(PropertyKind.DATA, cardinality + 1, propertyIdentifier, fillerIdentifier));
    }


    // literal shapes that end a clause early

    private static boolean isTautology(OWLClassExpression disjunct) {
        if (disjunct.isOWLThing()) {
            return true;
        }
        if (disjunct instanceof OWLDataAllValuesFrom only) {
            return only.getFiller().isTopDatatype();
        }
        if (disjunct instanceof OWLObjectAllValuesFrom only) {
            return only.getFiller().isOWLThing();
        }
        if (disjunct instanceof OWLObjectMaxCardinality atMost) {
            return atMost.getFiller().isOWLNothing();
        }
        if (disjunct instanceof OWLObjectMinCardinality atLeast) {
            return atLeast.getCardinality() == 0;
        }
        return false;
    }

    private static boolean isContradiction(OWLClassExpression disjunct) {
        if (disjunct.isOWLNothing()) {
            return true;
        }
        if (disjunct instanceof OWLDataSomeValuesFrom some) {
            return isComplementOfLiteral(some.getFiller());
        }
        if (disjunct instanceof OWLObjectSomeValuesFrom some) {
            return some.getFiller().isOWLNothing();
        }
        if (disjunct instanceof OWLObjectMinCardinality atLeast) {
            return atLeast.getFiller().isOWLNothing();
        }
        return false;
    }

    // entities

    private int classProposition(OWLClass owlClass) {
        String iri = owlClass.getIRI().toString();
        if (owlClass.isOWLThing()) {
            return store.top();
        }
        if (iri.startsWith(FRESH_CLASS_PREFIX)) {
            return store.internFreshClass(ontologyNumber, iri);
        }
        if (index.getTypeOfEntity4IRI(iri) != Utilities.CLASSES) {
            throw new Unreadable(DroppedClause.Reason.UNKNOWN_ENTITY);
        }
        return index.getClassIdentifier4IRI(iri);
    }

    private int fillerProposition(OWLClassExpression filler) {
        if (filler instanceof OWLClass owlClass) {
            return classProposition(owlClass);
        }
        if (filler instanceof OWLObjectOneOf nominal) {
            return nominalProposition(nominal);
        }
        throw new Unreadable(reasonFor(filler));
    }


    private static boolean isComplementOfLiteral(OWLDataRange range) {
        return range instanceof OWLDataComplementOf complement && complement.getDataRange().isTopDatatype();
    }


    private int dataPropertyIdentifier(OWLDataPropertyExpression property) {
        String iri = property.asOWLDataProperty().getIRI().toString();
        if (index.getTypeOfEntity4IRI(iri) != Utilities.DATAPROPERTIES) {
            throw new Unreadable(DroppedClause.Reason.UNKNOWN_ENTITY);
        }
        return index.getDataPropIdentifier4IRI(iri);
    }


    /** A datatype, a facet restriction or a single literal (a data hasValue); anything else is dropped. */
    private int dataRangeProposition(OWLDataRange range) {
        if (range instanceof OWLDatatype datatype) {
            return store.internDataRange(DataRange.of(datatype));
        }
        if (range instanceof OWLDatatypeRestriction restriction) {
            return store.internDataRange(DataRange.of(restriction));
        }
        if (range instanceof OWLDataOneOf enumeration) {
            if (enumeration.getValues().size() != 1) {
                throw new Unreadable(DroppedClause.Reason.ENUMERATION);
            }
            OWLLiteral literal = enumeration.getValues().iterator().next();
            return store.internDataRange(DataRange.of(literal));
        }
        throw new Unreadable(DroppedClause.Reason.UNSUPPORTED_DATA_RANGE);
    }

    /** The identifier of the named property, of `R` and of `R⁻` alike. */
    private int propertyIdentifier(OWLObjectPropertyExpression property) {
        String iri = property.getNamedProperty().getIRI().toString();
        if (index.getTypeOfEntity4IRI(iri) != Utilities.OBJECTPROPERTIES) {
            throw new Unreadable(DroppedClause.Reason.UNKNOWN_ENTITY);
        }
        return index.getObjectPropIdentifier4IRI(iri);
    }

    /** The signed property of an expression: `R` outgoing, `R⁻` incoming. */
    private int propertyToken(OWLObjectPropertyExpression property) {
        return SignedProperties.token(propertyIdentifier(property), property.isAnonymous());
    }


    private static DroppedClause.Reason reasonFor(OWLClassExpression expression) {
        if (expression instanceof OWLObjectCardinalityRestriction
                || expression instanceof OWLObjectSomeValuesFrom
                || expression instanceof OWLObjectAllValuesFrom
                || expression instanceof OWLClass
                || expression instanceof OWLObjectComplementOf) {
            return DroppedClause.Reason.NOT_HORN;
        }
        return DroppedClause.Reason.OTHER;
    }


    // property inclusions

    /** `R ⊑ S`, `R ⊑ S⁻` (an inverse pair or a symmetric property arrives so) as a fact between signed properties. */
    private void readPropertyInclusion(OWLObjectPropertyExpression subProperty, OWLObjectPropertyExpression superProperty) {
        try {
            store.addSubProperty(PropertyKind.OBJECT, propertyToken(subProperty), propertyToken(superProperty));
        } catch (Unreadable unreadable) {
            store.drop(unreadable.reason, subProperty + " -> " + superProperty);
        }
    }

    private void readDataPropertyInclusion(OWLDataPropertyExpression subProperty, OWLDataPropertyExpression superProperty) {
        try {
            store.addSubProperty(PropertyKind.DATA, SignedProperties.outgoing(dataPropertyIdentifier(subProperty)),
                    SignedProperties.outgoing(dataPropertyIdentifier(superProperty)));
        } catch (Unreadable unreadable) {
            store.drop(unreadable.reason, subProperty + " -> " + superProperty);
        }
    }

    // property characteristics and facts

    /** `Reflexive(R)` is `TOP → hasSelf(R)`, `Irreflexive(R)` is `hasSelf(R) → FALSE`. */
    private void readReflexivity(OWLObjectPropertyExpression property, boolean irreflexive) {
        try {
            int self = selfProposition(property);
            if (irreflexive) {
                store.add(HornInclusion.of(List.of(self), HornInclusion.FALSE));
            } else {
                store.add(HornInclusion.of(List.of(store.top()), self));
            }
        } catch (Unreadable unreadable) {
            store.drop(unreadable.reason, (irreflexive ? "irreflexive " : "reflexive ") + property);
        }
    }

    /**
     * The one ABox reading, for the individuals some hasValue mentions: a named type is the
     * clause `{a} → T`, a distinctness the clash `{a} ∧ {b} → FALSE`. Everything else is
     * dropped: individuals no hasValue mentions never enter Dowling–Gallier.
     */
    private void readFact(OWLIndividualAxiom fact) {
        if (fact instanceof OWLClassAssertionAxiom assertion
                && assertion.getClassExpression() instanceof OWLClass type && !type.isOWLThing()) {
            Integer nominal = knownNominal(assertion.getIndividual());
            if (nominal != null) {
                try {
                    store.add(HornInclusion.of(List.of(nominal), classProposition(type)));
                } catch (Unreadable unreadable) {
                    store.drop(unreadable.reason, describe(fact));
                }
                return;
            }
        }
        if (fact instanceof OWLDifferentIndividualsAxiom different) {
            List<Integer> nominals = new ArrayList<>();
            for (OWLIndividual individual : different.getIndividuals()) {
                Integer nominal = knownNominal(individual);
                if (nominal != null) {
                    nominals.add(nominal);
                }
            }
            if (nominals.size() >= 2) {
                for (int first = 0; first < nominals.size(); first++) {
                    for (int second = first + 1; second < nominals.size(); second++) {
                        store.add(HornInclusion.of(List.of(nominals.get(first), nominals.get(second)), HornInclusion.FALSE));
                    }
                }
                return;
            }
        }
        store.drop(DroppedClause.Reason.ASSERTION, describe(fact));
    }

    private Integer knownNominal(OWLIndividual individual) {
        if (individual.isAnonymous()) {
            return null;
        }
        return store.nominalOf(individual.asOWLNamedIndividual().getIRI().toString());
    }


    private void dropAll(Iterable<?> axioms, DroppedClause.Reason reason) {
        for (Object axiom : axioms) {
            store.drop(reason, describe(axiom));
        }
    }

    private static String describe(Object axiom) {
        if (axiom instanceof Object[] parts) {
            List<String> rendered = new ArrayList<>();
            for (Object part : parts) {
                rendered.add(part.toString());
            }
            return String.join(" | ", rendered);
        }
        return axiom.toString();
    }

    /** Thrown while reading one literal; the clause is then dropped with this reason. */
    private static final class Unreadable extends RuntimeException {

        private final DroppedClause.Reason reason;

        Unreadable(DroppedClause.Reason reason) {
            super(reason.explanation());
            this.reason = reason;
        }
    }
}