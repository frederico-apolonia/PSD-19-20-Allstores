package Client;
import AllStoresServer.Interfaces.AllStoresServerInterface;
import ZooKeeper.ZooKeeperConnector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class Client {

	private static ZooKeeperConnector zooKeeperConnector;

	private static final String FILE_SEPARATOR = File.separator;
	private static final String ZK_PATH = System.getProperty("user.home")
			+ FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

	private static Random randomApp;

	private static String[] findAppServer(ZooKeeper zooKeeper) {
		randomApp = new Random();

		try {
			List<String> children = getNumberOfChildren(zooKeeper);

			int child = randomApp.nextInt(children.size());
			String znode = children.get(child);

			byte[] bp = zooKeeper.getData(ZK_PATH.concat("app").concat(FILE_SEPARATOR).concat(znode), false, null);
			String s = new String(bp);
			String[] data = s.split(":");

			if(data.length == 2)
				return data;

		} catch (Exception e) { System.out.println(e.getMessage()); }

		return null;
	}

	private static List<String> getNumberOfChildren(ZooKeeper zooKeeper) {
		try {
			Stat stat = zooKeeper.exists(ZK_PATH.concat("app"), false);
			if(stat != null) {
				// fetch all children from /app
				List<String> childrenList = zooKeeper.getChildren(ZK_PATH.concat("app"), false);
				Collections.sort(childrenList);

				return childrenList;
			} else { System.out.println("Node does not exist."); } 
		} catch (Exception e) { System.out.println(e.getMessage()); }

		return null;
	}

	public static void main(String[] args) throws Exception {

		AllStoresServerInterface allStoresServer = null;
		int clientID, storeID, productID, quantity, port;
		String[] znodeData;

		try {

			String zooKeeperHost = getZooKeeperHost();
			ZooKeeper zooKeeper = zooKeeperConnector.connect(zooKeeperHost);

			// getting a random app server to connect
			znodeData = findAppServer(zooKeeper);
			String host = znodeData[0];
			port = Integer.parseInt(znodeData[1]);

			// getting the registry and looking up the registry for the remote object
			Registry registry = LocateRegistry.getRegistry(host, port);
			allStoresServer = (AllStoresServerInterface) registry.lookup("ClientServerInterface");

			while(true) {
				System.out.println("Welcome to Allstores, a place where you can buy products from 600 stores!\n"
						+ "Tell us what you want to do (List, Reserve, Buy, Buyall, Cancel):");

				Scanner input = new Scanner(System.in);
				String cmdline= input.nextLine();

				if(!tryParseInt(cmdline)) {
					if("exit".compareTo(cmdline)==0)
						break;
				}

				// gives details about the methods the client can call
				if(cmdline.equals("man")) {
					System.out.println("Type List <storeID> to get a list of the products from a specific store.\n <storeID> int 1-600.\n\n"
							+ "Type Reserve <clientID> <storeID> <productID> <quantity> to make a reservation of a quantity of a specific product from a specific store.\n <clientID> int, <storeID> int 1-600, <productID> int 1-20, <quantity> int.\n\n"
							+ "Type Buy <clientID> <storeID> <productID> <quantity> to buy a quantity of a specific product from a specific store, it checks if it was reserved previously.\n <clientID> int, <storeID> int 1-600, <productID> int 1-20, <quantity> int.\n\n"
							+ "Type Buyall <clientID> to buy all the products reserved with that client ID.\n <lientID> int.\n\n"
							+ "Type Cancel <clientID> to cancel all the reservations made with that client ID.\n <clientID> int.");
				}

				String[] splitInput = cmdline.split(" ");
				// "List storeID"
				if(cmdline.toUpperCase().startsWith("LIST")) {
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
					} else {
						storeID = Integer.parseInt(splitInput[1]);
						System.out.println(list(allStoresServer, storeID));
					}
				}

				// "Reserve clientID storeID productID quantity"
				if(cmdline.toUpperCase().startsWith("RESERVE")) {
					if (splitInput.length != 5) {
						System.out.println("Error: check if you introduced all (5) the parameters right!");
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						storeID = Integer.parseInt(splitInput[2]);
						productID = Integer.parseInt(splitInput[3]);
						quantity = Integer.parseInt(splitInput[4]);
						System.out.println(reserve(allStoresServer, clientID, storeID, productID, quantity));
					}
				}

				// "Buy clientID storeID productID quantity"
				if(cmdline.toUpperCase().startsWith("BUY")) {
					if (splitInput.length != 5) {
						System.out.println("Error: check if you introduced all (5) the parameters right!");
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						storeID = Integer.parseInt(splitInput[2]);
						productID = Integer.parseInt(splitInput[3]);
						quantity = Integer.parseInt(splitInput[4]);
						System.out.println(buy(allStoresServer, clientID, storeID, productID, quantity));
					}
				}

				// "Buyall clientID"
				if(cmdline.toUpperCase().startsWith("BUYALL")) {
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						System.out.println(buyAll(allStoresServer, clientID));
					}
				}

				// "Cancel clientID"
				if(cmdline.toUpperCase().startsWith("CANCEL")) {
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						System.out.println(cancel(allStoresServer, clientID));
					}
				}
			}

			//System.out.println("Remote method invoked.");
		}
		catch (Exception e){
			System.err.println("Client exception: " + e.toString());
			e.printStackTrace();
		}
	}

	public static String buy(AllStoresServerInterface allStoresServer, int clientID, int storeID, int productID, int quantity)
			throws RemoteException {
		if (clientID == 0 || storeID == 0 || productID == 0 || quantity == 0) {
			return "Error: check if the numbers introduced match the parameters!";
		}

		return allStoresServer.buy(clientID, storeID, productID, quantity); // <sold> or <unavailable> message
	}

	public static String reserve(AllStoresServerInterface allStoresServer, int clientID, int storeID, int productID, int quantity)
			throws RemoteException {
		if (clientID == 0 || storeID == 0 || productID == 0 || quantity == 0) {
			return "Error: check if the numbers introduced match the parameters!";
		} else {
			return allStoresServer.addReservation(storeID, productID, quantity, clientID); // <reserved> or <unavailable> message
		}
	}

	public static String buyAll(AllStoresServerInterface allStoresServer, int clientID) throws RemoteException {
		if (clientID == 0) {
			return "Error: check if you introduced the right clientID!";
		}
		List<String> resultBuyAll = allStoresServer.buyAll(clientID);

		StringBuilder sb = new StringBuilder();
		sb.append("List of all the products bought:\n");
		for(String s : resultBuyAll) {
			sb.append(String.format("%s\n", s)); // <cart> message, listing all the product bought by the client
		}
		return sb.toString();
	}

	public static String list(AllStoresServerInterface allStoresServer, int storeID) throws RemoteException {

		if (storeID == 0 || storeID > 600) {
			return "Error: check if the number introduced is between 1 and 600!";
		}

		List<String> resultList = allStoresServer.getList(storeID);
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Available products on store number %d:\n", storeID));

		for (String s : resultList) {
			sb.append(String.format("%s\n", s)); // <stock> message, listing the products of the store required by the client
		}
		return sb.toString();
	}

	public static String cancel(AllStoresServerInterface allStoresServer, int clientID) throws RemoteException {
		if (clientID == 0) {
			return "Error: check if you introduced the right clientID!";
		}

		List<String> resultCancel = allStoresServer.cancel(clientID);
		StringBuilder sb = new StringBuilder();
		for(String s : resultCancel) {
			sb.append(String.format("%s\n", s)); // <cancelled> message, listing the freed products
		}
		return sb.toString();
	}

	private static String getZooKeeperHost() throws IOException {
		BufferedReader fileReader = new BufferedReader(new FileReader(ZK_PATH + "conf.cfg"));
		String result = fileReader.readLine();
		fileReader.close();
		return result;
	}

	private static boolean tryParseInt(String n) {
		try {
			Integer.parseInt(n);
			return true;
		} catch(NumberFormatException e) {
			return false;
		}
	}
}
