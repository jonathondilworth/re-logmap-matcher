/* Copyright 2008, 2009, 2010 by the Oxford University Computing Laboratory

   This file is part of HermiT.

   HermiT is free software: you can redistribute it and/or modify
   it under the terms of the GNU Lesser General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   HermiT is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public License
   along with HermiT.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.semanticweb.HermiT.structural;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLHasKeyAxiom;
import org.semanticweb.owlapi.model.OWLIndividualAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;

public class OWLAxiomsAdapted extends OWLAxioms {
    
   public OWLAxiomsAdapted() {

   }
    
   public Collection<OWLClassExpression[]> getNormalisedConceptInclusions() { 
      return m_conceptInclusions;
   }

   public Collection<OWLObjectPropertyExpression[]> getSimpleObjectPropertyInclusions() {
      return m_simpleObjectPropertyInclusions;
   }

   /**
    * Each complex inclusion (a property chain below a property) as one array: the chain's
    * properties in order, then the super property last. ComplexObjectPropertyInclusion is
    * package-private in HermiT, so it cannot be handed out directly.
    */
   public Collection<OWLObjectPropertyExpression[]> getComplexObjectPropertyInclusionsAsChains() {
      Collection<OWLObjectPropertyExpression[]> chains = new ArrayList<OWLObjectPropertyExpression[]>();
      for (ComplexObjectPropertyInclusion inclusion : m_complexObjectPropertyInclusions) {
         OWLObjectPropertyExpression[] chain = new OWLObjectPropertyExpression[inclusion.m_subObjectProperties.length + 1];
         System.arraycopy(inclusion.m_subObjectProperties, 0, chain, 0, inclusion.m_subObjectProperties.length);
         chain[chain.length - 1] = inclusion.m_superObjectProperty;
         chains.add(chain);
      }
      return chains;
   }

   public Collection<OWLObjectPropertyExpression[]> getDisjointObjectProperties() {
      return m_disjointObjectProperties;
   }

   public Set<OWLObjectPropertyExpression> getReflexiveObjectProperties() {
      return m_reflexiveObjectProperties;
   }

   public Set<OWLObjectPropertyExpression> getIrreflexiveObjectProperties() {
      return m_irreflexiveObjectProperties;
   }

   public Set<OWLObjectPropertyExpression> getAsymmetricObjectProperties() {
      return m_asymmetricObjectProperties;
   }

   public Collection<OWLDataPropertyExpression[]> getDataPropertyInclusions() {
      return m_dataPropertyInclusions;
   }

   public Collection<OWLDataPropertyExpression[]> getDisjointDataProperties() {
      return m_disjointDataProperties;
   }

   public Collection<OWLDataRange[]> getDataRangeInclusions() {
      return m_dataRangeInclusions;
   }

   public Collection<OWLIndividualAxiom> getFacts() {
      return m_facts;
   }

   public Set<OWLHasKeyAxiom> getHasKeys() {
      return m_hasKeys;
   }

}