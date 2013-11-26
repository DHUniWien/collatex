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

package eu.interedition.collatex.dekker.vectorspace;

import com.google.common.collect.RowSortedTable;

import eu.interedition.collatex.AbstractTest;
import eu.interedition.collatex.VariantGraph;
import eu.interedition.collatex.Witness;
import eu.interedition.collatex.Token;
import eu.interedition.collatex.jung.JungVariantGraph;
import eu.interedition.collatex.simple.SimpleWitness;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public class VSTranspositionTest extends AbstractTest {
  @Override
  protected VariantGraph collate(String... witnesses) {
    SimpleWitness[] witnesses2 = createWitnesses(witnesses);
    return collate(witnesses2);
  }

  @Override
  protected VariantGraph collate(SimpleWitness... witnesses2) {
    VariantGraph graph = new JungVariantGraph();
    DekkerVectorSpaceAlgorithm algo = new DekkerVectorSpaceAlgorithm();
    if (witnesses2.length==2) {
      algo.collate(graph, witnesses2[0], witnesses2[1]);
    } else {
      algo.collate(graph, witnesses2[0], witnesses2[1], witnesses2[2]);
    }
    return graph;
  }
  
  
  @Test
  public void noTransposition() {
    assertEquals(0, collate("no transposition", "no transposition").transpositions().size());
    assertEquals(0, collate("a b", "c a").transpositions().size());
  }

  @Test
  public void oneTransposition() {
    assertEquals(1, collate("a b", "b a").transpositions().size());
  }

  @Test
  public void multipleTranspositions() {
    assertEquals(1, collate("a b c", "b c a").transpositions().size());
  }

  //TODO: Re-enable assert when n-dimensional vectorspace is created!
  @Test
  public void transposition1() {
    final SimpleWitness[] w = createWitnesses(//
            "the white and black cat", "The black cat",//
            "the black and white cat", "the black and green cat");
    final RowSortedTable<Integer, Witness, Set<Token>> table = table(collate(w));

    assertEquals("|the|white|and|black|cat|", toString(table, w[0]));
    assertEquals("|the| | |black|cat|", toString(table, w[1]));
    assertEquals("|the|black|and|white|cat|", toString(table, w[2]));
//    assertEquals("|the|black|and|green|cat|", toString(table, w[3]));
  }

  @Test
  public void transposition2() {
    final SimpleWitness[] w = createWitnesses("He was agast, so", "He was agast", "So he was agast");
    final RowSortedTable<Integer, Witness, Set<Token>> table = table(collate(w));

    assertEquals("| |he|was|agast|,|so|", toString(table, w[0]));
    assertEquals("| |he|was|agast| | |", toString(table, w[1]));
    assertEquals("|so|he|was|agast| | |", toString(table, w[2]));
  }

  @Test
  public void transposition2Reordered() {
    final SimpleWitness[] w = createWitnesses("So he was agast", "He was agast", "He was agast, so");
    final RowSortedTable<Integer, Witness, Set<Token>> table = table(collate(w));

    assertEquals("|so|he|was|agast| | |", toString(table, w[0]));
    assertEquals("| |he|was|agast| | |", toString(table, w[1]));
    assertEquals("| |he|was|agast|,|so|", toString(table, w[2]));
  }
  
  @Test
  public void testTranspositionLimiter1() {
    final SimpleWitness a = new SimpleWitness("A","X a b");
    final SimpleWitness b = new SimpleWitness("B","a b X");
    VariantGraph graph = collate(a,b);
    assertEquals(1, graph.transpositions().size());
    final RowSortedTable<Integer, Witness, Set<Token>> table = table(graph);
    assertEquals("|x|a|b| |", toString(table, a));
    assertEquals("| |a|b|x|", toString(table, b));
  }
  
  @Test
  public void testTranspositionLimiter2() {
    final SimpleWitness a = new SimpleWitness("A","a b c .");
    final SimpleWitness b = new SimpleWitness("B","a b c d e f g h i j k l m n o p q r s t u v w .");
    final RowSortedTable<Integer, Witness, Set<Token>> table = table(collate(a,b));
    assertEquals("|a|b|c| | | | | | | | | | | | | | | | | | | | |.|", toString(table, a));
    assertEquals("|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|.|", toString(table, b));
  }

  @Test
  public void testTranspositionLimiter3() {
    final SimpleWitness a = new SimpleWitness("A","X a b c d e f g h i j k l m n o p");
    final SimpleWitness b = new SimpleWitness("B","a b c d e f g h i j k l m n o p X");
    VariantGraph graph = collate(a,b);
    assertEquals(0, graph.transpositions().size());
    final RowSortedTable<Integer, Witness, Set<Token>> table = table(graph);
    assertEquals("|x|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p| |", toString(table, a));
    assertEquals("| |a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|x|", toString(table, b));
  }
  
  @Test
  public void testTranspositionLimiter4() {
    final SimpleWitness a = new SimpleWitness("A","a b c d e f g h i j k l m n o p X");
    final SimpleWitness b = new SimpleWitness("B","X a b c d e f g h i j k l m n o p");
    VariantGraph graph = collate(a,b);
    assertEquals(0, graph.transpositions().size());
    final RowSortedTable<Integer, Witness, Set<Token>> table = table(graph);
    assertEquals("| |a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|x|", toString(table, a));
    assertEquals("|x|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p| |", toString(table, b));
  }

}