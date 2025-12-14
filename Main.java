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
    
    // AG tracking
    int phase;  // 0=FCFS, 1=Priority, 2=SJF
    int phaseTimeUsed;
    
    // Statistics
    int waitingTime;
    int turnaroundTime;
    
    // History tracking
    List<String> quantumHistory = new ArrayList<>();
    
    public Process(String name, int arrivalTime, int burstTime, int priority, int quantum) {
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.priority = priority;
        this.originalQuantum = quantum;
        this.currentQuantum = quantum;
        this.remainingTime = burstTime;
        this.quantumUsed = 0;
        this.phase = 0;
        this.phaseTimeUsed = 0;
        
        quantumHistory.add("Initial: " + currentQuantum);
    }
    
    public void addQuantumHistory(int time, String event) {
        quantumHistory.add("Time " + time + ": " + event);
    }
    
    public int getFCFSLimit() {
        return (int) Math.ceil(0.25 * currentQuantum);
    }
    
    public int getPriorityLimit() {
        return (int) Math.ceil(0.5 * currentQuantum);
    }
    
    public boolean isFinished() {
        return remainingTime <= 0;
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
    
    public RoundRobinScheduler(List<Process> processes, int timeQuantum, int contextSwitchTime) {
        this.processes = new ArrayList<>(processes);
        this.timeQuantum = timeQuantum;
        this.contextSwitchTime = contextSwitchTime;
    }
    
    @Override
    public void schedule() {
        // To be implemented
    }
    
    @Override
    public void printExecutionOrder() {
        // To be implemented
    }
    
    @Override
    public void printStatistics() {
        // To be implemented
    }
    
    @Override
    public Map<String, Integer> getWaitingTimes() {
        // To be implemented
        return new HashMap<>();
    }
    
    @Override
    public Map<String, Integer> getTurnaroundTimes() {
        // To be implemented
        return new HashMap<>();
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
        this.agingInterval = agingInterval;
    }
    
    @Override
    public void schedule() {
        // To be implemented
    }
    
    @Override
    public void printExecutionOrder() {
        // To be implemented
    }
    
    @Override
    public void printStatistics() {
        // To be implemented
    }
    
    @Override
    public Map<String, Integer> getWaitingTimes() {
        // To be implemented
        return new HashMap<>();
    }
    
    @Override
    public Map<String, Integer> getTurnaroundTimes() {
        // To be implemented
        return new HashMap<>();
    }
    
    private void applyAging() {
        // To be implemented
    }
}

// ==================== AG SCHEDULER ====================
class AGScheduler implements Scheduler {
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
    
    @Override
    public void schedule() {
        // To be implemented
    }
    
    @Override
    public void printExecutionOrder() {
        // To be implemented
    }
    
    @Override
    public void printStatistics() {
        // To be implemented
    }
    
    @Override
    public Map<String, Integer> getWaitingTimes() {
        // To be implemented
        return new HashMap<>();
    }
    
    @Override
    public Map<String, Integer> getTurnaroundTimes() {
        // To be implemented
        return new HashMap<>();
    }
    
    public void printQuantumHistory() {
        // AG-specific requirement
        // To be implemented
    }
    
    // AG-specific helper methods
    private void addArrivingProcesses() {
        // To be implemented
    }
    
    private void handleProcessExecution() {
        // To be implemented
    }
    
    private void applyQuantumUpdateRule(int scenario, Process process) {
        // To be implemented
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
        agScheduler.printQuantumHistory();  // AG-specific output
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
