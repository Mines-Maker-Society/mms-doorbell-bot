package edu.mines.mmsbot.pi;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.plugin.gpiod.provider.gpio.digital.GpioDDigitalInputProvider;
import edu.mines.mmsbot.MMSContext;

import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DoorMonitor implements MMSContext {

    private boolean monitoring;
    private Thread monitorThread;
    private final AtomicBoolean lastStableState = new AtomicBoolean(false);
    private final boolean virtualMode;
    private final AtomicBoolean virtualSensorState = new AtomicBoolean(false);

    public DoorMonitor(boolean virtualMode) {
        this.virtualMode = virtualMode;
    }

    /**
     * Reports sensor state changes to SpaceStatus
     */
    private void reportSensorState(boolean sensorShowsLocked) {
        // Only report if state actually changed, otherwise overrides don't work
        if (lastStableState.get() != sensorShowsLocked || sensorShowsLocked != spaceStatus().isSpaceLocked()) {
            lastStableState.set(sensorShowsLocked);
            log().info("Door sensor detected state change: {}", sensorShowsLocked ? "LOCKED" : "OPEN");
            spaceStatus().handleSensorStateChange(sensorShowsLocked, System.currentTimeMillis(),-1);
        }
    }

    public void setupMonitor() {
        monitoring = true;

        if (virtualMode) {
            setupVirtualMonitor();
            return;
        }

        Context pi4j = Pi4J.newContextBuilder()
                .add(GpioDDigitalInputProvider.newInstance())
                .setGpioChipName("gpiochip0")
                .build();

        Properties properties = new Properties();
        properties.put("id", "door-sensor");
        properties.put("address", 4);
        properties.put("pull", "UP");
        properties.put("name", "DOOR-SENSOR");

        var config = DigitalInput.newConfigBuilder(pi4j)
                .load(properties)
                .build();

        var input = pi4j.din().create(config);

        // Initialize with current sensor state
        lastStableState.set(input.state().isHigh());

        AtomicBoolean lastReadState = new AtomicBoolean(lastStableState.get());
        AtomicInteger debounceCounter = new AtomicInteger(0);

        monitorThread = new Thread(() -> {
            try {
                log().info("Door monitor thread started. Initial sensor state: {}",
                        lastStableState.get() ? "HIGH (OPEN)" : "LOW (LOCKED)");

                while (monitoring) {
                    boolean currentRead = !input.state().isHigh();

                    if (currentRead == lastReadState.get()) {
                        // State hasn't changed, increment debounce counter
                        debounceCounter.incrementAndGet();
                    } else {
                        // State changed, reset debounce
                        debounceCounter.set(0);
                        lastReadState.set(currentRead);
                    }

                    // If state has been stable for debounce period, report it
                    if (debounceCounter.get() >= config().debounceSeconds) {
                        reportSensorState(currentRead);
                        debounceCounter.set(0); // Reset after reporting
                    }

                    Thread.sleep(1000);
                }
            } catch (InterruptedException ex) {
                monitoring = false;
                log().error("Door monitoring stopped due to interruption!", ex);
            } catch (Exception ex) {
                monitoring = false;
                log().error("Door monitoring stopped due to unexpected error!", ex);
            }
        });

        monitorThread.setName("DoorMonitor-Thread");
        monitorThread.setDaemon(false);
    }

    private void setupVirtualMonitor() {
        log().info("Starting in VIRTUAL SENSOR mode for testing");
        log().info("Commands: 'lock' or 'open' to change sensor state, 'status' to check, 'quit' to exit");

        lastStableState.set(false);

        monitorThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);

            while (monitoring) {
                try {
                    if (scanner.hasNextLine()) {
                        String command = scanner.nextLine().trim().toLowerCase();

                        switch (command) {
                            case "lock" -> {
                                virtualSensorState.set(true);
                                log().info("Virtual sensor set to: LOCKED");
                            }
                            case "open" -> {
                                virtualSensorState.set(false);
                                log().info("Virtual sensor set to: OPEN");
                            }
                            case "status" -> {
                                log().info("Current sensor state: {}", virtualSensorState.get() ? "LOCKED" : "OPEN");
                                log().info("Last stable state: {}", lastStableState.get() ? "LOCKED" : "OPEN");
                                continue;
                            }
                            case "exit", "quit" -> {
                                monitoring = false;
                                log().info("Exiting virtual sensor mode");
                                return;
                            }
                            default -> {
                                log().warn("Unknown command: {}. Use 'lock', 'open', 'status', or 'quit'", command);
                                continue;
                            }
                        }
                    }

                    reportSensorState(virtualSensorState.get());
                } catch (Exception ex) {
                    log().error("Error in virtual sensor mode!", ex);
                }
            }
            scanner.close();
        });

        monitorThread.setName("VirtualDoorMonitor-Thread");
        monitorThread.setDaemon(false);
    }

    public Thread getMonitorThread() {
        return monitorThread;
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    public void stopMonitoring() {
        log().info("Stopping door monitor...");
        monitoring = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    public boolean getLastStableState() {
        return lastStableState.get();
    }
}