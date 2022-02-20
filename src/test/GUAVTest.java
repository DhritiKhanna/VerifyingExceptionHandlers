package test;

import java.util.concurrent.TimeUnit;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Service;

public class GUAVTest {

	public void testcase1() throws Exception {
        Service service = new ScheduledServiceWithCustomBlockingScheduler(5000);
        new ServiceStateMonitor().monitorServiceStateWithServiceListener(service);
        System.out.println("starting service");
        service.startAsync();
        Thread.sleep(6000); // wait just enough time for 2nd call of getNextSchedule() to start
        System.out.println("Stopping service");
        service.stopAsync();
        System.out.println("Bye");
      }
}

class ScheduledServiceWithCustomBlockingScheduler extends AbstractScheduledService {
    private long blockTime;

    public ScheduledServiceWithCustomBlockingScheduler(final long blockTime) {
        this.blockTime = blockTime;
    }

    @Override
    protected void runOneIteration() throws Exception {
        System.out.println("runOneIteration()");
    }

    @Override
    protected Scheduler scheduler() {
        return new CustomScheduler() {
            @Override
            protected Schedule getNextSchedule() throws Exception {
                System.out.println("getNextSchedule()...");
                Thread.sleep(blockTime);
                if (state() == State.STOPPING) {
                    System.out.println("getNextSchedule() - not running anymore so don't return scheduler");
                    throw new Exception("not running anymore so don't return scheduler");
                }
                System.out.println("...getNextSchedule()");
                return new Schedule(0, TimeUnit.NANOSECONDS);
            }
        };
    }

    @Override
    protected void startUp() throws Exception {
        System.out.println("startUp()");
    }

    @Override
    protected void shutDown() throws Exception {
        System.out.println("shutDown()");
    }
}

class ServiceStateMonitor {
    public void monitorServiceStateWithServiceListener(final Service serviceToMonitor) {
        serviceToMonitor.addListener(new PrintOutNewStateServiceListener(), MoreExecutors.directExecutor());
    }

    private class PrintOutNewStateServiceListener extends Service.Listener {
        @Override
        public void starting() {
            System.out.println("SERVICE LISTENER : Starting");
        }

        @Override
        public void running() {
            System.out.println("SERVICE LISTENER : Running");
        }

        @Override
        public void stopping(final Service.State from) {
            System.out.println("SERVICE LISTENER : Stopping");
        }

        @Override
        public void terminated(final Service.State from) {
            System.out.println("SERVICE LISTENER : Terminated");
        }

        @Override
        public void failed(final Service.State from, final Throwable failure) {
            System.out.println("SERVICE LISTENER : Failed");
        }
    }
}

