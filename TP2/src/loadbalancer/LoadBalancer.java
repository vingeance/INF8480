package loadbalancer;

import shared.OperationServerInterface;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;

public class LoadBalancer {

    private OperationServerInterface operationServerStub = null;
    private static final String OPERATIONS_DIRECTORY = "operations";

    public static void main(String[] args)
    {
        if(args.length != 1)
        {
            System.err.println("Error: You need to specify the operations filename " +
                    "to execute this program.");
            return;
        }

        LoadBalancer loadBalancer = new LoadBalancer();
        loadBalancer.run(args[0]);
    }

    private LoadBalancer()
    {
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        operationServerStub = loadOperationServerStub("127.0.0.1");
    }

    private OperationServerInterface loadOperationServerStub(String hostname) {
        OperationServerInterface stub = null;

        try
        {
            Registry registry = LocateRegistry.getRegistry(hostname);
            stub = (OperationServerInterface) registry.lookup("operationserver");
        }
        catch (NotBoundException e)
        {
            System.err.println("Error: The name '" + e.getMessage()
                    + "' is not defined in the registry.");
        }
        catch (RemoteException e)
        {
            System.err.println("Error: " + e.getMessage());
        }

        return stub;
    }

    private void run(String operationsFilename)
    {
        ArrayList<String> operationsList = readOperationsFile(operationsFilename);
        try
        {
            int result = operationServerStub.calculateResult(operationsList);
            System.out.println("The result is : " + result + ".");
        }
        catch (RemoteException e)
        {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private ArrayList<String> readOperationsFile(String operationsFilename)
    {
        ArrayList<String> allOperations = new ArrayList<>();
        File operationsFile = new File(OPERATIONS_DIRECTORY + "/" + operationsFilename);

        try
        {
            BufferedReader in = new BufferedReader(new FileReader(operationsFile));
            String readedOperation = "";
            while ((readedOperation = in.readLine()) != null)
            {
                allOperations.add(readedOperation);
            }
        }
        catch (IOException e)
        {
            System.err.println("Error: " + e.getMessage());
        }

        return allOperations;
    }

}
