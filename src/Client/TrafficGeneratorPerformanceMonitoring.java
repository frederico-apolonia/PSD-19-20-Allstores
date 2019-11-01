package Client;

import AllStoresServer.Interfaces.AllStoresServerInterface;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrafficGeneratorPerformanceMonitoring {

    private static final String ALLSTORES_HOST = "127.0.0.1";
    private static final int ALLSTORES_PORT = 1099;
    private static final String ALLSTORES_REGISTRY_NAME = "ClientServerInterface";

    private static final int ALLSTORES_MAX_SHOPID = 600;
    private static final int ALLSTORES_MAX_PRODUCTID = 20;
    private static final int BUY_RESERVE_QUANTITY = 1;

    private static class Options {
        int numberClients;
        boolean writeMode = false;
        int storeID = -1;
        int trafficTime;
    }

    public static void main(String[] args) {
        Options clientOptions = readArguments(args);
        System.out.println(String.format("Number of clients: %d\nWrite mode = %b\nStoreID = %d\nTraffic time = %d",
                clientOptions.numberClients, clientOptions.writeMode, clientOptions.storeID, clientOptions.trafficTime));

        // ...
        // todo inicializar X threads durante Y segundos (armar um alarme)
    }

    class ClientThead extends Thread {

        private final boolean writeMode;
        private final int singleStore;
        private final int clientID;
        private final AllStoresServerInterface allStoresServer;
        private int actionCount = 0;
        private int unavailableCount = 0;
        private long timeSum;

        ClientThead (boolean writeMode, int singleStore, int clientID) throws RemoteException, NotBoundException {
            this.writeMode = writeMode;
            this.singleStore = singleStore;
            this.clientID = clientID;

            Registry registry = LocateRegistry.getRegistry(ALLSTORES_HOST, ALLSTORES_PORT);
            this.allStoresServer = (AllStoresServerInterface) registry.lookup(ALLSTORES_REGISTRY_NAME);
        }

        public void run() {
            // todo falta ver como retornar valores quando o pai chama as threads de volta
            int store = singleStore;
            int product;
            long start, end;
            Random rnd = new Random();
            try {
                while (true) {
                    product = rnd.nextInt(ALLSTORES_MAX_PRODUCTID) + 1;
                    if (singleStore != -1) {
                        // random store
                        store = rnd.nextInt(ALLSTORES_MAX_SHOPID) + 1;
                    }
                    if (writeMode) {
                        start = System.currentTimeMillis();
                        Client.buy(this.allStoresServer, this.clientID, store, product, BUY_RESERVE_QUANTITY);
                        end = System.currentTimeMillis();
                    } else {
                        start = System.currentTimeMillis();
                        Client.reserve(this.allStoresServer, this.clientID, store, product, BUY_RESERVE_QUANTITY);
                        end = System.currentTimeMillis();
                    }
                    // todo if busy => nao se conta
                    // todo if unavailable => counterUnavailable
                    actionCount++;
                    timeSum += (end - start);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Reads the arguments passed from the command line and creates a new Option object with the input parsed correctly.
     * @param args cli args
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
        } catch ( ArrayIndexOutOfBoundsException e) {
            System.err.println("Program requires at least TWO arguments, number of clients and active seconds.");
            System.err.println("$ tgpm <c> [-w] [-s <sid>] <s>");
            System.exit(0);
        }

        String argsString = String.join(" ",args);

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

}
