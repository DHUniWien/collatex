package eu.interedition.collatex2.legacy.alignment;

import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import eu.interedition.collatex2.experimental.tokenmatching.ITokenMatcher;
import eu.interedition.collatex2.experimental.tokenmatching.VariantGraphIndexMatcher;
import eu.interedition.collatex2.implementation.alignmenttable.Columns;
import eu.interedition.collatex2.implementation.modifications.Addition;
import eu.interedition.collatex2.implementation.modifications.Omission;
import eu.interedition.collatex2.implementation.modifications.Replacement;
import eu.interedition.collatex2.implementation.modifications.Transposition;
import eu.interedition.collatex2.input.Phrase;
import eu.interedition.collatex2.interfaces.IAddition;
import eu.interedition.collatex2.interfaces.IAlignment;
import eu.interedition.collatex2.interfaces.IAlignmentTable;
import eu.interedition.collatex2.interfaces.IColumn;
import eu.interedition.collatex2.interfaces.IColumns;
import eu.interedition.collatex2.interfaces.IGap;
import eu.interedition.collatex2.interfaces.IMatch;
import eu.interedition.collatex2.interfaces.INormalizedToken;
import eu.interedition.collatex2.interfaces.IOmission;
import eu.interedition.collatex2.interfaces.IPhrase;
import eu.interedition.collatex2.interfaces.IReplacement;
import eu.interedition.collatex2.interfaces.ITokenMatch;
import eu.interedition.collatex2.interfaces.ITransposition;
import eu.interedition.collatex2.interfaces.IWitness;
import eu.interedition.collatex2.legacy.tokenmatching.ColumnPhraseMatch;

public class Alignment implements IAlignment {
  private final List<IMatch> matches;
  private final List<IGap> gaps;

  // Warning: matches should be sorted on Columns.getBeginPosition()! 
  public Alignment(final List<IMatch> matches, final List<IGap> gaps) {
    this.matches = matches;
    this.gaps = gaps;
  }

  public List<IMatch> getMatches() {
    return matches;
  }

  public List<IGap> getGaps() {
    return gaps;
  }

  final Comparator<IMatch> SORT_MATCHES_ON_POSITION_WITNESS = new Comparator<IMatch>() {
    public int compare(final IMatch o1, final IMatch o2) {
      return o1.getPhrase().getBeginPosition() - o2.getPhrase().getBeginPosition();
    }
  };

  public List<IMatch> getMatchesSortedForWitness() {
    final List<IMatch> matchesForWitness = Lists.newArrayList(matches);
    Collections.sort(matchesForWitness, SORT_MATCHES_ON_POSITION_WITNESS);
    return matchesForWitness;
  }

  @Override
  public List<ITransposition> getTranspositions() {
    final List<IMatch> matchesA = getMatches();
    final List<IMatch> matchesB = getMatchesSortedForWitness();
    final List<ITransposition> transpositions = Lists.newArrayList();
    for (int i = 0; i < matchesA.size(); i++) {
      final IMatch matchA = matchesA.get(i);
      final IMatch matchB = matchesB.get(i);
      if (!matchA.equals(matchB)) {
        transpositions.add(new Transposition(matchA, matchB));
      }
    }
    return transpositions;
  }

  private static final Predicate<IGap> ADDITION_PREDICATE = new Predicate<IGap>() {
    @Override
    public boolean apply(final IGap gap) {
      return gap.isAddition();
    }
  };

  private static final Predicate<IGap> REPLACEMENT_PREDICATE = new Predicate<IGap>() {
    @Override
    public boolean apply(final IGap gap) {
      return gap.isReplacement();
    }
  };

  private static final Predicate<IGap> OMISSION_PREDICATE = new Predicate<IGap>() {
    @Override
    public boolean apply(final IGap gap) {
      return gap.isOmission();
    }
  };

  private static final Function<IGap, IAddition> GAP_TO_ADDITION = new Function<IGap, IAddition>() {
    @Override
    public IAddition apply(final IGap gap) {
      return Addition.create(gap);
    }
  };

  private static final Function<IGap, IOmission> GAP_TO_OMISSION = new Function<IGap, IOmission>() {
    @Override
    public IOmission apply(final IGap gap) {
      return Omission.create(gap);
    }
  };

  private static final Function<IGap, IReplacement> GAP_TO_REPLACEMENT = new Function<IGap, IReplacement>() {
    @Override
    public IReplacement apply(final IGap gap) {
      return Replacement.create(gap);
    }
  };

  @Override
  public List<IAddition> getAdditions() {
    return Lists.newArrayList(transform(filter(getGaps(), ADDITION_PREDICATE), GAP_TO_ADDITION));
  }

  @Override
  public List<IReplacement> getReplacements() {
    return Lists.newArrayList(transform(filter(getGaps(), REPLACEMENT_PREDICATE), GAP_TO_REPLACEMENT));
  }

  @Override
  public List<IOmission> getOmissions() {
    return Lists.newArrayList(transform(filter(getGaps(), OMISSION_PREDICATE), GAP_TO_OMISSION));
  }

  public static List<IMatch> getColumnMatches(IAlignmentTable table, IWitness witness) {
    Map<INormalizedToken, IColumn> tokenToColumn = Maps.newLinkedHashMap();
    for (IColumn column : table.getColumns()) {
      for (String sigil : column.getSigli()) {
        INormalizedToken token = column.getToken(sigil);
        tokenToColumn.put(token, column);
      }
    }
    ITokenMatcher matcher = new VariantGraphIndexMatcher(table);
    List<ITokenMatch> tokenMatches = matcher.getMatches(witness);
    List<IMatch> columnMatches = Lists.newArrayList();
    for (ITokenMatch tokenMatch : tokenMatches) {
      INormalizedToken tableToken = tokenMatch.getTableToken();
      IColumn column = tokenToColumn.get(tableToken);
      IPhrase witnessPhrase = new Phrase(Lists.newArrayList(tokenMatch.getWitnessToken()));
      IColumns columns = new Columns(Lists.newArrayList(column));
      IMatch columnMatch = new ColumnPhraseMatch(columns, witnessPhrase);
      columnMatches.add(columnMatch);
    }
    // Sort the ColumnMatches here
    // otherwise the gapdetection goes ballistic!
    Collections.sort(columnMatches, new Comparator<IMatch>() {
      @Override
      public int compare(IMatch o1, IMatch o2) {
        return o1.getColumns().getBeginPosition() - o2.getColumns().getBeginPosition();
      }
    });
    return columnMatches;
  }

  //	  public void accept(final ModificationVisitor modificationVisitor) {
  //		    for (final Gap gap : gaps) {
  //		      final Modification modification = gap.getModification();
  //		      modification.accept(modificationVisitor);
  //		    }
  //		  }

}
