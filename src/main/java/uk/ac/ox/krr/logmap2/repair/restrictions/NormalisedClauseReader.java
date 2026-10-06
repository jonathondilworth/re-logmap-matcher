package uk.ac.ox.krr.logmap2.repair.restrictions;

import java.util.ArrayList;
import java.util.List;

import org.semanticweb.HermiT.structural.OWLAxiomsAdapted;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
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
 * in the body, and what remains is the head. The clause is kept when exactly one head
 * remains; a clause over named classes only is left to LogMap's index; everything else is
 * dropped with a reason.
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
        dropAll(normalised.getComplexObjectPropertyInclusionsAsChains(), DroppedClause.Reason.PROPERTY_CHAIN);
        dropAll(normalised.getDisjointObjectProperties(), DroppedClause.Reason.DISJOINT_PROPERTIES);
        dropAll(normalised.getReflexiveObjectProperties(), DroppedClause.Reason.PROPERTY_CHARACTERISTIC);
        dropAll(normalised.getIrreflexiveObjectProperties(), DroppedClause.Reason.PROPERTY_CHARACTERISTIC);
        dropAll(normalised.getAsymmetricObjectProperties(), DroppedClause.Reason.PROPERTY_CHARACTERISTIC);
        dropAll(normalised.getDataPropertyInclusions(), DroppedClause.Reason.DATA_PROPERTY);
        dropAll(normalised.getDisjointDataProperties(), DroppedClause.Reason.DATA_PROPERTY);
        dropAll(normalised.getDataRangeInclusions(), DroppedClause.Reason.DATA_PROPERTY);
        dropAll(normalised.getFacts(), DroppedClause.Reason.ASSERTION);
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
            mentionsRestrictionOrFreshClass |= isRestrictionOrFreshClass(literal);
        }

        List<Integer> body = new ArrayList<>();
        List<Literal> heads = new ArrayList<>();

        for (Literal literal : literals) {
            if (literal.inBody()) {
                body.add(literal.proposition());
            } else {
                heads.add(literal);
            }
        }

        Literal head = chooseHead(heads);

        for (Literal otherHead : heads) {
            if (otherHead == head) {
                continue;
            }
            if (!otherHead.hasDual()) {
                store.drop(DroppedClause.Reason.NOT_HORN, describe(disjuncts));
                return;
            }
            body.add(store.intern(otherHead.dual()));
            mentionsRestrictionOrFreshClass = true;
        }

        if (!mentionsRestrictionOrFreshClass) {
            store.countCarriedByIndex();
            return;
        }
        if (body.isEmpty()) {
            body.add(store.top());
        }
        store.add(HornInclusion.of(body, head == null ? HornInclusion.FALSE : head.proposition()));
    }


    // A head that cannot become a body atom is kept as the head; otherwise the first head
    private static Literal chooseHead(List<Literal> heads) {
        for (Literal head : heads) {
            if (!head.hasDual()) {
                return head;
            }
        }
        return heads.isEmpty() ? null : heads.get(0);
    }


    private boolean isRestrictionOrFreshClass(Literal literal) {
        return store.isRestriction(literal.proposition()) || store.isFreshClass(literal.proposition());
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
        throw new Unreadable(reasonFor(disjunct));
    }


    private Literal readNegatedLiteral(OWLClassExpression operand) {
        if (operand instanceof OWLClass owlClass) {
            return Literal.body(classProposition(owlClass));
        }
        throw new Unreadable(reasonFor(operand));
    }


    /** `∃R.C` and `≥n R.C`, whose dual (a `≤n-1`) is an atom only for n ≥ 2. */
    private Literal readExistential(OWLObjectPropertyExpression property, int cardinality, OWLClassExpression filler) {
        int propertyIdentifier = propertyIdentifier(property);
        
        if (filler instanceof OWLObjectComplementOf complement) {
            if (cardinality != 1) {
                throw new Unreadable(DroppedClause.Reason.COMPLEMENT_FILLER);
            }
            return Literal.body(store.intern(Restriction.only(propertyIdentifier, fillerProposition(complement.getOperand()))));
        }
        
        int fillerIdentifier = fillerProposition(filler);
        int proposition = store.intern(Restriction.atLeast(cardinality, propertyIdentifier, fillerIdentifier));
        
        if (cardinality >= 2) {
            return Literal.headWithDual(proposition, Restriction.atMost(cardinality - 1, propertyIdentifier, fillerIdentifier));
        }
        
        return Literal.head(proposition);
    }


    /** `∀R.C`; `∀R.¬C` is the body atom `∃R.C`, `∀R.⊥` the body atom `∃R.⊤`. */
    private Literal readUniversal(OWLObjectPropertyExpression property, OWLClassExpression filler) {
        int propertyIdentifier = propertyIdentifier(property);
        if (filler instanceof OWLObjectComplementOf complement) {
            return Literal.body(store.intern(Restriction.some(propertyIdentifier, fillerProposition(complement.getOperand()))));
        }
        if (filler.isOWLNothing()) {
            return Literal.body(store.intern(Restriction.some(propertyIdentifier, store.top())));
        }
        return Literal.head(store.intern(Restriction.only(propertyIdentifier, fillerProposition(filler))));
    }

    /** `≤n R.C`, whose dual is the body atom `≥n+1 R.C`. */
    private Literal readAtMost(OWLObjectPropertyExpression property, int cardinality, OWLClassExpression filler) {
        int propertyIdentifier = propertyIdentifier(property);
        if (filler instanceof OWLObjectComplementOf) {
            throw new Unreadable(DroppedClause.Reason.COMPLEMENT_FILLER);
        }
        int fillerIdentifier = fillerProposition(filler);
        if (cardinality == 0) {
            return Literal.body(store.intern(Restriction.some(propertyIdentifier, fillerIdentifier)));
        }
        int proposition = store.intern(Restriction.atMost(cardinality, propertyIdentifier, fillerIdentifier));
        return Literal.headWithDual(proposition, Restriction.atLeast(cardinality + 1, propertyIdentifier, fillerIdentifier));
    }

    // literal shapes that end a clause early

    private static boolean isTautology(OWLClassExpression disjunct) {
        if (disjunct.isOWLThing()) {
            return true;
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
        throw new Unreadable(reasonFor(filler));
    }

    private int propertyIdentifier(OWLObjectPropertyExpression property) {
        if (property.isAnonymous()) {
            throw new Unreadable(DroppedClause.Reason.INVERSE_PROPERTY);
        }
        String iri = property.asOWLObjectProperty().getIRI().toString();
        if (index.getTypeOfEntity4IRI(iri) != Utilities.OBJECTPROPERTIES) {
            throw new Unreadable(DroppedClause.Reason.UNKNOWN_ENTITY);
        }
        return index.getObjectPropIdentifier4IRI(iri);
    }

    private static DroppedClause.Reason reasonFor(OWLClassExpression expression) {
        if (expression instanceof OWLObjectHasSelf) {
            return DroppedClause.Reason.HAS_SELF;
        }
        if (expression instanceof OWLObjectOneOf) {
            return DroppedClause.Reason.HAS_VALUE;
        }
        if (expression instanceof OWLObjectCardinalityRestriction
                || expression instanceof OWLObjectSomeValuesFrom
                || expression instanceof OWLObjectAllValuesFrom
                || expression instanceof OWLClass
                || expression instanceof OWLObjectComplementOf) {
            return DroppedClause.Reason.NOT_HORN;
        }
        return DroppedClause.Reason.DATA_PROPERTY;
    }

    // property inclusions

    private void readPropertyInclusion(OWLObjectPropertyExpression subProperty, OWLObjectPropertyExpression superProperty) {
        try {
            store.addSubProperty(propertyIdentifier(subProperty), propertyIdentifier(superProperty));
        } catch (Unreadable unreadable) {
            store.drop(unreadable.reason, subProperty + " -> " + superProperty);
        }
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