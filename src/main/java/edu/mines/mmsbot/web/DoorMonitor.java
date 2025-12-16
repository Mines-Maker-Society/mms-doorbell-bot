package edu.mines.mmsbot.web;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.plugin.gpiod.provider.gpio.digital.GpioDDigitalInputProvider;
import edu.mines.mmsbot.MMSApp;
import edu.mines.mmsbot.MMSContext;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DoorMonitor implements MMSContext {

    private boolean monitoring;
    private Thread monitorThread;

    private void pingServer(boolean unlock) {
        if (spaceStatus().isLocked() && !unlock) return;
        if (!spaceStatus().isLocked() && unlock) return;

        log().info("Changing lock state to {}", !unlock);
        if (!unlock) spaceStatus().lock(-1,false);
        else spaceStatus().open(-1,true);
    }

    public void setupMonitor() {
        monitoring = true;

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

        AtomicBoolean lastState = new AtomicBoolean(false);
        AtomicInteger debounce = new AtomicInteger(0);

        monitorThread = new Thread(()->{
            try {
                while (monitoring) {
                    boolean curState = input.state().isHigh();

                    if (curState == lastState.get()) {
                        debounce.incrementAndGet();
                    } else {
                        debounce.set(0);
                    }
                    lastState.set(curState);

                    if (debounce.get() > MMSApp.getApp().getConfig().debounceSeconds) {
                        pingServer(lastState.get());
                    }

                Thread.sleep(1000);
                }
            } catch (InterruptedException ex) {
                monitoring = false;
                log().error("Door monitoring stopped for an unexpected reason!",ex);
            }
        });
    }


    public Thread getMonitorThread() {
        return monitorThread;
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    public void stopMonitoring() {
        monitoring = false;
    }
}
