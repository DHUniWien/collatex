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

package eu.interedition.collatex.http;

import com.google.common.collect.Sets;
import com.sun.jersey.api.container.grizzly2.GrizzlyServerFactory;
import com.sun.jersey.api.core.DefaultResourceConfig;
import eu.interedition.collatex.VariantGraph;
import eu.interedition.collatex.io.Collation;
import eu.interedition.collatex.io.CollationDeserializer;
import eu.interedition.collatex.io.VariantGraphSerializer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.module.SimpleModule;
import org.glassfish.grizzly.http.server.HttpServer;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class Server extends DefaultResourceConfig implements Runnable {

  String staticPath;
  String dotPath;

  String contextPath;
  int httpPort;

  ObjectMapper objectMapper;

  int maxParallelCollations;
  int maxCollationSize;

  @Override
  public void run() {
    try {
      objectMapper = new ObjectMapper();
      objectMapper.registerModule(new CollateXModule());

      final URI context = UriBuilder.fromUri("http://localhost/").port(httpPort).path(contextPath).build();

      if (LOG.isLoggable(Level.INFO)) {
        LOG.info("Starting HTTP server at " + context.toString());
      }

      final HttpServer httpServer = GrizzlyServerFactory.createHttpServer(context, this);
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          if (LOG.isLoggable(Level.INFO)) {
            LOG.info("Stopping HTTP server");
          }
          httpServer.stop();
        }
      }));

      httpServer.start();

      synchronized (httpServer) {
        try {
          httpServer.wait();
        } catch (InterruptedException e) {
        }
      }
    } catch (IOException e) {
      LOG.log(Level.SEVERE, "I/O error", e);
    }
  }

  Server configure(CommandLine commandLine) {
    httpPort = Integer.parseInt(commandLine.getOptionValue("p", "7369"));
    contextPath = commandLine.getOptionValue("cp", "/").replaceAll("/+$", "");

    dotPath = commandLine.getOptionValue("dot", null);

    maxParallelCollations = Integer.parseInt(commandLine.getOptionValue("mpc", "2"));
    maxCollationSize = Integer.parseInt(commandLine.getOptionValue("mcs", "0"));

    staticPath = System.getProperty("collatex.static.path", null);

    return this;
  }

  public static void main(String... args) {
    try {
      final CommandLine commandLine = new GnuParser().parse(OPTIONS, args);
      if (commandLine.hasOption("h")) {
        new HelpFormatter().printHelp("collatex-server [<options> ...]\n", OPTIONS);
        return;
      }

      new Server().configure(commandLine).run();
    } catch (ParseException e) {
      LOG.log(Level.SEVERE, "Error while parsing command line", e);
    }
  }


  @Override
  public Set<Class<?>> getProviderClasses() {
    return Sets.<Class<?>>newHashSet(
            VariantGraphDotMessageBodyWriter.class,
            VariantGraphMLMessageBodyWriter.class,
            VariantGraphTEIMessageBodyWriter.class
    );
  }

  @Override
  public Set<Object> getSingletons() {
    return Sets.newHashSet(
            new CollateResource(staticPath, maxParallelCollations, maxCollationSize),
            new ObjectMapperMessageBodyReaderWriter(objectMapper),
            new VariantGraphSVGMessageBodyWriter(dotPath)
    );
  }

  static class CollateXModule extends SimpleModule {

    public CollateXModule() {
      super(CollateXModule.class.getPackage().getName(), Version.unknownVersion());
      addDeserializer(Collation.class, new CollationDeserializer());
      addSerializer(VariantGraph.class, new VariantGraphSerializer());
    }
  }

  static final Logger LOG = Logger.getLogger(Server.class.getName());
  static final Options OPTIONS = new Options();

  static {
    OPTIONS.addOption("cp", "context-path", true, "URL base/context path of the service, default: '/'");
    OPTIONS.addOption("dot", "dot-path", true, "path to Graphviz 'dot', auto-detected by default");
    OPTIONS.addOption("h", "help", false, "prints usage instructions");
    OPTIONS.addOption("p", "port", true, "HTTP port to bind server to, default: 7369");
    OPTIONS.addOption("mpc", "max-parallel-collations", true, "maximum number of collations to perform in parallel, default: 2");
    OPTIONS.addOption("mcs", "max-collation-size", true, "maximum number of characters (counted over all witnesses) to perform collations on, default: unlimited");
  }
}