import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
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


public class AGSchedulerJsonTest {
private static int passedCount = 0;
    private static int totalCount = 0;
    
    @AfterAll
    static void printSummary() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("TEST SUMMARY");
        System.out.println("=".repeat(60));
        System.out.println("Total Tests: " + totalCount);
        System.out.println("Passed: " + passedCount);
        System.out.println("Failed: " + (totalCount - passedCount));
        System.out.println("=".repeat(60));
    }
    @TestFactory
    Stream<DynamicTest> testFromFiles() {
        File dir = new File("test_cases_v5\\AG");
        File[] testFiles = dir.listFiles((d, name) -> name.matches("AG_test\\d+\\.json"));

        if (testFiles == null) return Stream.empty();
         totalCount = testFiles.length;

        return java.util.Arrays.stream(testFiles)
            .sorted() // Ensure order
            .map(file -> DynamicTest.dynamicTest(file.getName(), () -> {
                try {
                    runTest(file);
                    passedCount++;  // Count passed tests
                } catch (AssertionError e) {
                    // Test failed - don't increment passedCount
                    throw e;
                }
            }));
    }

    private void runTest(File jsonFile) throws Exception {
        String content = Files.readString(jsonFile.toPath());
        
        // Parse Inputs
        List<Process> processes = parseProcesses(content);
        List<String> expectedOrder = parseExpectedOrder(content);
        
        // Run Scheduler
        AGScheduler scheduler = new AGScheduler(processes, 0);
        scheduler.schedule();
        
        // Verify Execution Order
        List<String> actualOrder = scheduler.getExecutionOrder();
        assertEquals(expectedOrder, actualOrder, "Execution Order Mismatch for " + jsonFile.getName());
        
        // Verify Statistics
        verifyProcessResults(content, scheduler, jsonFile.getName());
    }
    
    // --- Parsers & Verifiers (Adapted from JsonTestRunner) ---
    
    private List<Process> parseProcesses(String json) {
        List<Process> list = new ArrayList<>();
        Pattern pArray = Pattern.compile("\"processes\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher mArray = pArray.matcher(json);
        if (mArray.find()) {
            String arrayContent = mArray.group(1);
            Pattern pObj = Pattern.compile("\\{(.*?)\\}");
            Matcher mObj = pObj.matcher(arrayContent);
            while (mObj.find()) {
                String obj = mObj.group(1);
                String name = extractString(obj, "name");
                int arrival = extractInt(obj, "arrival");
                int burst = extractInt(obj, "burst");
                int priority = extractInt(obj, "priority");
                int quantum = extractInt(obj, "quantum");
                list.add(new Process(name, arrival, burst, priority, quantum));
            }
        }
        return list;
    }
    
    private List<String> parseExpectedOrder(String json) {
        List<String> list = new ArrayList<>();
        Pattern p = Pattern.compile("\"executionOrder\"\\s*:\\s*\\[(.*?)\\]");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String[] parts = m.group(1).split(",");
            for (String s : parts) {
                list.add(s.trim().replace("\"", ""));
            }
        }
        return list;
    }
    
    private void verifyProcessResults(String json, AGScheduler scheduler, String testName) {
        // Match objects containing "waitingTime" directly to avoid issues with nested arrays (like quantumHistory)
        // [^{}]* ensures we match strictly within a single JSON object (assuming no nested objects inside results)
        Pattern pObj = Pattern.compile("\\{([^{}]*\"waitingTime\"[^{}]*)\\}");
        Matcher mObj = pObj.matcher(json);
            
        Map<String, Integer> waitingTimes = scheduler.getWaitingTimes();
        Map<String, Integer> turnaroundTimes = scheduler.getTurnaroundTimes();
        
        int itemsChecked = 0;
        while (mObj.find()) {
            itemsChecked++;
            String obj = mObj.group(1); // Content inside {}
            String name = extractString(obj, "name");
            int expectedWait = extractInt(obj, "waitingTime");
            int expectedTurnaround = extractInt(obj, "turnaroundTime");
            
            // Assertion will determine if the values are correct
            assertEquals(expectedWait, waitingTimes.get(name), 
                "Waiting Time Mismatch for process " + name + " in " + testName);
            
            assertEquals(expectedTurnaround, turnaroundTimes.get(name), 
                "Turnaround Time Mismatch for process " + name + " in " + testName);
        }
        
        if (itemsChecked == 0) {
             fail("No process results found in JSON for " + testName);
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
