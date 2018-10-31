package loadbalancer;

import shared.*;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadBalancer {

    private AuthenticationServiceInterface authenticationServiceStub;
    private ArrayList<OperationServerInterface> operationServerStubs;
    private ArrayList<OperationServerSharedInfo> operationServersInfos;
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

        String authenticationServiceIp = ApplicationProperties.getPropertyValueFromKey("serviceIp");
        authenticationServiceStub = loadAuthenticationServiceStub(authenticationServiceIp);
        try {
            operationServersInfos = authenticationServiceStub.getAvailableServersInfo();
        } catch (RemoteException e) {
            System.out.println("Error : " + e.getMessage());
        }
        operationServerStubs = loadOperationServerStubs();
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

    private ArrayList<OperationServerInterface> loadOperationServerStubs() {

        ArrayList<OperationServerInterface> stubList = new ArrayList<>();
        try
        {
            for(OperationServerSharedInfo serverInfo : operationServersInfos)
            {
                Registry registry = LocateRegistry.getRegistry(serverInfo.getIpAddress(), 5021);
                System.out.println(serverInfo.getIpAddress());
                //System.out.println(Arrays.toString(registry.list()));
                OperationServerInterface stub = null;
                stub = (OperationServerInterface) registry.lookup(serverInfo.getIpAddress());

                //System.out.println(stub);
                stubList.add(stub);
                ArrayList<String> test = new ArrayList<>();
                test.add("prime 8673");
                try {
                    stub.calculateResult(test);
                } catch (TaskRejectedException e) {
                    e.printStackTrace();
                }

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

        /*try {
            ArrayList<String> test = new ArrayList<>();
            test.add("prime 8673");
            stubList.get(1).calculateResult(test);
        } catch (RemoteException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (TaskRejectedException e) {
            e.printStackTrace();
        }*/

        return stubList;
    }

    private void run(String operationsFilename)
    {
        ArrayList<String> operationsList = readOperationsFile(operationsFilename);
        int nbAvailableServers = operationServerStubs.size();
        final int[] totalResult = {0};

        Thread[] threads = new Thread[nbAvailableServers];
        int[] activeServersCapacities = new int[nbAvailableServers];
        while(operationsList.size() > 0)
        {
            for (int i = 0; i < threads.length; i++)
            {
                if (threads[i] == null || !threads[i].isAlive())
                {
                    activeServersCapacities[i] = operationServersInfos.get(i).getCapacity();
                    System.out.println("Thread " + i + " is dead. Capacity of the server : " + activeServersCapacities[i]);
                }
            }

            Integer threadNumber = getServerWithBiggestCapacity(activeServersCapacities);
            //System.out.println("Server with biggest capacity is number " + threadNumber);
            activeServersCapacities = new int[nbAvailableServers];
            if(threadNumber == null)
            {
                continue;
            }

            int serverCapacity = operationServersInfos.get(threadNumber).getCapacity();

            List<String> task = operationsList;
            if(2*serverCapacity <= operationsList.size())
            {
                task = operationsList.subList(0, 2 * serverCapacity);
            }

            List<String> finalTask = new ArrayList<>();
            finalTask.addAll(task);

            operationsList.subList(0, task.size()).clear();

            threads[threadNumber] = new Thread(new Runnable() {
                public void run() {
                    try
                    {
                        int result = operationServerStubs.get(threadNumber).calculateResult((ArrayList<String>) finalTask);
                        System.out.println("The result is : " + result + ".");
                        totalResult[0] += result;
                    }
                    catch (RemoteException | TaskRejectedException e)
                    {
                        operationsList.addAll(finalTask);
                        System.err.println("Error: " + e.getMessage());
                    }
                }
            });
            threads[threadNumber].start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("Final result : " + totalResult[0] + ".");

        /*ArrayList<String> operationsList2 = readOperationsFile(operationsFilename);
        try {
            int result = operationServerStubs.get(0).calculateResult(operationsList2);
            System.out.println("Expected result : " + result + ".");
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (TaskRejectedException e) {
            e.printStackTrace();
        }*/
    }

    public Integer getServerWithBiggestCapacity(int[] serverCapacities)
    {
        Integer biggestCapacity = 0;
        boolean found = false;
        for(int i = 0; i < serverCapacities.length; i++)
        {
            if(serverCapacities[i] > biggestCapacity)
            {
                biggestCapacity = i;
                found = true;
            }
        }
        if (found)
            return biggestCapacity;
        else
            return null;
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
