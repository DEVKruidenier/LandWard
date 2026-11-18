package io.github.devkruidenier.landward.platform;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TaskRegistrationTest {
    @Test void cancellationBeforePlatformReturnsHandleStillCancelsExactlyOnce() {
        TaskRegistration task=new TaskRegistration(); AtomicInteger calls=new AtomicInteger();
        task.cancel(); task.bind(calls::incrementAndGet); task.cancel();
        assertTrue(task.cancelled()); assertEquals(1,calls.get());
    }
    @Test void concurrentBindingAndShutdownDeliverExactlyOneCancellation() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            for(int i=0;i<500;i++) {
                TaskRegistration task=new TaskRegistration(); AtomicInteger calls=new AtomicInteger();
                CountDownLatch start=new CountDownLatch(1);
                Future<?> bound=executor.submit(()->{await(start);task.bind(calls::incrementAndGet);});
                Future<?> stopped=executor.submit(()->{await(start);task.cancel();});
                start.countDown();bound.get(5,TimeUnit.SECONDS);stopped.get(5,TimeUnit.SECONDS);
                assertEquals(1,calls.get());
            }
        }
    }
    private static void await(CountDownLatch latch) {
        try{latch.await();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new RuntimeException(error);}
    }
}
