package Client;

import AllStoresServer.Interfaces.AllStoresServerInterface;
import ZooKeeper.ZooKeeperConnector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class TrafficGeneratorPerformanceMonitoring {
	private static ZooKeeperConnector zooKeeperConnector = new ZooKeeperConnector();

	private static final String FILE_SEPARATOR = File.separator;
	private static final String ZK_PATH = System.getProperty("user.home")
			+ FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

	private static final int ALLSTORES_MAX_SHOPID = 600;
	private static final int ALLSTORES_MAX_PRODUCTID = 20;
	private static final int BUY_RESERVE_QUANTITY = 1;

	private static boolean stopThreads = false;
	private static int elapsedTime;

	private static class Options {
		int numberClients;
		boolean writeMode = false;
		int storeID = -1;
		int trafficTime;
	}

	private class ThreadResult {
		int actionCount = 0;
		int replyCount = 0;
		int unavailableCount = 0;
		long timeSum;
	}

	class ClientThread extends Thread implements Callable<ThreadResult> {

		private final boolean writeMode;
		private final int singleStore;
		private final int clientID;
		private AllStoresServerInterface allStoresServer;
		private ZooKeeper zooKeeper;
		private ThreadResult result = new ThreadResult();
		private String randomAppServer;

		ClientThread(boolean writeMode, int singleStore, int clientID) throws IOException, InterruptedException {
			this.writeMode = writeMode;
			this.singleStore = singleStore;
			this.clientID = clientID;

			// connect to ZooKeeper
			String zooKeeperHost = getZooKeeperHost();
			zooKeeper = zooKeeperConnector.connect(zooKeeperHost);
		}

		public ThreadResult getResult() {
			return result;
		}

		public void run() {
			int store = singleStore;
			int product;
			long start, end;
			Random rnd = new Random();
			String actionResult;
			boolean randomStores = singleStore == -1;

				while (!stopThreads) {
					// get random app server so that we're not always pinging the same one
					randomAppServer = Client.getRandomAppServer(zooKeeper);
					assert randomAppServer != null;
					try {
						allStoresServer = Client.connectToAppServer(randomAppServer);
					} catch (Exception e) {
						continue;
					}
					product = rnd.nextInt(ALLSTORES_MAX_PRODUCTID) + 1;
					if (randomStores) {
						// random store
						store = rnd.nextInt(ALLSTORES_MAX_SHOPID) + 1;
					}
					start = System.currentTimeMillis();
					try {
						if (writeMode) {
							Client.reserve(this.allStoresServer, this.clientID, store, product,
									BUY_RESERVE_QUANTITY);
							actionResult = Client.buy(this.allStoresServer, this.clientID, store, product,
									BUY_RESERVE_QUANTITY);

						} else {
							actionResult = Client.reserve(this.allStoresServer, this.clientID, store, product,
									BUY_RESERVE_QUANTITY);
							Client.cancel(this.allStoresServer, this.clientID);
						}
					} catch (Exception e) {
						actionResult = "error";
					} finally {
						end = System.currentTimeMillis();
						this.result.actionCount++;
					}

					if (!actionResult.toLowerCase().contains("busy") && !actionResult.toLowerCase().contains("error")) {
						this.result.replyCount++;
						this.result.timeSum += (end - start);
					}
					if (actionResult.toLowerCase().contains("unavailable")) {
						this.result.unavailableCount++;
					}

				}


		}

		@Override
		public ThreadResult call() throws Exception {
			return this.result;
		}
	}

	public static void main(String[] args) {
		Options options = readArguments(args);
		System.out.println(String.format("Number of clients: %d\nWrite mode = %b\nStoreID = %d\nTraffic time = %d",
				options.numberClients, options.writeMode, options.storeID, options.trafficTime));
		System.out.println("Elapsed time; Throughput; Average latency; Fulfilled rate");

		TrafficGeneratorPerformanceMonitoring tgpm = new TrafficGeneratorPerformanceMonitoring();
		try {
			tgpm.runClients(options);
		} catch (Exception e) {
			// do nothing
		}
	}

	private void runClients(Options options) throws RemoteException, NotBoundException, InterruptedException, IOException {
		List<ClientThread> threads = new ArrayList<>();
		Random random = new Random();
		int randomClientID;

		// initialize threads
		for (int i = 0; i < options.numberClients; i++) {
			randomClientID = random.nextInt() & Integer.MAX_VALUE;
			threads.add(i, new ClientThread(options.writeMode, options.storeID, randomClientID));
		}

		// start the threads
		for (ClientThread ct : threads) {
			ct.start();
		}

		// 1 sec clock for visual updates
		Timer visualUpdateTimer = new Timer();
		final List<ThreadResult>[] previousResults = new List[]{new ArrayList<>()};
		visualUpdateTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				elapsedTime++;
				List<ThreadResult> updateResults = new ArrayList<>();

				for (ClientThread ct : threads) {
					try {
						updateResults.add(ct.getResult());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				printProgress(updateResults, previousResults[0]);
				previousResults[0] = new ArrayList<>();
				for (ThreadResult tr : updateResults) {
					ThreadResult tmp = new ThreadResult();
					tmp.actionCount = tr.actionCount;
					tmp.replyCount = tr.replyCount;
					tmp.unavailableCount = tr.unavailableCount;
					tmp.timeSum = tr.timeSum;
					previousResults[0].add(tmp);
				}

			}
		}, 1000, 1000);

		List<ThreadResult> results = new ArrayList<>();
		Timer trafficTimeTimer = new Timer();
		trafficTimeTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				elapsedTime++;
				visualUpdateTimer.cancel();
				stopThreads = true;

				for (ClientThread ct : threads) {
					try {
						results.add(ct.call());
						ct.join();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				printFinalResults(results);
			}
		}, (options.trafficTime * 1000));

	}

	private void printProgress(List<ThreadResult> updateResults, List<ThreadResult> previousResults) {
		double totalNumberRequests = 0, completedRequests = 0, unavailableRequests = 0;
		long totalLatency = 0;
		for (int i = 0; i < updateResults.size(); i++) {
			ThreadResult current = updateResults.get(i);
			ThreadResult currentPrevious;
			if(previousResults.size() == 0) {
				currentPrevious= new ThreadResult();
				currentPrevious.timeSum = 0;
			} else {
				currentPrevious = previousResults.get(i);
			}

			totalNumberRequests += (current.actionCount - currentPrevious.actionCount);
			completedRequests += (current.replyCount - currentPrevious.replyCount);
			unavailableRequests += (current.unavailableCount - currentPrevious.unavailableCount);
			totalLatency += (current.timeSum - currentPrevious.timeSum);
		}
		double fulfilledRate = ((completedRequests - unavailableRequests) / totalNumberRequests) * 100;
		System.out.println(String.format(
				"ET: %d seconds T: %.2f op/s AL: %.2f ms FR: %.2f%%",
				elapsedTime, completedRequests, (totalLatency / totalNumberRequests), fulfilledRate));

	}

	private void printFinalResults(List<ThreadResult> results) {
		int totalNumberRequests = 0, completedRequests = 0, unavailableRequests = 0;

		for (ThreadResult tr : results) {
			totalNumberRequests += tr.actionCount;
			completedRequests += tr.replyCount;
			unavailableRequests += tr.unavailableCount;
		}

		double fulfilledRate = (completedRequests / totalNumberRequests) * 100;
		double completionRate = (completedRequests / totalNumberRequests) * 100;
		double throughput = completedRequests / elapsedTime;

		System.out.println("***************************************************");
		System.out.println(String.format(
				"Finished\n" +
						"Total number of sent requests: %d\n" +
						"The number of received replies: %d\n" +
						"Execution time: %d seconds\n" +
						"Throughput: %.2f op/s\n" +
						"Fulfilled rate: %.2f%%\n" +
						"Completion rate: %.2f%%",
				totalNumberRequests, completedRequests, elapsedTime, throughput, fulfilledRate, completionRate));
	}

	/**
	 * Reads the arguments passed from the command line and creates a new Option
	 * object with the input parsed correctly.
	 * 
	 * @param args
	 *            cli args
	 * @return new Options object with the inputs parsed
	 */
	private static Options readArguments(String[] args) {
		Options result = new Options();
		try {
			result.numberClients = Integer.parseInt(args[0]);
			result.trafficTime = Integer.parseInt(args[args.length - 1]);
		} catch (java.lang.NumberFormatException e) {
			System.err.println("Error while converting to integer. Did you run the program correctly?");
			System.err.println("$ tgpm <c> [-w] [-s <sid>] <s>");
			System.exit(0);
		} catch (ArrayIndexOutOfBoundsException e) {
			System.err.println("Program requires at least TWO arguments, number of clients and active seconds.");
			System.err.println("$ tgpm <c> [-w] [-s <sid>] <s>");
			System.exit(0);
		}

		String argsString = String.join(" ", args);

		if (argsString.contains("-w")) {
			result.writeMode = true;
		}

		Pattern p = Pattern.compile("-s ([0-9]+)");
		Matcher m = p.matcher(argsString);
		if (m.find()) {
			if (args.length > 3) {
				result.storeID = Integer.parseInt(m.group(1));
			} else {
				System.err.println("When single store mode is enabled, the program requires AT LEAST 4 arguments.");
				System.err.println("$ tgpm <c> [-w] [-s <sid>] <s>");
				System.exit(0);
			}
		}
		return result;
	}

	private static String getZooKeeperHost() throws IOException {
		BufferedReader fileReader = new BufferedReader(new FileReader(ZK_PATH + "conf.cfg"));
		String result = fileReader.readLine();
		fileReader.close();
		return result;
	}
}
