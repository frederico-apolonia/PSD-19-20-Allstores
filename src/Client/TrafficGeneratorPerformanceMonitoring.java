package Client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrafficGeneratorPerformanceMonitoring {

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
