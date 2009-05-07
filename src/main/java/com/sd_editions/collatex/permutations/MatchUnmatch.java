package com.sd_editions.collatex.permutations;

import java.util.List;
import java.util.Set;

public class MatchUnmatch {

  private final List<MatchSequence> matchSequencesForBase;
  private final List<MatchSequence> matchSequencesForWitness;
  private final List<MisMatch> unmatches;
  private final Set<Match> permutation;

  public MatchUnmatch(Set<Match> _permutation, List<MatchSequence> _matchSequencesForBase, List<MatchSequence> _matchSequencesForWitness, List<MisMatch> _unmatches) {
    super();
    this.permutation = _permutation;
    this.matchSequencesForBase = _matchSequencesForBase;
    this.matchSequencesForWitness = _matchSequencesForWitness;
    this.unmatches = _unmatches;
  }

  public Set<Match> getPermutation() {
    return permutation;
  }

  public List<MatchSequence> getMatchSequencesForBase() {
    return matchSequencesForBase;
  }

  public List<MatchSequence> getMatchSequencesForWitness() {
    return matchSequencesForWitness;
  }

  public List<MisMatch> getUnmatches() {
    return unmatches;
  }
}