package com.docsync.exec;

/**
 * Helper main class spawned by {@link DefaultProcessRunnerTest} to exercise
 * real subprocess behaviour: echoing, spamming output past the 64 KB cap, or
 * sleeping past the timeout.
 */
public final class TestProcessMain {

    private TestProcessMain() {
    }

    /**
     * Runs the scripted behaviour selected by the first argument.
     *
     * @param args {@code ["echo"]}, {@code ["spam"]} or {@code ["sleep"]}
     * @throws InterruptedException when the sleep is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        switch (args.length > 0 ? args[0] : "") {
            case "echo" -> System.out.println("hello from child");
            case "spam" -> {
                System.out.print("x".repeat(100_000));
                System.out.print("TAIL-MARKER");
            }
            case "sleep" -> Thread.sleep(30_000);
            default -> System.exit(3);
        }
    }
}
