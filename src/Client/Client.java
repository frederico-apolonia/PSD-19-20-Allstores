package Client;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;
import AllStoresServer.Interfaces.AllStoresServerInterface;

public class Client {

	private static final int ALLSTORES_PORT = 1099;

	public static void main(String[] args) throws Exception {

		String host = "127.0.0.1"; // default host
		AllStoresServerInterface allStoresServer = null;
		int clientID, storeID, productID, quantity;

		try {

			// getting the registry and looking up the registry for the remote object
			Registry registry = LocateRegistry.getRegistry(host, ALLSTORES_PORT);
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
						list(allStoresServer, storeID);
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
						reserve(allStoresServer, clientID, storeID, productID, quantity);
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
						buy(allStoresServer, clientID, storeID, productID, quantity);
					}
				}

				// "Buyall clientID"
				if(cmdline.toUpperCase().startsWith("BUYALL")) {
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						buyAll(allStoresServer, clientID);
					}
				}

				// "Cancel clientID"
				if(cmdline.toUpperCase().startsWith("CANCEL")) {
					if (splitInput.length != 2) {
						System.out.println("Error: check if you introduced all (2) the parameters right!");
					} else {
						clientID = Integer.parseInt(splitInput[1]);
						cancel(allStoresServer, clientID);
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

	public static void buy(AllStoresServerInterface allStoresServer, int clientID, int storeID, int productID, int quantity)
			throws RemoteException {
		if (clientID == 0 || storeID == 0 || productID == 0 || quantity == 0) {
			System.out.println("Error: check if the numbers introduced match the parameters!");
			return;
		}

		String resultBuy = allStoresServer.buy(clientID, storeID, productID, quantity);
		System.out.println(resultBuy); // <sold> or <unavailable> message
	}

	public static void reserve(AllStoresServerInterface allStoresServer, int clientID, int storeID, int productID, int quantity)
			throws RemoteException {
		if (clientID == 0 || storeID == 0 || productID == 0 || quantity == 0) {
			System.out.println("Error: check if the numbers introduced match the parameters!");
		} else {
			String resultReserve = allStoresServer.addReservation(storeID, productID, quantity, clientID);
			System.out.println(resultReserve); // <reserved> or <unavailable> message
		}
	}

	public static void buyAll(AllStoresServerInterface allStoresServer, int clientID) throws RemoteException {
		if (clientID == 0) {
			System.out.println("Error: check if you introduced the right clientID!");
			return;
		}

		List<String> resultBuyAll = allStoresServer.buyAll(clientID);

		System.out.println("List of all the products bought:\n");
		for(String s : resultBuyAll) {
			System.out.println(s); // <cart> message, listing all the product bought by the client
		}
	}

	public static void list(AllStoresServerInterface allStoresServer, int storeID) throws RemoteException {

		if (storeID == 0 || storeID > 600) {
			System.out.println("Error: check if the number introduced is between 1 and 600!");
			return;
		}

		List<String> resultList = allStoresServer.getList(storeID);

		System.out.println(String.format("Available products on store number %d:\n", storeID));

		for (String s : resultList) {
			System.out.println(s); // <stock> message, listing the products of the store required by the client
		}

	}

	public static void cancel(AllStoresServerInterface allStoresServer, int clientID) throws RemoteException {
		if (clientID == 0) {
			System.out.println("Error: check if you introduced the right clientID!");
			return;
		}

		List<String> resultCancel = allStoresServer.cancel(clientID);

		for(String s : resultCancel) {
			System.out.println(s); // <cancelled> message, listing the freed products
		}
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
