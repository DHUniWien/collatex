package eu.interedition.collatex2.implementation.tokenmatching;

import eu.interedition.collatex2.interfaces.INormalizedToken;
import eu.interedition.collatex2.interfaces.ITokenMatch;

public class TokenMatch implements ITokenMatch {
  private final INormalizedToken baseToken;
  private final INormalizedToken witnessToken;

  public TokenMatch(INormalizedToken baseToken, INormalizedToken witnessToken) {
    this.baseToken = baseToken;
    this.witnessToken = witnessToken;
  }

  @Override
  public INormalizedToken getBaseToken() {
   return baseToken;
  }

  @Override
  public INormalizedToken getWitnessToken() {
    return witnessToken;
  }

}
