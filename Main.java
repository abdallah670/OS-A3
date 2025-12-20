import java.util.*;

class Process {
    String name;
    int arrivalTime;
    int burstTime;
    int priority;
    int originalQuantum;
    
    // Dynamic state
    int remainingTime;
    int currentQuantum;
    int quantumUsed;
    int finishTime;
    int startTime = -1;
    
    // For phase tracking
    int currentPhase = 0;  // 0=FCFS, 1=Priority, 2=SJF
    
    // Statistics
    int waitingTime;
    int turnaroundTime;
    
    // History
    List<Integer> quantumHistory = new ArrayList<>();
    public int lastReadyTime;
    
    public Process(String name, int arrivalTime, int burstTime, int priority, int quantum) {
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priority = priority;
        this.originalQuantum = quantum;
        
        this.remainingTime = burstTime;
        this.currentQuantum = quantum;
        this.quantumUsed = 0;
        this.currentPhase = 0;
        
        quantumHistory.add(quantum);
    }
    
    public int getFCFSLimit() {
        return (int) Math.ceil(0.25 * currentQuantum);
    }
    
    public int getPriorityLimit() {
        // FIX: Use additive logic (25% + 25%) rather than ceil(50%)
        // This ensures consistent behavior for small quantums (e.g., Q=6 -> 2+2=4)
        return getFCFSLimit() + getFCFSLimit();
    }
    
    public boolean isFinished() {
        return remainingTime <= 0;
    }
    
    public void resetForNewRun() {
        quantumUsed = 0;
        currentPhase = 0;
    }
    
    public int getRemainingQuantum() {
        return currentQuantum - quantumUsed;
    }
    
    public void updatePhase() {
        if (quantumUsed >= getPriorityLimit()) {
            currentPhase = 2;  // SJF phase
        } else if (quantumUsed >= getFCFSLimit()) {
            currentPhase = 1;  // Priority phase
        } else {
            currentPhase = 0; // FCFS phase
        }
    }
    
    public int getTimeToNextPhase() {
        if (currentPhase == 0) {
            return getFCFSLimit() - quantumUsed;
        } else if (currentPhase == 1) {
            return getPriorityLimit() - quantumUsed;
        }
        return currentQuantum - quantumUsed;  // SJF phase, run to end of quantum
    }
}
// ==================== BASE SCHEDULER INTERFACE ====================
interface Scheduler {
    void schedule();
    void printExecutionOrder();
    void printStatistics();
    Map<String, Integer> getWaitingTimes();
    Map<String, Integer> getTurnaroundTimes();
}

// ==================== PREEMPTIVE SJF SCHEDULER ====================
class PreemptiveSJFScheduler implements Scheduler {
    private List<Process> processes;
    private List<String> executionOrder = new ArrayList<>();
    private int contextSwitchTime;
    private int currentTime = 0;
    
    public PreemptiveSJFScheduler(List<Process> processes, int contextSwitchTime) {
        this.processes = new ArrayList<>(processes);
        this.contextSwitchTime = contextSwitchTime;
    }
    
    @Override
    public void schedule() 
    {
        int completed = 0;
        Process currentProcess = null;

        while (completed < processes.size()) {

            // Get all available processes
            Process shortest = null;
            for (Process p : processes) {
                if (p.arrivalTime <= currentTime && p.remainingTime > 0) {
                    if (shortest == null || p.remainingTime < shortest.remainingTime||
                        (p.remainingTime == shortest.remainingTime && 
                        p.arrivalTime < shortest.arrivalTime)) 
                    {
                        shortest = p;
                    }
                }
            }

            // No process available → idle CPU
            if (shortest == null) {
                currentTime++;
                executionOrder.add("Idle");
                currentProcess = null;
                continue;
            }

            // Context switching
            if (currentProcess != shortest && currentProcess != null) {
                    currentTime += contextSwitchTime;
                    // Log context switch
                    for(int i = 0; i < contextSwitchTime; i++) {
                        executionOrder.add("CS");
                    }
            }
            currentProcess = shortest;

            // First time execution
            if (currentProcess.startTime == -1) {
                currentProcess.startTime = currentTime;
            }

            // Execute for 1 time unit
            // the process with the shortest remaining time
            executionOrder.add(currentProcess.name);
            currentProcess.remainingTime--;
            currentTime++;

            // If process finished
            if (currentProcess.remainingTime == 0) {
                currentProcess.finishTime = currentTime;
                completed++;
            }
        }

        // Calculate statistics
        for (Process p : processes) {
            p.turnaroundTime = p.finishTime - p.arrivalTime; 
            p.waitingTime = p.turnaroundTime - p.burstTime;
        }

        printExecutionOrder();
        printStatistics();
}

    
    @Override
    public void printExecutionOrder() {
        System.out.println("Execution Order:");
        for (String s : executionOrder) 
            System.out.print("| " + s + " ");
        System.out.println("|");
    }
    
    @Override
    public void printStatistics() {
        System.out.println("\nProcess\tWaiting Time\tTurnaround Time");
        for (Process p : processes) {
            System.out.println(p.name + "\t\t" +
                    p.waitingTime + "\t\t" +
                    p.turnaroundTime);
        }
    }
    
    @Override
    public Map<String, Integer> getWaitingTimes() {
        Map<String, Integer> map = new HashMap<>();
        for (Process p : processes) {
            map.put(p.name, p.waitingTime);
        }
        return map;
    }
    
    @Override
    public Map<String, Integer> getTurnaroundTimes() {
        Map<String, Integer> map = new HashMap<>();
        for (Process p : processes) {
            map.put(p.name, p.turnaroundTime);
        }
        return map;
    }
}

// ==================== ROUND ROBIN SCHEDULER ====================

class RoundRobinScheduler implements Scheduler {
    private List<Process> processes;
    private List<String> executionOrder = new ArrayList<>();
    private int timeQuantum;
    private int contextSwitchTime;
    private int currentTime = 0;
    private Map<String, Process> processMap = new HashMap<>();
    private Map<String, Integer> startTimeMap = new HashMap<>();
    private Map<String, Integer> lastExecutionTimeMap = new HashMap<>();
    private Map<String, Integer> waitingTimeMap = new HashMap<>();

    public RoundRobinScheduler(List<Process> processes, int timeQuantum, int contextSwitchTime) {
        this.processes = new ArrayList<>(processes);
        this.timeQuantum = timeQuantum;
        this.contextSwitchTime = contextSwitchTime;

        // Initialize maps
        for (Process p : processes) {
            processMap.put(p.name, p);
            lastExecutionTimeMap.put(p.name, p.arrivalTime);
            waitingTimeMap.put(p.name, 0);
        }
    }

    @Override
    public void schedule() {
        //we create deep copies of processes to work with
        List<Process> workingProcesses = new ArrayList<>();
        for (Process p : processes) {
            Process copy = new Process(p.name, p.arrivalTime, p.burstTime, p.priority, p.originalQuantum);
            copy.remainingTime = p.burstTime;
            copy.startTime = -1;
            workingProcesses.add(copy);
        }

        //then sort by arrival time
        workingProcesses.sort(Comparator.comparingInt(p -> p.arrivalTime));

        Queue<Process> readyQueue = new LinkedList<>();
        Process currentProcess = null;
        int nextProcessIndex = 0;
        int completed = 0;

        //then reset execution order
        executionOrder.clear();

        while (completed < workingProcesses.size()) {
            //we add newly arrived processes to ready queue
            while (nextProcessIndex < workingProcesses.size() &&
                    workingProcesses.get(nextProcessIndex).arrivalTime <= currentTime) {
                Process arrivedProcess = workingProcesses.get(nextProcessIndex);
                readyQueue.add(arrivedProcess);
                nextProcessIndex++;
            }

            //if no current process but ready queue has processes
            if (currentProcess == null && !readyQueue.isEmpty()) {
                currentProcess = readyQueue.poll();

                //first time execution
                if (currentProcess.startTime == -1) {
                    currentProcess.startTime = currentTime;
                }

                  //we calculate waiting time since last execution.
                int waitingPeriod = currentTime - lastExecutionTimeMap.get(currentProcess.name);
                waitingTimeMap.put(currentProcess.name,
                        waitingTimeMap.get(currentProcess.name) + waitingPeriod);

                //then add to execution order.
                executionOrder.add(currentProcess.name);
            }

            //if still no process, advance time to next arrival
            if (currentProcess == null && nextProcessIndex < workingProcesses.size()) {
                currentTime = workingProcesses.get(nextProcessIndex).arrivalTime;
                continue;
            }

            //if no process at all and that shouldn't happen , break.
            if (currentProcess == null) {
                break;
            }

            //execute current process
            int executionTime = Math.min(timeQuantum, currentProcess.remainingTime);

            //update the last execution time
            lastExecutionTimeMap.put(currentProcess.name, currentTime);

            //execute for executionTime
            currentTime += executionTime;
            currentProcess.remainingTime -= executionTime;

            //add newly arrived processes during execution
            while (nextProcessIndex < workingProcesses.size() &&
                    workingProcesses.get(nextProcessIndex).arrivalTime <= currentTime) {
                readyQueue.add(workingProcesses.get(nextProcessIndex));
                nextProcessIndex++;
            }

            //we check if process has finished.
            if (currentProcess.remainingTime == 0) {
                currentProcess.finishTime = currentTime;
                completed++;

                //then calculate turnaround time for original process.
                Process original = processMap.get(currentProcess.name);
                original.finishTime = currentProcess.finishTime;
                original.turnaroundTime = original.finishTime - original.arrivalTime;
                original.waitingTime = original.turnaroundTime - original.burstTime;

                //we add context switching if more processes remain.

                if (completed < workingProcesses.size()) {
                    currentTime += contextSwitchTime;
                    //we add context switch to execution order
                    for (int i = 0; i < contextSwitchTime; i++) {
                        executionOrder.add("CS");
                    }
                }

                currentProcess = null;
            } else {
                //if process didn't finish, add it to end of ready queue.
                readyQueue.add(currentProcess);
                currentProcess = null;

                //add context switching time
                if (!readyQueue.isEmpty() || nextProcessIndex < workingProcesses.size()) {
                    currentTime += contextSwitchTime;
                    //add context switch to execution order
                    for (int i = 0; i < contextSwitchTime; i++) {
                        executionOrder.add("CS");
                    }
                }
            }

            //add newly arrived processes during context switch.
            while (nextProcessIndex < workingProcesses.size() &&
                    workingProcesses.get(nextProcessIndex).arrivalTime <= currentTime) {
                readyQueue.add(workingProcesses.get(nextProcessIndex));
                nextProcessIndex++;
            }
        }
    }

    @Override
    public void printExecutionOrder() {
        System.out.println("Execution Order:");
        System.out.print("|");
        for (int i = 0; i < executionOrder.size(); i++) {
            System.out.print(" " + executionOrder.get(i) + " |");
            if ((i + 1) % 10 == 0 && i != executionOrder.size() - 1) {
                System.out.print("\n|");
            }
        }
        System.out.println();
    }

    @Override
    public void printStatistics() {
        System.out.println("\nProcess\tWaiting Time\tTurnaround Time");
        for (Process p : processes) {
            System.out.println(p.name + "\t\t" +
                    p.waitingTime + "\t\t" +
                    p.turnaroundTime);
        }

        //calculate averages
        double avgWaitingTime = processes.stream()
                .mapToInt(p -> p.waitingTime)
                .average()
                .orElse(0.0);

        double avgTurnaroundTime = processes.stream()
                .mapToInt(p -> p.turnaroundTime)
                .average()
                .orElse(0.0);

        System.out.printf("\nAverage Waiting Time: %.2f", avgWaitingTime);
        System.out.printf("\nAverage Turnaround Time: %.2f\n", avgTurnaroundTime);
    }

    @Override
    public Map<String, Integer> getWaitingTimes() {
        Map<String, Integer> map = new HashMap<>();
        for (Process p : processes) {
            map.put(p.name, p.waitingTime);
        }
        return map;
    }

    @Override
    public Map<String, Integer> getTurnaroundTimes() {
        Map<String, Integer> map = new HashMap<>();
        for (Process p : processes) {
            map.put(p.name, p.turnaroundTime);
        }
        return map;
    }

    //getter for execution order that is useful for testing.
    public List<String> getExecutionOrder() {
        return new ArrayList<>(executionOrder);
    }

    //getter for averages that also useful for testing
    public double getAverageWaitingTime() {
        return processes.stream()
                .mapToInt(p -> p.waitingTime)
                .average()
                .orElse(0.0);
    }

    public double getAverageTurnaroundTime() {
        return processes.stream()
                .mapToInt(p -> p.turnaroundTime)
                .average()
                .orElse(0.0);
    }
}


// ==================== PREEMPTIVE PRIORITY SCHEDULER ====================
class PreemptivePriorityScheduler implements Scheduler {
    private List<Process> processes;
    private List<String> executionOrder = new ArrayList<>();
    private int contextSwitchTime;
    private int currentTime = 0;
    private int agingInterval;  // To solve starvation
    
    public PreemptivePriorityScheduler(List<Process> processes, int contextSwitchTime, int agingInterval) {
        this.processes = new ArrayList<>(processes);
        this.contextSwitchTime = contextSwitchTime;
        this.agingInterval = agingInterval* 2;
    }
    
    @Override
    public void schedule() {
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        List<Process> readyQueue = new ArrayList<>();

        int currentTime = 0;
        int completed = 0;
        Process running = null;
        boolean needReselect = true;

        while (completed < processes.size()) {

            // Add arrivals
            for (Process p : processes) {
                if (p.arrivalTime == currentTime && p.remainingTime > 0 && !readyQueue.contains(p)) {
                    readyQueue.add(p);
                    p.lastReadyTime = currentTime;
                    needReselect = true;
                }
            }

            // Aging
            for (Process p : readyQueue) {
                if ((currentTime - p.lastReadyTime) > agingInterval) {
                    p.priority = Math.max(0, p.priority - 1);
                    p.lastReadyTime = currentTime;
                }
            }

            //  Select process only at boundaries
            if (needReselect) {
                Process highest = null;
                for (Process p : readyQueue) {
                    if (highest == null || p.priority < highest.priority) {
                        highest = p;
                    }
                }

                if (highest != null) {
                    running = highest;
                    readyQueue.remove(highest);
                    executionOrder.add(running.name);
                    if (running.startTime == -1) running.startTime = currentTime;
                    needReselect = false;
                }
            }

            // Execute
            if (running != null) {
                running.remainingTime--;
                currentTime++;

                if (running.remainingTime == 0) {
                    running.finishTime = currentTime;
                    running.turnaroundTime = running.finishTime - running.arrivalTime;
                    running.waitingTime = running.turnaroundTime - running.burstTime;
                    completed++;
                    running = null;
                    needReselect = true;

                    // Context switch
                    currentTime += contextSwitchTime;
                }
            } else {
                currentTime++;
                needReselect = true;
            }
        }
    }
    
    @Override
    public void printExecutionOrder() {
        // To be implemented
        System.out.println("Execution Order:");
        System.out.print("|");
        for (int i = 0; i < executionOrder.size(); i++) {
            System.out.print(" " + executionOrder.get(i) + " |");
            if ((i + 1) % 10 == 0 && i != executionOrder.size() - 1) {
                System.out.print("\n|");
            }
        }
        System.out.println();
    }
    
    @Override
    public void printStatistics() {
       
        System.out.println("\nProcess\tWaiting Time\tTurnaround Time");
        for (Process p : processes) {
            System.out.println(p.name + "\t\t" +
                    p.waitingTime + "\t\t" +
                    p.turnaroundTime);
        }

        //calculate averages
        double avgWaitingTime = processes.stream()
                .mapToInt(p -> p.waitingTime)
                .average()
                .orElse(0.0);

        double avgTurnaroundTime = processes.stream()
                .mapToInt(p -> p.turnaroundTime)
                .average()
                .orElse(0.0);

        System.out.printf("\nAverage Waiting Time: %.2f", avgWaitingTime);
        System.out.printf("\nAverage Turnaround Time: %.2f\n", avgTurnaroundTime);
    }
    
    @Override
    public Map<String, Integer> getWaitingTimes() {
        Map<String, Integer> map = new HashMap<>();
        for (Process p : processes) {
            map.put(p.name, p.waitingTime);
        }
        return map;
    }
    
    @Override
    public Map<String, Integer> getTurnaroundTimes() {
        Map<String, Integer> map = new HashMap<>();
        for (Process p : processes) {
            map.put(p.name, p.turnaroundTime);
        }
        return map;
    }
    
   
}

// ==================== AG SCHEDULER ====================
class AGScheduler {
    private List<Process> processes;
    private List<Process> readyQueue = new ArrayList<>();
    private List<String> executionOrder = new ArrayList<>();
    private int currentTime = 0;
    private Process currentProcess = null;
    private int contextSwitchTime;
    
    public AGScheduler(List<Process> processes, int contextSwitchTime) {
        this.processes = new ArrayList<>(processes);
        this.contextSwitchTime = contextSwitchTime;
    }
    
    public void schedule() {
        while (!allProcessesFinished()) {
            // Add arriving processes at the very start
            addArrivingProcesses();
            
            // If no process is running and queue is not empty, select next
            if (currentProcess == null && !readyQueue.isEmpty()) {
                currentProcess = readyQueue.remove(0);
                if (currentProcess.startTime == -1) {
                    currentProcess.startTime = currentTime;
                }
                currentProcess.resetForNewRun();
            }
            
            // If we have a current process, execute it
            if (currentProcess != null) {
                executeCurrentProcess();
            } else {
                // No process ready, advance time
                int nextArrival = getNextArrivalTime();
                if (nextArrival != -1) {
                    currentTime = nextArrival;
                } else {
                    currentTime++;
                }
            }
        }
        
        calculateStatistics();
    }
    
    private boolean allProcessesFinished() {
        for (Process p : processes) {
            if (!p.isFinished()) return false;
        }
        return true;
    }
    
    private void addArrivingProcesses() {
        for (Process p : processes) {
            if (p.arrivalTime <= currentTime && !p.isFinished() && 
                !readyQueue.contains(p) && p != currentProcess) {
                readyQueue.add(p);
            }
        }
    }
    
    private int getNextArrivalTime() {
        int next = -1;
        for (Process p : processes) {
            if (p.arrivalTime > currentTime && !p.isFinished()) {
                if (next == -1 || p.arrivalTime < next) {
                    next = p.arrivalTime;
                }
            }
        }
        return next;
    }
    
    private void executeCurrentProcess() {
        Process p = currentProcess;
        
        // 1. Determine maximum run duration based on phase
        int timeToNextPhase = p.getTimeToNextPhase();
        int timeToRun = Math.min(p.remainingTime, timeToNextPhase);
        
        // 2. Handling Arrivals during execution
        // FCFS Phase (0): Runs uninterrupted unless completed.
        // Priority Phase (1): Runs uninterrupted (Non-Preemptive) to solve Test 3.
        // SJF Phase (2): Can be interrupted by arrivals to check for preemption.
        if (p.currentPhase == 2) { 
             int nextArrival = getNextArrivalTime();
             if (nextArrival != -1 && nextArrival < currentTime + timeToRun) {
                 timeToRun = nextArrival - currentTime;
             }
        }
        
        // Safety: Ensure we advance at least 1 unit if logic above results in 0
        if (timeToRun <= 0) timeToRun = 1;
        
        // Record execution order
        if (executionOrder.isEmpty() || !executionOrder.get(executionOrder.size() - 1).equals(p.name)) {
            executionOrder.add(p.name);
        }
        
        // Execute
        p.remainingTime -= timeToRun;
        p.quantumUsed += timeToRun;
        currentTime += timeToRun;
        
        // Update phase status after running
        int oldPhase = p.currentPhase;
        p.updatePhase();
        
        // CRITICAL FIX: Check for arrivals NOW, before deciding to re-queue current process.
        // This ensures strictly correct FCFS ordering in the Ready Queue.
        addArrivingProcesses();

        // 3. Check for Completion or Quantum Expiry
        if (p.isFinished()) {
            p.finishTime = currentTime;
            handleScenario(4, p);
            currentProcess = null;
            // Add context switch overhead if applicable
            if(contextSwitchTime > 0) currentTime += contextSwitchTime; 
            return;
        } 
        
        if (p.quantumUsed >= p.currentQuantum) {
            // Scenario 1: Used all quantum
            handleScenario(1, p);
            readyQueue.add(p); // Move to back of queue
            currentProcess = null;
            if(contextSwitchTime > 0) currentTime += contextSwitchTime;
            return;
        } 
        
        // 4. Check for Preemption Logic
        boolean preempted = false;
        
        if (oldPhase != p.currentPhase) {
            // We just crossed a phase boundary (e.g., FCFS -> Priority)
            preempted = checkPreemptionAtPhaseChange(p);
        } else {
            // We are mid-phase (Priority or SJF), check if a better process arrived
            preempted = checkPreemption(p);
        }
        
        if (preempted) {
            // Preemption logic handled inside helper functions (adding to queue, switching process)
            // Just need to apply context switch cost if needed
            if(contextSwitchTime > 0) currentTime += contextSwitchTime;
        }
    }
    
    private boolean checkPreemptionAtPhaseChange(Process p) {
        // Entering Priority Phase: Check if there's a higher priority process waiting
        if (p.currentPhase == 1) { 
            Process higherPriority = getHigherPriorityInQueue(p);
            if (higherPriority != null) {
                handleScenario(2, p);
                readyQueue.add(p);
                readyQueue.remove(higherPriority);
                currentProcess = higherPriority;
                if (currentProcess.startTime == -1) currentProcess.startTime = currentTime;
                currentProcess.resetForNewRun();
                return true;
            }
        } 
        // Entering SJF Phase: Check if there's a shorter job waiting
        else if (p.currentPhase == 2) { 
            Process shorterJob = getShorterJobInQueue(p);
            if (shorterJob != null) {
                handleScenario(3, p);
                readyQueue.add(p);
                readyQueue.remove(shorterJob);
                currentProcess = shorterJob;
                if (currentProcess.startTime == -1) currentProcess.startTime = currentTime;
                currentProcess.resetForNewRun();
                return true;
            }
        }
        return false;
    }
    
    private boolean checkPreemption(Process p) {
        // Priority Phase Preemption (by new arrival)
        if (p.currentPhase == 1) { 
            Process higherPriority = getHigherPriorityInQueue(p);
            if (higherPriority != null) {
                handleScenario(2, p);
                readyQueue.add(p);
                readyQueue.remove(higherPriority);
                currentProcess = higherPriority;
                if (currentProcess.startTime == -1) currentProcess.startTime = currentTime;
                currentProcess.resetForNewRun();
                return true;
            }
        } 
        // SJF Phase Preemption (by new arrival)
        else if (p.currentPhase == 2) { 
            Process shorterJob = getShorterJobInQueue(p);
            if (shorterJob != null) {
                handleScenario(3, p);
                readyQueue.add(p);
                readyQueue.remove(shorterJob);
                currentProcess = shorterJob;
                if (currentProcess.startTime == -1) currentProcess.startTime = currentTime;
                currentProcess.resetForNewRun();
                return true;
            }
        }
        return false;
    }
    
    // Helpers (Unchanged logic, just ensuring they exist)
    private Process getHigherPriorityInQueue(Process current) {
        Process best = null;
        for (Process p : readyQueue) {
            if (p.priority < current.priority) {
                if (best == null || p.priority < best.priority) {
                    best = p;
                }
            }
        }
        return best;
    }
    
    private Process getShorterJobInQueue(Process current) {
        Process best = null;
        for (Process p : readyQueue) {
            if (p.remainingTime < current.remainingTime) {
                if (best == null || p.remainingTime < best.remainingTime) {
                    best = p;
                }
            }
        }
        return best;
    }
    
    private void handleScenario(int scenario, Process p) {
        int oldQuantum = p.currentQuantum;
        
        switch(scenario) {
            case 1: // Used all quantum
                p.currentQuantum += 2;
                break;
                
            case 2: // Preempted in Priority phase
                int remaining = p.getRemainingQuantum();
                int increase = (int) Math.ceil(remaining / 2.0);
                p.currentQuantum += increase;
                break;
                
            case 3: // Preempted in SJF phase
                remaining = p.getRemainingQuantum();
                p.currentQuantum += remaining;
                break;
                
            case 4: // Finished early
                p.currentQuantum = 0;
                break;
        }
        
        if (oldQuantum != p.currentQuantum) {
            p.quantumHistory.add(p.currentQuantum);
        }
    }
    
    private void calculateStatistics() {
        for (Process p : processes) {
            p.turnaroundTime = p.finishTime - p.arrivalTime;
            p.waitingTime = p.turnaroundTime - p.burstTime;
        }
    }
    
    // Getters for stats/output
    public List<String> getExecutionOrder() { return executionOrder; }
    public List<Integer> getQuantumHistory(String name) {
        for(Process p : processes) if(p.name.equals(name)) return p.quantumHistory;
        return new ArrayList<>();
    }
    public Map<String, Integer> getWaitingTimes() {
        Map<String, Integer> map = new HashMap<>();
        for(Process p : processes) map.put(p.name, p.waitingTime);
        return map;
    }
    public Map<String, Integer> getTurnaroundTimes() {
        Map<String, Integer> map = new HashMap<>();
        for(Process p : processes) map.put(p.name, p.turnaroundTime);
        return map;
    }
    public double getAverageWaitingTime() {
        return processes.stream().mapToInt(p -> p.waitingTime).average().orElse(0.0);
    }
    public double getAverageTurnaroundTime() {
        return processes.stream().mapToInt(p -> p.turnaroundTime).average().orElse(0.0);
    }
}

// ==================== MAIN SIMULATOR CLASS ====================
class CPUSchedulerSimulator {
    private List<Process> processes = new ArrayList<>();
    private int rrTimeQuantum;
    private int contextSwitchTime;
    private int priorityAgingInterval = 5;  // Default
    
    // Scheduler instances
    private PreemptiveSJFScheduler sjfScheduler;
    private RoundRobinScheduler rrScheduler;
    private PreemptivePriorityScheduler priorityScheduler;
    private AGScheduler agScheduler;
    
    // Input methods
    public void readInput() {
        // To be implemented
    }
    
    public void addProcess(String name, int arrivalTime, int burstTime, int priority, int quantum) {
        processes.add(new Process(name, arrivalTime, burstTime, priority, quantum));
    }
    
    // Run all schedulers
    public void runAllSchedulers() {
        // Create scheduler instances
        sjfScheduler = new PreemptiveSJFScheduler(processes, contextSwitchTime);
        rrScheduler = new RoundRobinScheduler(processes, rrTimeQuantum, contextSwitchTime);
        priorityScheduler = new PreemptivePriorityScheduler(processes, contextSwitchTime, priorityAgingInterval);
        agScheduler = new AGScheduler(processes, contextSwitchTime);
        
        // Run schedulers
        System.out.println("\n=== Preemptive SJF Scheduling ===");
        sjfScheduler.schedule();
        
        System.out.println("\n=== Round Robin Scheduling ===");
        rrScheduler.schedule();
        
        System.out.println("\n=== Preemptive Priority Scheduling ===");
        priorityScheduler.schedule();
        
        System.out.println("\n=== AG Scheduling ===");
        agScheduler.schedule();
       
    }
    
    // Unit test helper
    public void runTest(String testName, List<Process> testProcesses) {
        // To be implemented
    }
}

// ==================== MAIN CLASS ====================
public class Main {
    public static void main(String[] args) {
        CPUSchedulerSimulator simulator = new CPUSchedulerSimulator();
        simulator.readInput();
        simulator.runAllSchedulers();
    }
}
