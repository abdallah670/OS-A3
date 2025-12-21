import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;

public class OtherSchedulersJsonTest {
    private static int passedCount = 0;
    private static int totalCount = 0;
    
    @AfterAll
    static void printSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("OTHER SCHEDULERS TEST SUMMARY");
        System.out.println("=".repeat(60));
        System.out.println("Total Scheduler-Test Pairs: " + totalCount);
        System.out.println("Passed: " + passedCount);
        System.out.println("Failed: " + (totalCount - passedCount));
        System.out.println("=".repeat(60));
    }

    @TestFactory
    Stream<DynamicTest> testOtherSchedulers() {
        File dir = new File("test_cases\\Other_Schedulers");
        File[] testFiles = dir.listFiles((d, name) -> name.endsWith(".json"));

        if (testFiles == null) return Stream.empty();

        List<DynamicTest> dynamicTests = new ArrayList<>();
        java.util.Arrays.sort(testFiles);

        for (File file : testFiles) {
            String[] schedulerTypes = {"SJF", "RR", "Priority"};
            for (String type : schedulerTypes) {
                totalCount++;
                dynamicTests.add(DynamicTest.dynamicTest(file.getName() + " [" + type + "]", () -> {
                    try {
                        runTest(file, type);
                        passedCount++;
                    } catch (Throwable e) {
                        System.err.println("Failed test: " + file.getName() + " [" + type + "]");
                        e.printStackTrace();
                        throw e;
                    }
                }));
            }
        }
        return dynamicTests.stream();
    }

    private void runTest(File jsonFile, String schedulerType) throws Exception {
        String content = Files.readString(jsonFile.toPath());
        
        // Parse Inputs
        String inputSection = extractSection(content, "input");
        int contextSwitch = extractInt(inputSection, "contextSwitch");
        int rrQuantum = extractInt(inputSection, "rrQuantum");
        int agingInterval = extractInt(inputSection, "agingInterval");
        List<Process> processes = parseProcesses(inputSection);
        
        // Parse Expected for this specific scheduler
        String expectedOutputSection = extractSection(content, "expectedOutput");
        String schedulerExpected = extractSection(expectedOutputSection, schedulerType);
        
        if (schedulerExpected.isEmpty()) {
            fail("No expected output found for " + schedulerType + " in " + jsonFile.getName());
        }
        
        List<String> expectedOrder = parseExpectedOrder(schedulerExpected);
        
        // Run Scheduler
        Scheduler scheduler;
        if (schedulerType.equals("SJF")) {
            scheduler = new PreemptiveSJFScheduler(processes, contextSwitch);
        } else if (schedulerType.equals("RR")) {
            scheduler = new RoundRobinScheduler(processes, rrQuantum, contextSwitch);
        } else if (schedulerType.equals("Priority")) {
            scheduler = new PreemptivePriorityScheduler(processes, contextSwitch, agingInterval);
        } else {
            throw new IllegalArgumentException("Unknown scheduler type: " + schedulerType);
        }
        
        scheduler.schedule();
        
        // Verify Execution Order
        List<String> actualOrder = scheduler.getExecutionOrder();
        
        // Filter out "Idle" and "CS" if the expected order doesn't have them, 
        // OR keep them if they are expected.
        // Looking at the JSONs, SJF expected order in test_4.json is ["P1", "P4", "P6", "P2", "P5", "P3"]
        // The actual implementation of PreemptiveSJFScheduler seems to add "CS" and "Idle".
        // Let's filter them out for comparison if they are not in expected.
        List<String> filteredActual = new ArrayList<>();
        boolean expectedHasCS = expectedOrder.contains("CS");
        boolean expectedHasIdle = expectedOrder.contains("Idle");
        
        for (String s : actualOrder) {
            if (s.equals("CS") && !expectedHasCS) continue;
            if (s.equals("Idle") && !expectedHasIdle) continue;
            // Also handle duplicates if the expected order is just a list of transitions
            if (!filteredActual.isEmpty() && filteredActual.get(filteredActual.size()-1).equals(s)) {
                // If the scheduler logs every tick, we might need to compress it
                // SJF logs | P1 | P1 | ... so we compress it to transitions
                continue;
            }
            filteredActual.add(s);
        }

        assertEquals(expectedOrder, filteredActual, "Execution Order Mismatch for " + schedulerType + " in " + jsonFile.getName());
        
        // Verify Statistics
        verifyProcessResults(schedulerExpected, scheduler, jsonFile.getName() + " [" + schedulerType + "]");
    }

    private String extractSection(String src, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*");
        Matcher m = p.matcher(src);
        if (m.find()) {
            int start = m.end();
            int bracketCount = 0;
            char opening = src.charAt(start);
            if (opening != '{' && opening != '[') {
                 // Try looking for the next bracket
                 while (start < src.length() && src.charAt(start) != '{' && src.charAt(start) != '[') {
                     start++;
                 }
                 if (start >= src.length()) return "";
                 opening = src.charAt(start);
            }
            
            char closing = (opening == '{') ? '}' : ']';
            for (int i = start; i < src.length(); i++) {
                char c = src.charAt(i);
                if (c == opening) bracketCount++;
                else if (c == closing) bracketCount--;
                
                if (bracketCount == 0) {
                    return src.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private List<Process> parseProcesses(String inputSection) {
        List<Process> list = new ArrayList<>();
        String procArray = extractSection(inputSection, "processes");
        if (procArray.isEmpty()) return list;
        
        Pattern pObj = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
        Matcher mObj = pObj.matcher(procArray);
        while (mObj.find()) {
            String obj = mObj.group(1);
            String name = extractString(obj, "name");
            int arrival = extractInt(obj, "arrival");
            int burst = extractInt(obj, "burst");
            int priority = extractInt(obj, "priority");
            list.add(new Process(name, arrival, burst, priority, 20));
        }
        return list;
    }

    private List<String> parseExpectedOrder(String expectedSection) {
        List<String> list = new ArrayList<>();
        String orderArray = extractSection(expectedSection, "executionOrder");
        if (orderArray.isEmpty()) return list;
        
        String content = orderArray.substring(1, orderArray.length() - 1);
        String[] parts = content.split(",");
        for (String s : parts) {
            list.add(s.trim().replace("\"", ""));
        }
        return list;
    }

    private void verifyProcessResults(String schedulerExpected, Scheduler scheduler, String testName) {
        Pattern pObj = Pattern.compile("\\{([^{}]*\"waitingTime\"[^{}]*)\\}");
        Matcher mObj = pObj.matcher(schedulerExpected);
            
        Map<String, Integer> waitingTimes = scheduler.getWaitingTimes();
        Map<String, Integer> turnaroundTimes = scheduler.getTurnaroundTimes();
        
        int itemsChecked = 0;
        while (mObj.find()) {
            itemsChecked++;
            String obj = mObj.group(1);
            String name = extractString(obj, "name");
            int expectedWait = extractInt(obj, "waitingTime");
            int expectedTurnaround = extractInt(obj, "turnaroundTime");
            
            assertEquals(expectedWait, waitingTimes.get(name), 
                "Waiting Time Mismatch for process " + name + " in " + testName);
            
            assertEquals(expectedTurnaround, turnaroundTimes.get(name), 
                "Turnaround Time Mismatch for process " + name + " in " + testName);
        }
        
        if (itemsChecked == 0) {
             fail("No process results found in expected output for " + testName);
        }
    }

    private String extractString(String src, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"(.*?)\"").matcher(src);
        return m.find() ? m.group(1) : "";
    }
    
    private int extractInt(String src, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(src);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}

