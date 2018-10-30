package loadbalancer;

import shared.*;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;

public class LoadBalancer {

    private AuthenticationServiceInterface authenticationServiceStub;
    private ArrayList<OperationServerInterface> operationServerStubs;
    private static final String OPERATIONS_DIRECTORY = "operations";
    private boolean secureMode;

    public static void main(String[] args)
    {
        if(args.length == 0)
        {
            System.err.println("Error: You need to specify the operations filename " +
                    "to execute this program.");
            return;
        }

        String secureValue = ApplicationProperties.getPropertyValueFromKey("secure");
        if(secureValue == null)
        {
            System.err.println("Error: Could not read secure property value in application.properties.");
            return;
        }

        LoadBalancer loadBalancer = new LoadBalancer(Boolean.parseBoolean(secureValue));
        loadBalancer.run(args[0]);
    }

    private LoadBalancer(boolean secureMode)
    {
        this.secureMode = secureMode;

        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        authenticationServiceStub = loadAuthenticationServiceStub("127.0.0.1");
        operationServerStubs = loadOperationServerStubs();
    }

    private AuthenticationServiceInterface loadAuthenticationServiceStub(String hostname)
    {
        AuthenticationServiceInterface stub = null;

        try
        {
            Registry registry = LocateRegistry.getRegistry(hostname);
            stub = (AuthenticationServiceInterface) registry.lookup("authenticationservice");
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

    private ArrayList<OperationServerInterface> loadOperationServerStubs() {

        ArrayList<OperationServerInterface> stubList = new ArrayList<>();
        try
        {
            for(OperationServerSharedInfo serverInfo : authenticationServiceStub.getAvailableServersInfo())
            {
                Registry registry = LocateRegistry.getRegistry(serverInfo.getIpAddress());
                OperationServerInterface stub = (OperationServerInterface) registry.lookup(serverInfo.getIpAddress());
                stubList.add(stub);
            }
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

        return stubList;
    }

    private void run(String operationsFilename)
    {
        ArrayList<String> operationsList = readOperationsFile(operationsFilename);
        int nbAvailableServers = operationServerStubs.size();

        try
        {
            int result = operationServerStubs.get(0).calculateResult(operationsList);
            System.out.println("The result is : " + result + ".");
        }
        catch (RemoteException | TaskRejectedException e)
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
