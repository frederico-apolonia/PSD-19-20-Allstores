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

	private static final String ALLSTORES_REGISTRY_NAME = "ClientServerInterface";

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

	private class ThreadResult extends Thread {
		int actionCount = 0;
		int replyCount = 0;
		int unavailableCount = 0;
		long timeSum;
	}

	class ClientThread extends Thread implements Callable<ThreadResult> {

		private final boolean writeMode;
		private final int singleStore;
		private final int clientID;
		private final AllStoresServerInterface allStoresServer;
		private ThreadResult result = new ThreadResult();
		private String randomAppServer, host;
		private int port;

		ClientThread(boolean writeMode, int singleStore, int clientID) throws RemoteException, NotBoundException, IOException, InterruptedException {
			this.writeMode = writeMode;
			this.singleStore = singleStore;
			this.clientID = clientID;

			String zooKeeperHost = getZooKeeperHost();
			ZooKeeper zooKeeper = zooKeeperConnector.connect(zooKeeperHost);

			// getting a random app server
			randomAppServer = findAppServer(zooKeeper);
			assert randomAppServer != null;
			String[] appServerSplit = randomAppServer.split(":");
			assert appServerSplit.length == 2;
			host = appServerSplit[0];
			port = Integer.parseInt(appServerSplit[1]);

			Registry registry = LocateRegistry.getRegistry(host, port);
			this.allStoresServer = (AllStoresServerInterface) registry.lookup(ALLSTORES_REGISTRY_NAME);
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
			try {
				while (!stopThreads) {
					product = rnd.nextInt(ALLSTORES_MAX_PRODUCTID) + 1;
					if (randomStores) {
						// random store
						store = rnd.nextInt(ALLSTORES_MAX_SHOPID) + 1;
					}
					if (writeMode) {
						start = System.currentTimeMillis();
						actionResult = Client.buy(this.allStoresServer, this.clientID, store, product,
								BUY_RESERVE_QUANTITY);
						end = System.currentTimeMillis();
					} else {
						start = System.currentTimeMillis();
						actionResult = Client.reserve(this.allStoresServer, this.clientID, store, product,
								BUY_RESERVE_QUANTITY);
						end = System.currentTimeMillis();
					}
					this.result.actionCount++;

					if (!actionResult.toLowerCase().contains("busy") && !actionResult.toLowerCase().contains("error")) {
						this.result.replyCount++;
						this.result.timeSum += (end - start);
					}
					if (actionResult.toLowerCase().contains("unavailable")) {
						this.result.unavailableCount++;
					}
				}

			} catch (RemoteException e) {
				e.printStackTrace();
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

		TrafficGeneratorPerformanceMonitoring tgpm = new TrafficGeneratorPerformanceMonitoring();
		try {
			tgpm.runClients(options);
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void runClients(Options options) throws RemoteException, NotBoundException, InterruptedException, IOException {
		List<ClientThread> threads = new ArrayList<>();
		Random random = new Random();
		int randomClientID;

		// initialize threads
		for (int i = 0; i < options.numberClients; i++) {
			randomClientID = random.nextInt();
			threads.add(i, new ClientThread(options.writeMode, options.storeID, randomClientID));
		}

		// start the threads
		for (ClientThread ct : threads) {
			ct.start();
		}

		// 1 sec clock for visual updates
		Timer visualUpdateTimer = new Timer();
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

				printProgress(updateResults);

			}
		}, 1000, 1000);

		List<ThreadResult> results = new ArrayList<>();
		Timer trafficTimeTimer = new Timer();
		trafficTimeTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				visualUpdateTimer.cancel();
				stopThreads = true;

				for (ClientThread ct : threads) {
					try {
						results.add(ct.call());
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				printFinalResults(results);
			}
		}, (options.trafficTime * 1000));

	}

	private void printProgress(List<ThreadResult> updateResults) {
		double totalNumberRequests = 0, completedRequests = 0, unavailableRequests = 0;
		long totalLatency = 0;
		for (ThreadResult tr : updateResults) {
			totalNumberRequests += tr.actionCount;
			completedRequests += tr.replyCount;
			unavailableRequests += tr.unavailableCount;
			totalLatency += tr.timeSum;
		}

		double fulfilledRate = ((totalNumberRequests - unavailableRequests) / completedRequests);
		System.out.println(String.format(
				"Elapsed time: %d seconds\nThroughput: %.2f op/s\nAverage latency: %.2f ms\nFulfilled rate: %.2f%%",
				elapsedTime, (completedRequests / elapsedTime), (totalLatency / totalNumberRequests), fulfilledRate));
		System.out.println("");
		System.out.println("");

	}

	private void printFinalResults(List<ThreadResult> results) {
		double totalNumberRequests = 0, completedRequests = 0, unavailableRequests = 0;

		for (ThreadResult tr : results) {
			try {
				tr.join();
				totalNumberRequests += tr.actionCount;
				completedRequests += tr.replyCount;
				unavailableRequests += tr.unavailableCount;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		double fulfilledRate = ((totalNumberRequests - unavailableRequests) / totalNumberRequests);
		double completionRate = ((totalNumberRequests - completedRequests) / totalNumberRequests);

		System.out.println(String.format(
				"Total number of sent requests: %f\nThe number of received replies: %f\nExecution time: %d seconds\nThroughput: %.2f op/s\nFulfilled rate: %.2f%\nCompletion rate: %.2f%",
				totalNumberRequests, completedRequests, elapsedTime, (completedRequests / elapsedTime), fulfilledRate, completionRate));
		System.out.println("");
		System.out.println("");
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

	private static String findAppServer(ZooKeeper zooKeeper) {
		Random randomApp = new Random();

		try {
			List<String> children = getNumberOfChildren(zooKeeper);
			assert children != null;
			int child = randomApp.nextInt(children.size());
			String znode = children.get(child);

			byte[] bp = zooKeeper.getData("/app/".concat(znode), false, null);
			return new String(bp);

		} catch (Exception e) { System.out.println(e.getMessage()); }

		return null;
	}

	private static List<String> getNumberOfChildren(ZooKeeper zooKeeper) {
		try {
			Stat stat = zooKeeper.exists("/app", false);
			if(stat != null) {
				// fetch all children from /app
				List<String> childrenList = zooKeeper.getChildren("/app", false);
				Collections.sort(childrenList);

				return childrenList;
			} else { System.out.println("Node does not exist."); } 
		} catch (Exception e) { System.out.println(e.getMessage()); }

		return null;
	}

	private static String getZooKeeperHost() throws IOException {
		BufferedReader fileReader = new BufferedReader(new FileReader(ZK_PATH + "conf.cfg"));
		String result = fileReader.readLine();
		fileReader.close();
		return result;
	}
}
