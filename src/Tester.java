import tests.PaymentTest;
import util.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static util.Utilities.logError;
import static util.Utilities.logInfo;

public class Tester {

	private static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(20);
	private static final Thread MAIN_THREAD = Thread.currentThread();
	private static volatile boolean terminate = false;
	private static volatile boolean active = true;
	private static final int SHUTDOWN_TIME = 60; // seconds to wait for tests to finish

	public static void main(String[] args) {
		//
		// DESCRIPTION
		//
		if (args.length == 0) {
			printHelp();
			System.exit(0);
		}
		//
		// INIT
		//
		if (args.length == 1) {
			File directory = new File(args[0]);
			if (directory.isDirectory()) {
				XMLGenerator.generatePaymentFiles(directory);
			} else {
				logError("Parameter " + args[0] + " is not a directory!");
			}
			System.exit(0);
		}
		logInfo("");
		logInfo("Performance Test Tool started!");
		if (args.length < 2) {
			logError("Missing parameters!");
			System.exit(1);
		}
		String[] connectionString = args[0].split(";", -1);
		Connection.CONNECTION_HOST = connectionString[0];
		Connection.CONNECTION_USER = connectionString[1];
		Connection.CONNECTION_PASSWORD = connectionString[2];
		File inputFile = new File(args[1]);
		if (!inputFile.exists()) {
			logError("Test plan file (" + args[1] + ") does not exist!");
			System.exit(1);
		}

		createShutDownHook();

		Path logParentPath = inputFile.toPath();
		PaymentTest.resultLog = new Logger(logParentPath.resolveSibling(PaymentTest.RESULT_LOG_FILENAME), PaymentTest.RESULT_LOG_COLUMNS);
		PaymentTest.resultLog.start();
		PaymentTest.failedPaymentLog = new Logger(logParentPath.resolveSibling(PaymentTest.FAILED_PAYMENTS_LOG_FILENAME), PaymentTest.FAILED_PAYMENTS_LOG_COLUMNS);
		PaymentTest.failedPaymentLog.start();

		createJobsFromTestPlan(inputFile);

		//
		// WAIT
		//
		while (!EXECUTOR.isTerminated() && !terminate) {
			try {
				EXECUTOR.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			} catch (InterruptedException ignore) {
			}
		}

		//
		// SHUTDOWN
		//
		if (EXECUTOR.isTerminated()) {
			logInfo("All scheduled tests finished!");
		}
		// if JVM termination issued from shutdown hook (user interruption), let's wait a bit for scheduled tests to finish
		if (terminate && !EXECUTOR.isTerminated()) {
			try {
				logInfo("Waiting %s seconds for tests to finish", SHUTDOWN_TIME);
				if (!EXECUTOR.awaitTermination(SHUTDOWN_TIME, TimeUnit.SECONDS)) {
					logInfo("Some tests are still running. Forcing shutdown and waiting %s more seconds", SHUTDOWN_TIME);
					List<Runnable> droppedTasks = EXECUTOR.shutdownNow();
					EXECUTOR.awaitTermination(SHUTDOWN_TIME, TimeUnit.SECONDS);
					logInfo("%d scheduled tests dropped", droppedTasks.size());
				}
			} catch (InterruptedException ignore) {
			}
		}
		PaymentTest.resultLog.close();
		PaymentTest.failedPaymentLog.close();
		logInfo("Shutdown");
		active = false;
	}

	private static void createJobsFromTestPlan(File file) {
		try (Stream<String> lines = Files.lines(file.toPath())) {
			String[][] tests = lines.filter(x -> !Utilities.isEmpty(x)).map(x -> x.split(";")).toArray(String[][]::new);
			for (String[] test : tests) {
				String startDateTime = test[0];
				String testFilePath = test[1];
				if (!new File(test[1]).exists()) {
					logError("Skipping test scheduled at %s, because file %s does not exist!", startDateTime, testFilePath);
				} else {
					long startEpoch;
					PaymentTest paymentTest = new PaymentTest(testFilePath, test[2], test[3]);
					// special keyword to launch test immediately
					if ("ASAP".equalsIgnoreCase(startDateTime)) {
						startEpoch = Instant.now().getEpochSecond() + 2;
					} else {
						startEpoch = LocalDateTime.parse(startDateTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.of("Europe/Riga")).toEpochSecond();
					}
					long nowEpoch = Instant.now().getEpochSecond();
					if (nowEpoch > startEpoch) { // start time in past
						logInfo("Skipping test file (%s) because it's scheduled in past (%s)", testFilePath, startDateTime);
					} else {
						EXECUTOR.schedule(paymentTest, startEpoch - nowEpoch, TimeUnit.SECONDS);
						logInfo("Test file %s scheduled at %s", testFilePath, startDateTime);
					}
				}
			}
		} catch (Exception e) {
			logError("Error while reading test plan file: %s", e.getMessage());
			e.printStackTrace();
		}
		// we won't accept new tasks, so let's shutdown executor
		EXECUTOR.shutdown();
	}

	private static void createShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {

			@Override
			public void run() {
				if (!EXECUTOR.isTerminated()) {
					logInfo("User initiated shutdown");
					terminate = true;
					MAIN_THREAD.interrupt();
					// wait for main thread to shutdown
					while (active && MAIN_THREAD.isAlive()) {
						try {
							Thread.sleep(200);
						} catch (InterruptedException ignore) {
						}
					}
				}
			}
		});
	}
}
