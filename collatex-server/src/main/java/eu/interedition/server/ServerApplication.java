package eu.interedition.server;

import eu.interedition.server.collatex.VariantGraphResource;
import freemarker.template.Configuration;
import org.gearman.Gearman;
import org.gearman.GearmanServer;
import org.gearman.GearmanWorker;
import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.CharacterSet;
import org.restlet.data.Protocol;
import org.restlet.resource.Directory;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import static org.restlet.routing.Template.MODE_EQUALS;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
@Component
public class ServerApplication extends Application implements InitializingBean {
  @Autowired
  private AbstractApplicationContext applicationContext;

  @Autowired
  private Environment environment;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private Configuration freemarkerConfiguration;

  public ServerApplication() {
    super();
  }

  @Override
  public Restlet createInboundRoot() {
    final Context context = getContext();

    final String contextPath = environment.getRequiredProperty("interedition.context_path");
    final Router router = new Router(context);
    router.attach(contextPath + "/", new TemplateResourceFinder(freemarkerConfiguration, "index.ftl"), MODE_EQUALS);
    router.attach(contextPath + "/collate", transactional(newFinder(VariantGraphResource.class)), MODE_EQUALS);
    router.attach(contextPath + "/collate/apidocs", new TemplateResourceFinder(freemarkerConfiguration, "collate/apidocs.ftl"), MODE_EQUALS);
    router.attach(contextPath + "/collate/console", new TemplateResourceFinder(freemarkerConfiguration, "collate/console.ftl"), MODE_EQUALS);
    router.attach(contextPath + "/collate/darwin", new TemplateResourceFinder(freemarkerConfiguration, "collate/darwin-example.ftl"), MODE_EQUALS);
    router.attach(contextPath + "/static", new Directory(context.createChildContext(), environment.getRequiredProperty("interedition.static")));
    return router;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    getMetadataService().setDefaultCharacterSet(CharacterSet.UTF_8);
  }

  private Restlet transactional(Restlet restlet) {
    return new TransactionFilter(getContext().createChildContext(), restlet, transactionManager);
  }

  private <T extends ServerResource> ApplicationContextFinder<T> newFinder(Class<T> resourceType) {
    return new ApplicationContextFinder<T>(getContext().createChildContext(), applicationContext, resourceType);
  }

  public static void main(String... args) {
    final Logger logger = Logger.getLogger("");
    for (Handler handler : logger.getHandlers()) {
      if (ConsoleHandler.class.isAssignableFrom(handler.getClass())) {
        logger.removeHandler(handler);
      }
    }
    SLF4JBridgeHandler.install();

    try {
      final ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(new String[]{"/application-context.xml"}, false);

      final ConfigurableEnvironment environment = ctx.getEnvironment();
      final MutablePropertySources envProps = environment.getPropertySources();
      envProps.addLast(new PropertiesPropertySource("system", System.getProperties()));
      envProps.addLast(new DetectingConfigurationPropertySource("detected"));
      envProps.addLast(new ResourcePropertySource(new ClassPathResource("/config.properties", ServerApplication.class)));

      ctx.registerShutdownHook();
      ctx.refresh();

      final Gearman gearman = Gearman.createGearman();
      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          gearman.shutdown();
        }
      }));

      final GearmanServer gearmanServer = gearman.createGearmanServer(
              environment.getRequiredProperty("interedition.gearman.server.host"),
              environment.getRequiredProperty("interedition.gearman.server.port", Integer.class)
      );

      final GearmanWorker worker = gearman.createGearmanWorker();
      worker.addFunction("collatex", ctx.getBean(CollationWorker.class));
      worker.addServer(gearmanServer);

      final ServerApplication application = ctx.getBean(ServerApplication.class);

      final org.restlet.Component component = new org.restlet.Component();
      component.getClients().add(Protocol.CLAP);
      component.getClients().add(Protocol.FILE);
      component.getLogService().setEnabled(false);
      component.getDefaultHost().attach(application);
      component.getServers().add(Protocol.HTTP, environment.getRequiredProperty("interedition.port", Integer.class)).getContext().getParameters().set("maxThreads", "512");

      Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
        @Override
        public void run() {
          if (component.isStarted()) {
            try {
              component.stop();
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
        }
      }));

      component.start();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
