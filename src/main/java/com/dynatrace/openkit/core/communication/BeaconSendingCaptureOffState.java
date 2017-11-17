package com.dynatrace.openkit.core.communication;

import com.dynatrace.openkit.protocol.StatusResponse;

import java.util.concurrent.TimeUnit;

/**
 * State where no data is captured. Periodically issues a status request to check if capturing shall be re-enabled.
 * The check interval is defined in {@link BeaconSendingCaptureOffState#INITIAL_RETRY_SLEEP_TIME_MILLISECONDS}.
 *
 * <p>
 *     Transition to:
 *     <ul>
 *         <li>{@link BeaconSendingCaptureOnState} if capturing is re-enabled</li>
 *         <li>{@link BeaconSendingFlushSessionsState} on shutdown</li>
 *     </ul>
 * </p>
 */
class BeaconSendingCaptureOffState extends AbstractBeaconSendingState {

    /**
     * number of retries for the status request
     */
    private static final int STATUS_REQUEST_RETRIES = 5;
    private static final long INITIAL_RETRY_SLEEP_TIME_MILLISECONDS = TimeUnit.SECONDS.toMillis(1);
    /**
     * maximum time to wait till next status check
     */
    private static final long STATUS_CHECK_INTERVAL = TimeUnit.HOURS.toMillis(2);

    BeaconSendingCaptureOffState() {
        super(false);
    }

    @Override
    void doExecute(BeaconSendingContext context) throws InterruptedException {

        long currentTime = context.getCurrentTimestamp();

        long delta = STATUS_CHECK_INTERVAL - (currentTime - context.getLastStatusCheckTime());
        if (delta > 0) {
            context.sleep(delta);
        }
        StatusResponse statusResponse = sendStatusRequest(context);
        handleStatusResponse(context, statusResponse);

        // update the last status check time in any case
        context.setLastStatusCheckTime(currentTime);
    }

    @Override
    AbstractBeaconSendingState getShutdownState() {
        return new BeaconSendingFlushSessionsState();
    }

    private static StatusResponse sendStatusRequest(BeaconSendingContext context) throws InterruptedException {

        StatusResponse statusResponse;
        int retry = 0;
        long sleepTimeInMillis = INITIAL_RETRY_SLEEP_TIME_MILLISECONDS;

        while (true) {
            statusResponse = context.getHTTPClient().sendStatusRequest();

            // if no (valid) status response was received -> sleep 1s [2s, 4s, 8s] and then retry (max 5 times altogether)
            if (!retryStatusRequest(context, statusResponse, retry)) {
                break;
            }

            context.sleep(sleepTimeInMillis);
            sleepTimeInMillis *= 2;
            retry++;
        }

        return statusResponse;
    }

    private static boolean retryStatusRequest(BeaconSendingContext context, StatusResponse statusResponse, int retry) {

        return !context.isShutdownRequested()
            && (statusResponse == null)
            && (retry < STATUS_REQUEST_RETRIES);
    }

    private static void handleStatusResponse(BeaconSendingContext context, StatusResponse statusResponse) {

        if (statusResponse == null) {
            return; // nothing to handle
        }

        context.handleStatusResponse(statusResponse);
        if (context.isCaptureOn()) {
            // capturing is re-enabled again
            context.setCurrentState(new BeaconSendingCaptureOnState());
        }
    }
}
