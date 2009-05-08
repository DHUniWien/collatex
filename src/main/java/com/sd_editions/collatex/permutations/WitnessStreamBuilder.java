package com.sd_editions.collatex.permutations;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;

public abstract class WitnessStreamBuilder extends WitnessBuilder {

  public abstract Witness build(InputStream inputStream) throws SAXException, IOException;

}
