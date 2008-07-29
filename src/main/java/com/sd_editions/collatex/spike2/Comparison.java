package com.sd_editions.collatex.spike2;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class Comparison {

  private final SortedSet<Integer> colorsPerWitness;
  private final SortedSet<Integer> colorsPerWitness2;
  private final Index colors;
  private final Set<Integer> added_words;
  private final Set<Integer> removed_words;

  public Comparison(@SuppressWarnings("hiding") SortedSet<Integer> colorsPerWitness, @SuppressWarnings("hiding") SortedSet<Integer> colorsPerWitness2, Index index) {
    this.colorsPerWitness = colorsPerWitness;
    this.colorsPerWitness2 = colorsPerWitness2;
    this.colors = index;
    added_words = Sets.newLinkedHashSet(colorsPerWitness2);
    added_words.removeAll(colorsPerWitness);
    removed_words = Sets.newLinkedHashSet(colorsPerWitness);
    removed_words.removeAll(colorsPerWitness2);
  }

  public List<String> getAddedWords() {
    List<String> additions = Lists.newArrayList();
    for (Integer color : added_words) {
      additions.add(getWordForColor(color));
    }
    return additions;
  }

  @SuppressWarnings("boxing")
  private String getWordForColor(Integer color) {
    return colors.getWord(color);
  }

  public List<String> getReplacedWords() {
    // NOTE: this far too simple!
    List<String> replacements = Lists.newArrayList();
    if (added_words.size() == removed_words.size()) {
      Iterator<Integer> rem = removed_words.iterator();
      for (Integer add : added_words) {
        Integer remove = rem.next();
        replacements.add(getWordForColor(remove) + "/" + getWordForColor(add));
      }
    }
    return replacements;
  }

  public List<String> getRemovedWords() {
    List<String> removals = Lists.newArrayList();
    for (Integer color : removed_words) {
      removals.add(getWordForColor(color));
    }
    return removals;
  }
}
