package operationserver;

import shared.OperationServerInterface;

import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

public class OperationServer implements OperationServerInterface {

    private int capacity; // nb operations pour lequel la tache est garantie
    private int maliciousResultRate; // 0: toujours de bons resultats, 100: toujours de faux resultats


    public static void main(String[] args)
    {
        // Make sure two required params are passed
        if(args.length != 2)
        {
            System.err.println("Error: Capacity and malicious result rate both have to " +
                    "be specified to execute this program.");
            return;
        }

        // Check that both params are integers
        String numberRegex = "^\\d+$";
        if (!args[0].matches(numberRegex) || !args[1].matches(numberRegex))
        {
            System.err.println("Error: Both capacity and malicious result rate have to " +
                    "be positive integers.");
            return;
        }

        int capacity = Integer.parseInt(args[0]);
        int maliciousResultRate = Integer.parseInt(args[1]);
        if(maliciousResultRate > 100)
        {
            System.err.println("Error: Malicious result rate has to be between 0 and 100.");
            return;
        }

        OperationServer operationServer = new OperationServer(capacity, maliciousResultRate);
        operationServer.run();
    }

    public OperationServer(int capacity, int maliciousResultRate)
    {
        this.capacity = capacity;
        this.maliciousResultRate = maliciousResultRate;
    }

    private void run()
    {
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        try
        {
            OperationServerInterface stub = (OperationServerInterface) UnicastRemoteObject
                    .exportObject(this, 0);

            Registry registry = LocateRegistry.getRegistry();
            registry.rebind("operationserver", stub);
            System.out.println("OperationServer ready.");
        }
        catch (ConnectException e)
        {
            System.err
                    .println("Cannot connect to RMI register. Please try 'rmiregistry &' command in bin directory.");
            System.err.println();
            System.err.println("Error: " + e.getMessage());
        }
        catch (Exception e)
        {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /*
     * Méthode accessible par RMI.
     */
    @Override
    public int calculateResult(ArrayList<String> operationsList) throws RemoteException
    {
        int operationResult = 0;
        for(String operation : operationsList)
        {
            String[] splitedOp = operation.split(" ");
            String opName = splitedOp[0];
            int opValue = Integer.parseInt(splitedOp[1]);

            if(opName.equals("pell"))
            {
                operationResult += (Operations.pell(opValue) % 4000);
            }
            else if(opName.equals("prime"))
            {
                operationResult += (Operations.prime(opValue) % 4000);
            }
        }

        return operationResult;
    }
}
