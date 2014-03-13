/*
 * Copyright (c) 2013 The Interedition Development Group.
 *
 * This file is part of CollateX.
 *
 * CollateX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * CollateX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CollateX.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.interedition.collatex.matching;

import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;

import eu.interedition.collatex.AbstractTest;
import eu.interedition.collatex.Token;
import eu.interedition.collatex.VariantGraph;
import eu.interedition.collatex.simple.SimpleWitness;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NearMatcherTest extends AbstractTest {
  
  @Test
  public void nearTokenMatching() {
    final SimpleWitness[] w = createWitnesses("near matching yeah", "nar matching");
    final VariantGraph graph = collate(w[0]);
    final ListMultimap<Token, VariantGraph.Vertex> matches = Matches.between(graph.vertices(), w[1].getTokens(), new EditDistanceTokenComparator()).getAll();

    assertEquals(2, matches.size());
    assertEquals(w[0].getTokens().get(0), Iterables.getFirst(Iterables.get(matches.get(w[1].getTokens().get(0)), 0).tokens(), null));
    assertEquals(w[0].getTokens().get(1), Iterables.getFirst(Iterables.get(matches.get(w[1].getTokens().get(1)), 0).tokens(), null));
  }
  
  @Test
  public void nearTokenMatchingDavidBirnbaum() {
    String w1 = "фрк0";
    String w2 = "фрц0";
    assertEquals(1, EditDistance.compute(w1, w2));
  }

}
