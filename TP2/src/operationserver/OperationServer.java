package operationserver;

import shared.*;

import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OperationServer implements OperationServerInterface {

    private String ipAddress;
    private int capacity; // nb operations pour lequel la tache est garantie
    private int maliciousResultRate; // 0: toujours de bons resultats, 100: toujours de faux resultats

    private static AuthenticationServiceInterface authenticationServiceStub;
    private static OperationServerSharedInfo operationServerSharedInfo;

    public static void main(String[] args)
    {
        // Make sure two required params are passed
        if(args.length != 3)
        {
            System.err.println("Error: Not enough params to run this program.");
            return;
        }

        if(validateIP(args[0]) == false)
        {
            System.err.println("IP " + args[0] + " is not valid.");
            return;
        }

        // Check that both capacity and malicious result rate are integers
        String numberRegex = "^\\d+$";
        if (!args[1].matches(numberRegex) || !args[2].matches(numberRegex))
        {
            System.err.println("Error: Both capacity and malicious result rate have to " +
                    "be positive integers.");
            return;
        }

        int capacity = Integer.parseInt(args[1]);
        int maliciousResultRate = Integer.parseInt(args[2]);
        if(maliciousResultRate > 100)
        {
            System.err.println("Error: Malicious result rate has to be between 0 and 100.");
            return;
        }

        String secureValue = ApplicationProperties.getPropertyValueFromKey("secure");
        if(secureValue == null)
        {
            System.err.println("Error: Could not read secure property value in application.properties.");
            return;
        }
        else
        {
            boolean secureMode = Boolean.parseBoolean(secureValue);
            if(secureMode == true)
            {
                System.out.println("Secure mode enabled. Forcing malicious result rate to 0%.");
                maliciousResultRate = 0;
            }
        }

        System.setProperty("java.rmi.server.hostname",args[0]);
        OperationServer operationServer = new OperationServer(args[0], capacity, maliciousResultRate);

        // If the server crashes or exits
        Runtime.getRuntime().addShutdownHook(new ShutDownTask());

        // Run the server
        operationServer.run();
    }

    private static class ShutDownTask extends Thread
    {
        @Override
        public void run()
        {
            try
            {
                authenticationServiceStub.unregisterOperationServer(operationServerSharedInfo);
                System.out.println("Unregistered server from authentication service.");
            }
            catch (RemoteException e)
            {
                System.err.println("Cannot register server in AuthenticationService.");
                System.err.println();
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private static boolean validateIP(String address)
    {
        String pattern = "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|" +
                "[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"; // ip
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(address);
        return m.find();
    }

    public OperationServer(String ipAddress, int capacity, int maliciousResultRate)
    {
        this.ipAddress = ipAddress;
        this.capacity = capacity;
        this.maliciousResultRate = maliciousResultRate;
        operationServerSharedInfo = new OperationServerSharedInfo(this.ipAddress, this.capacity);

        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        // Register server in the authentication service
        String authenticationServiceIp = ApplicationProperties.getPropertyValueFromKey("serviceIp");
        authenticationServiceStub = loadAuthenticationServiceStub(authenticationServiceIp);
        try
        {
            authenticationServiceStub.registerOperationServer(operationServerSharedInfo);
        }
        catch (RemoteException e)
        {
            System.err.println("Cannot register server in AuthenticationService.");
            System.err.println();
            System.err.println("Error: " + e.getMessage());
        }
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
                    .exportObject(this, 5014);

            Registry registry = LocateRegistry.getRegistry(this.ipAddress, 5021);
            registry.rebind(this.ipAddress, stub);

            /*Registry registry = LocateRegistry.getRegistry("127.0.0.1", 5021);
            registry.rebind("operationserver", stub);*/

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

    private AuthenticationServiceInterface loadAuthenticationServiceStub(String hostname)
    {
        AuthenticationServiceInterface stub = null;

        try
        {
            Registry registry = LocateRegistry.getRegistry(hostname, 5021);
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

    public boolean acceptTask(int taskSize)
    {
        // Condition pour que la tache soit garantie
        if(taskSize <= capacity)
        {
            return true;
        }
        else
        {
            double taskRejectRate = (taskSize - capacity) / (4.0 * capacity);
            double randomValue = Math.random();
            //System.out.println("Task reject rate: " + taskRejectRate + ", Random value: " + randomValue);
            return taskRejectRate < randomValue;
        }

    }

    /*
     * Méthode accessible par RMI.
     */
    @Override
    public int calculateResult(ArrayList<String> operationsList) throws RemoteException, TaskRejectedException
    {
        // Make sure the server has enough resources to handle the task
        if(acceptTask(operationsList.size()) == false)
        {
            throw new TaskRejectedException();
        }

        boolean malicious = (Math.random() * 100) < this.maliciousResultRate;

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

        if(malicious)
        {
            System.out.println("Returning malicious result.");
            return (operationResult + (int)(6 * Math.random() + 1));
        }
        return operationResult;
    }
}
