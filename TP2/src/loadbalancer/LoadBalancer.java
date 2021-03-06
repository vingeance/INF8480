package loadbalancer;

import shared.*;

import java.io.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static shared.ApplicationProperties.getPropertyValueFromKey;

public class LoadBalancer {

    private static String username;
    private String password;
    private static AuthenticationServiceInterface authenticationServiceStub;
    private ArrayList<OperationServerInterface> operationServerStubs;
    private ArrayList<OperationServerSharedInfo> operationServersInfos;
    private static final String OPERATIONS_DIRECTORY = "operations";
    private static AtomicInteger totalResult = new AtomicInteger(0);

    public static void main(String[] args)
    {
        if(args.length != 3)
        {
            System.err.println("Error: Not enough parameters to execute the program.");
            return;
        }

        String secureValue = getPropertyValueFromKey("secure");
        if(secureValue == null)
        {
            System.err.println("Error: Could not read secure property value in application.properties.");
            return;
        }

        LoadBalancer loadBalancer = new LoadBalancer(args[0], args[1]);

        // If the loadbalancer crashes or exits
        Runtime.getRuntime().addShutdownHook(new ShutDownTask());

        if(Boolean.parseBoolean(secureValue))
        {
            // Mode securise
            loadBalancer.runSecurely(args[2]);
        }
        else
        {
            // Mode non-securise
            loadBalancer.runInsecurely(args[2]);
        }
    }

    /**
     * Executed when LoadBalancer is stopped. Unregister from the service.
     * **/
    private static class ShutDownTask extends Thread
    {
        @Override
        public void run()
        {
            try
            {
                authenticationServiceStub.unregisterLoadBalancer(username);
                System.out.println("Unregistered loadbalancer from authentication service.");
            }
            catch (RemoteException e)
            {
                System.err.println("Cannot unregister loadbalancer from AuthenticationService.");
                System.err.println();
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private LoadBalancer(String username, String password)
    {
        LoadBalancer.username = username;
        this.password = password;
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManager());
        }

        String authenticationServiceIp = getPropertyValueFromKey("serviceIp");
        authenticationServiceStub = loadAuthenticationServiceStub(authenticationServiceIp);
        try {
            authenticationServiceStub.registerLoadBalancer(username, password);
            operationServersInfos = authenticationServiceStub.getAvailableServersInfo();
        } catch (RemoteException e) {
            System.out.println("Error : " + e.getMessage());
        }
        operationServerStubs = loadOperationServerStubs();
        if(operationServerStubs.isEmpty())
        {
            System.out.println("Error: no server available to calculate.");
            System.exit(0);
        }
    }

    /**
     * Load the name service stub
     * **/
    private AuthenticationServiceInterface loadAuthenticationServiceStub(String hostname)
    {
        AuthenticationServiceInterface stub = null;

        try
        {
            int rmiPort = Integer.parseInt(ApplicationProperties.getPropertyValueFromKey("rmiPort"));
            Registry registry = LocateRegistry.getRegistry(hostname, rmiPort);
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

    /**
     * Load all operation servers stubs
     * **/
    private ArrayList<OperationServerInterface> loadOperationServerStubs() {

        ArrayList<OperationServerInterface> stubList = new ArrayList<>();
        try
        {
            int rmiPort = Integer.parseInt(ApplicationProperties.getPropertyValueFromKey("rmiPort"));
            for(OperationServerSharedInfo serverInfo : operationServersInfos)
            {
                Registry registry = LocateRegistry.getRegistry(serverInfo.getIpAddress(), rmiPort);
                OperationServerInterface stub = null;
                stub = (OperationServerInterface) registry.lookup(serverInfo.getIpAddress() + ":" + serverInfo.getPort());

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
            System.err.println("Error1: " + e.getMessage());
        }

        return stubList;
    }

    /**
     * Called when secure parameter is true in application.properties
     * **/
    private void runSecurely(String operationsFilename)
    {
        ArrayList<String> operationsList = readOperationsFile(operationsFilename);
        int nbAvailableServers = operationServerStubs.size();

        Thread[] threads = new Thread[nbAvailableServers];
        int[] activeServersCapacities = new int[nbAvailableServers];

        long start = System.nanoTime();
        while(!operationsList.isEmpty() || (isAnyThreadAlive(threads)))
        {
            for (int i = 0; i < threads.length; i++)
            {
                if (threads[i] == null || !threads[i].isAlive())
                {
                    activeServersCapacities[i] = operationServersInfos.get(i).getCapacity();
                }
            }

            Integer threadNumber = getServerWithBiggestCapacity(activeServersCapacities);
            activeServersCapacities = new int[nbAvailableServers];

            if(threadNumber == null || operationsList.isEmpty())
            {
                // No thread available
                continue;
            }
            int serverCapacity = operationServersInfos.get(threadNumber).getCapacity();

            ArrayList<String> task = operationsList;
            if(2*serverCapacity <= operationsList.size())
            {
                task = new ArrayList<String>(operationsList.subList(0, 2 * serverCapacity));
            }

            final ArrayList<String> threadTask = new ArrayList<>(task);

            // Remove operations from 0 to task size
            operationsList.subList(0, task.size()).clear();
            threads[threadNumber] = new Thread(() ->
            {
                try
                {
                    int result = operationServerStubs.get(threadNumber).calculateResult(LoadBalancer.username, this.password, threadTask);
                    totalResult.getAndAdd(result);
                }
                catch (RemoteException | TaskRejectedException e)
                {
                    operationsList.addAll(threadTask);
                    //System.err.println("Error: " + e.getMessage());
                }
                catch (FalseIdentityException e)
                {
                    System.err.println("Error: " + e.getMessage());
                    System.exit(0);
                }
            });
            threads[threadNumber].start();
        }

        // Wait for threads to terminate
        for (Thread thread : threads)
        {
            try
            {
                if(thread != null)
                {
                    thread.join();
                }
            }
            catch (InterruptedException e)
            {
                System.err.println("Error: " + e.getMessage());
            }
        }

        long end = System.nanoTime();
        System.out.println("Temps pour " + nbAvailableServers + " serveurs : " + (end - start) / 1E9 + " s");
        System.out.println("Resultat final : " + totalResult.get() + ".");
    }

    /**
     * Called when secure parameter is false in application.properties
     * **/
    private void runInsecurely(String operationsFilename)
    {
        //List<OperationTaskResult> opTasksResult = Collections.synchronizedList(new ArrayList<>());
        ArrayList<String> operationsList = readOperationsFile(operationsFilename);
        int nbAvailableServers = operationServerStubs.size();

        if(nbAvailableServers < 2)
        {
            System.err.println("Error: Non-secure mode requires at least two servers.");
            return;
        }

        Thread[] threads = new Thread[nbAvailableServers];
        int fixedTaskSize = 2 * getMinimumCapacity(this.operationServersInfos);

        long start = System.nanoTime();
        while(!operationsList.isEmpty() || (isAnyThreadAlive(threads)))
        {
            int threadA = -1;
            int threadB = -1;
            for (int i = 0; i < threads.length; i++)
            {
                if (threads[i] == null || !threads[i].isAlive())
                {
                    if(threadA == -1)
                    {
                        threadA = i;
                    }
                    else if(threadB == -1)
                    {
                        threadB = i;
                    }
                }
            }

            if(threadA == -1 || threadB == -1 || operationsList.isEmpty())
            {
                // Not enough threads available
                continue;
            }

            ArrayList<String> task = operationsList;
            if(fixedTaskSize <= operationsList.size())
            {
                // Create a subtask
                task = new ArrayList<>(operationsList.subList(0, fixedTaskSize));
            }

            final ArrayList<String> threadTask = new ArrayList<>(task);

            // Remove operations from 0 to task size
            operationsList.subList(0, task.size()).clear();

            final OperationServerInterface taskServerStubA = operationServerStubs.get(threadA);
            final OperationServerInterface taskServerStubB = operationServerStubs.get(threadB);
            threads[threadA] = new Thread(() ->
            {
                try
                {
                    int resultA = taskServerStubA.calculateResult(LoadBalancer.username, this.password, threadTask);
                    int resultB = taskServerStubB.calculateResult(LoadBalancer.username, this.password, threadTask);

                    if(resultA == resultB)
                    {
                        totalResult.getAndAdd(resultA);
                    }
                    else
                    {
                        operationsList.addAll(threadTask);
                    }
                }
                catch (RemoteException | TaskRejectedException e)
                {
                    operationsList.addAll(threadTask);
                    //System.err.println("Error: " + e.getMessage());
                }
                catch (FalseIdentityException e)
                {
                    System.err.println("Error: " + e.getMessage());
                    System.exit(0);
                }
            });
            threads[threadA].start();
        }

        // Wait for threads to terminate
        for (Thread thread : threads)
        {
            try
            {
                if(thread != null)
                {
                    thread.join();
                }
            }
            catch (InterruptedException e)
            {
                System.err.println("Error: " + e.getMessage());
            }
        }

        long end = System.nanoTime();
        System.out.println("Temps pour " + nbAvailableServers + " serveurs : " + (end - start) / 1E9 + " s");
        System.out.println("Resultat final : " + totalResult.get() + ".");
    }



    /**** UTILITY FUNCTIONS ****************************************************************/
    // Returns true if any of the threads passed in parameter is alive
    private boolean isAnyThreadAlive(Thread[] threads)
    {
        boolean aThreadIsAlive = false;
        for (Thread thread : threads)
        {
            if (thread != null && thread.isAlive())
            {
                aThreadIsAlive = true;
                break;
            }
        }
        return aThreadIsAlive;
    }

    // Returns the indice of the server with the highest capacity
    private Integer getServerWithBiggestCapacity(int[] serverCapacities)
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
        {
            return biggestCapacity;
        }
        else
        {
            return null;
        }
    }

    // Returns the minimum capacity in the server list
    private int getMinimumCapacity(ArrayList<OperationServerSharedInfo> serversInfoList)
    {
        int minCapacity = serversInfoList.get(0).getCapacity();
        for(int i = 1; i < serversInfoList.size(); i++)
        {
            if(serversInfoList.get(i).getCapacity() < minCapacity)
            {
                minCapacity = serversInfoList.get(i).getCapacity();
            }
        }
        return minCapacity;
    }

    // Returns a list of operations read in a file
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