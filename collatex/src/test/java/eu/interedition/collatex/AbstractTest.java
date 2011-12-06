package eu.interedition.collatex;

import eu.interedition.collatex.implementation.alignment.VariantGraphBuilder;
import eu.interedition.collatex.implementation.graph.VariantGraph;
import eu.interedition.collatex.implementation.input.DefaultTokenNormalizer;
import eu.interedition.collatex.implementation.input.WhitespaceTokenizer;
import eu.interedition.collatex.implementation.input.WitnessBuilder;
import eu.interedition.collatex.implementation.output.AlignmentTable;
import eu.interedition.collatex.interfaces.ITokenizer;
import eu.interedition.collatex.interfaces.IVariantGraph;
import eu.interedition.collatex.interfaces.IWitness;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public abstract class AbstractTest {
  protected Logger LOG = LoggerFactory.getLogger(getClass());
  public static final char[] SIGLA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

  protected WitnessBuilder witnessBuilder = new WitnessBuilder(new DefaultTokenNormalizer());
  protected ITokenizer tokenizer = new WhitespaceTokenizer();

  protected IWitness[] createWitnesses(WitnessBuilder witnessBuilder, ITokenizer tokenizer, String... contents) {
    Assert.assertTrue("Not enough sigla", contents.length <= SIGLA.length);
    final IWitness[] witnesses = new IWitness[contents.length];
    for (int wc = 0; wc < contents.length; wc++) {
      witnesses[wc] = witnessBuilder.build(Character.toString(SIGLA[wc]), contents[wc], tokenizer);
    }
    return witnesses;
  }

  protected IWitness[] createWitnesses(String... contents) {
    return createWitnesses(witnessBuilder, tokenizer, contents);
  }

  protected VariantGraphBuilder merge(IVariantGraph graph, IWitness... witnesses) {
    final VariantGraphBuilder builder = new VariantGraphBuilder(graph);
    builder.add(witnesses);
    return builder;
  }

  protected IVariantGraph merge(IWitness... witnesses) {
    final VariantGraph graph = new VariantGraph();
    merge(graph, witnesses);
    return graph;
  }

  protected IVariantGraph merge(String... witnesses) {
    return merge(createWitnesses(witnesses));
  }

  protected AlignmentTable align(IVariantGraph graph) {
    return new AlignmentTable(graph);
  }

  protected AlignmentTable align(IWitness... witnesses) {
    return new AlignmentTable(merge(witnesses));
  }

  protected AlignmentTable align(String... witnesses) {
    return new AlignmentTable(merge(witnesses));
  }

}
