package com.undercover.network;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * BashExecutor - Exécute les scripts Bash en multi-threading
 * Gère l'exécution asynchrone des scripts réseau
 */
public class BashExecutor {
    
    private final ExecutorService executor;
    private final String scriptsDir;
    private final ConcurrentHashMap<String, Process> runningProcesses;
    
    public BashExecutor() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "BashExecutor-Thread");
            t.setDaemon(true);
            return t;
        });
        this.scriptsDir = getScriptsDirectory();
        this.runningProcesses = new ConcurrentHashMap<>();
        makeScriptsExecutable();
    }
    
    private String getScriptsDirectory() {
        String basePath = System.getProperty("user.dir");
        Path scriptsPath = Paths.get(basePath, "scripts", "network");
        if (!Files.exists(scriptsPath)) {
            scriptsPath = Paths.get(basePath, "..", "scripts", "network");
        }
        return scriptsPath.toAbsolutePath().normalize().toString();
    }
    
    private void makeScriptsExecutable() {
        try {
            Files.walk(Paths.get(scriptsDir))
                .filter(p -> p.toString().endsWith(".sh"))
                .forEach(p -> {
                    try {
                        new ProcessBuilder("chmod", "+x", p.toString()).start();
                    } catch (IOException e) {
                        // Ignorer
                    }
                });
        } catch (IOException e) {
            // Ignorer
        }
    }
    
    /**
     * Exécute un script de manière synchrone et retourne le résultat
     */
    public String executeSync(String scriptName, String... args) throws IOException, InterruptedException {
        ProcessBuilder pb = createProcessBuilder(scriptName, args);
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        process.waitFor(30, TimeUnit.SECONDS);
        return output.toString().trim();
    }
    
    /**
     * Exécute un script de manière asynchrone avec callback
     */
    public CompletableFuture<String> executeAsync(String scriptName, String... args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSync(scriptName, args);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    /**
     * Lance un script en arrière-plan (processus continu)
     * Retourne un ID pour pouvoir l'arrêter plus tard
     */
    public String startBackground(String scriptName, Consumer<String> outputHandler, String... args) {
        String processId = scriptName + "_" + System.currentTimeMillis();
        
        executor.submit(() -> {
            try {
                ProcessBuilder pb = createProcessBuilder(scriptName, args);
                Process process = pb.start();
                runningProcesses.put(processId, process);
                
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && process.isAlive()) {
                        if (outputHandler != null) {
                            outputHandler.accept(line);
                        }
                    }
                }
            } catch (IOException e) {
                // Process terminé ou erreur
            } finally {
                runningProcesses.remove(processId);
            }
        });
        
        return processId;
    }
    
    /**
     * Arrête un processus en arrière-plan
     */
    public void stopBackground(String processId) {
        Process process = runningProcesses.remove(processId);
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
            }
        }
    }
    
    /**
     * Arrête tous les processus en arrière-plan
     */
    public void stopAll() {
        runningProcesses.values().forEach(p -> {
            if (p.isAlive()) {
                p.destroy();
            }
        });
        runningProcesses.clear();
    }
    
    private ProcessBuilder createProcessBuilder(String scriptName, String... args) {
        String scriptPath = Paths.get(scriptsDir, scriptName).toString();
        
        String[] command = new String[args.length + 2];
        command[0] = "/bin/bash";
        command[1] = scriptPath;
        System.arraycopy(args, 0, command, 2, args.length);
        
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(new File(scriptsDir));
        
        return pb;
    }
    
    /**
     * Récupère l'IP locale via le script Bash
     */
    public String getLocalIp() {
        try {
            return executeSync("get_local_ip.sh");
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
    
    public void shutdown() {
        stopAll();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
