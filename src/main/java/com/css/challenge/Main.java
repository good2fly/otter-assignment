package com.css.challenge;

import com.css.challenge.client.Client;
import com.css.challenge.client.Order;
import com.css.challenge.client.OrderPickupScheduler;
import com.css.challenge.client.Problem;
import com.css.challenge.client.RandomOrderPickupDelayProvider;
import com.css.challenge.server.OrderActionDispatcher;
import com.css.challenge.server.OrderActionDispatcherImpl;
import com.css.challenge.server.OrderProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.time.Duration;

@Command(name = "challenge", showDefaultValues = true)
public class Main implements Runnable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  static {
    System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT: %5$s %n");
  }

  @Option(names = "--endpoint", description = "Problem server endpoint")
  String endpoint = "https://api.cloudkitchens.com";

  @Option(names = "--auth", description = "Authentication token (required)")
  String auth = "";

  @Option(names = "--name", description = "Problem name. Leave blank (optional)")
  String name = "";

  @Option(names = "--seed", description = "Problem seed (random if zero)")
  long seed = 0;

  @Option(names = "--rate", description = "Inverse order rate")
  Duration rate = Duration.ofMillis(500);

  @Option(names = "--min", description = "Minimum pickup time")
  Duration min = Duration.ofSeconds(4);

  @Option(names = "--max", description = "Maximum pickup time")
  Duration max = Duration.ofSeconds(8);

  @Override
  public void run() {

    validateOptions();

    OrderActionDispatcher dispatcher = new OrderActionDispatcherImpl();
    OrderProcessor processor = new OrderProcessor(dispatcher, 6, 6, 12);
    OrderPickupScheduler pickupScheduler = new OrderPickupScheduler(processor, new RandomOrderPickupDelayProvider(min, max));

    try {
      Client client = new Client(endpoint, auth);
      Problem problem = client.newProblem(name, seed);

      // ------ Execution harness logic goes here using rate, min and max ----

      for (Order order : problem.getOrders()) {
        LOGGER.info("Received: {}", order);
        processor.place(order);
        pickupScheduler.schedulePickup(order.getId());
        Thread.sleep(rate.toMillis());
      }

      pickupScheduler.join();

      // ----------------------------------------------------------------------

      String result = client.solveProblem(problem.getTestId(), rate, min, max, dispatcher.getActions());
      LOGGER.info("Result: {}", result);

    } catch (IOException | InterruptedException e) {
      LOGGER.error("Execution failed: {}", e.getMessage());
      pickupScheduler.shutdownNow();
    }
  }

  private void validateOptions() {
    if (min.compareTo(max) > 0) {
      throw new IllegalArgumentException("Min (" + min + ") must be less than or equal to max(" + max + ")");
    }
  }

  public static void main(String[] args) {
    new CommandLine(new Main()).execute(args);
  }
}
