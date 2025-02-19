/**
 * Copyright 2018-2019 Dynatrace LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dynatrace.openkit.core.communication;

import com.dynatrace.openkit.api.Logger;
import com.dynatrace.openkit.core.configuration.BeaconConfiguration;
import com.dynatrace.openkit.core.objects.SessionImpl;
import com.dynatrace.openkit.protocol.HTTPClient;
import com.dynatrace.openkit.protocol.Response;
import com.dynatrace.openkit.protocol.StatusResponse;
import com.dynatrace.openkit.providers.HTTPClientProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


public class BeaconSendingCaptureOnStateTest {

    private BeaconSendingContext mockContext;
    private SessionWrapper mockSession1Open;
    private SessionWrapper mockSession2Open;
    private SessionWrapper mockSession3Finished;
    private SessionWrapper mockSession4Finished;
    private SessionWrapper mockSession5New;
    private SessionWrapper mockSession6New;

    @Before
    public void setUp() {
        mockSession1Open = mock(SessionWrapper.class);
        mockSession2Open = mock(SessionWrapper.class);
        mockSession3Finished = mock(SessionWrapper.class);
        mockSession4Finished = mock(SessionWrapper.class);
        mockSession5New = mock(SessionWrapper.class);
        mockSession6New = mock(SessionWrapper.class);
        when(mockSession1Open.sendBeacon(any(HTTPClientProvider.class))).thenReturn(new StatusResponse(mock(Logger.class), "", 200, Collections.<String, List<String>>emptyMap()));
        when(mockSession2Open.sendBeacon(any(HTTPClientProvider.class))).thenReturn(new StatusResponse(mock(Logger.class), "", 404, Collections.<String, List<String>>emptyMap()));
        when(mockSession1Open.isDataSendingAllowed()).thenReturn(true);
        when(mockSession1Open.getSession()).thenReturn(mock(SessionImpl.class));
        when(mockSession2Open.getSession()).thenReturn(mock(SessionImpl.class));
        when(mockSession3Finished.getSession()).thenReturn(mock(SessionImpl.class));
        when(mockSession4Finished.getSession()).thenReturn(mock(SessionImpl.class));
        when(mockSession5New.getSession()).thenReturn(mock(SessionImpl.class));
        when(mockSession6New.getSession()).thenReturn(mock(SessionImpl.class));

        HTTPClientProvider mockHTTPClientProvider = mock(HTTPClientProvider.class);

        mockContext = mock(BeaconSendingContext.class);
        when(mockContext.getCurrentTimestamp()).thenReturn(42L);
        when(mockContext.getAllNewSessions()).thenReturn(Collections.<SessionWrapper>emptyList());
        when(mockContext.getAllOpenAndConfiguredSessions()).thenReturn(Arrays.asList(mockSession1Open, mockSession2Open));
        when(mockContext.getAllFinishedAndConfiguredSessions()).thenReturn(Arrays.asList(mockSession3Finished, mockSession4Finished));
        when(mockContext.getHTTPClientProvider()).thenReturn(mockHTTPClientProvider);
    }

    @Test
    public void aBeaconSendingCaptureOnStateIsNotATerminalState() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        // verify that BeaconSendingCaptureOnState is not a terminal state
        assertThat(target.isTerminalState(), is(false));
    }

    @Test
    public void aBeaconSendingCaptureOnStateHasTerminalStateBeaconSendingFlushSessions() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        AbstractBeaconSendingState terminalState = target.getShutdownState();
        //verify that terminal state is BeaconSendingFlushSessions
        assertThat(terminalState, is(instanceOf(BeaconSendingFlushSessionsState.class)));
    }

    @Test
    public void toStringReturnsStateName() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        // then
        assertThat(target.toString(), is(equalTo("CaptureOn")));
    }

    @Test
    public void newSessionRequestsAreMadeForAllNewSessions() {
        // given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        BeaconConfiguration defaultConfiguration = new BeaconConfiguration(1);

        HTTPClient mockClient = mock(HTTPClient.class);
        when(mockContext.getHTTPClient()).thenReturn(mockClient);
        when(mockContext.getAllNewSessions()).thenReturn(Arrays.asList(mockSession5New, mockSession6New));
        when(mockClient.sendNewSessionRequest())
            .thenReturn(new StatusResponse(mock(Logger.class), "mp=5", 200, Collections.<String, List<String>>emptyMap())) // first response valid
            .thenReturn(new StatusResponse(mock(Logger.class), "", Response.HTTP_BAD_REQUEST, Collections.<String, List<String>>emptyMap())); // second response invalid
        when(mockSession5New.canSendNewSessionRequest()).thenReturn(true);
        when(mockSession5New.getBeaconConfiguration()).thenReturn(defaultConfiguration);
        when(mockSession6New.canSendNewSessionRequest()).thenReturn(true);
        when(mockSession6New.getBeaconConfiguration()).thenReturn(defaultConfiguration);

        ArgumentCaptor<BeaconConfiguration> beaconConfigurationArgumentCaptor = ArgumentCaptor.forClass(BeaconConfiguration.class);

        // when
        target.execute(mockContext);

        // verify for both new sessions a new session request has been made
        verify(mockClient, times(2)).sendNewSessionRequest();

        // verify first has been updated, second decreased
        verify(mockSession5New, times(1)).updateBeaconConfiguration(beaconConfigurationArgumentCaptor.capture());
        assertThat(beaconConfigurationArgumentCaptor.getAllValues().get(0).getMultiplicity(), is(equalTo(5)));

        // verify for beacon 6 only the number of tries was decreased
        verify(mockSession6New, times(1)).decreaseNumNewSessionRequests();
    }

    @Test
    public void multiplicityIsSetToZeroIfNoFurtherNewSessionRequestsAreAllowed() {
        // given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        BeaconConfiguration defaultConfiguration = new BeaconConfiguration(1);

        HTTPClient mockClient = mock(HTTPClient.class);
        when(mockContext.getHTTPClient()).thenReturn(mockClient);
        when(mockContext.getAllNewSessions()).thenReturn(Arrays.asList(mockSession5New, mockSession6New));
        when(mockClient.sendNewSessionRequest())
            .thenReturn(new StatusResponse(mock(Logger.class), "mp=5", 200, Collections.<String, List<String>>emptyMap())) // first response valid
            .thenReturn(new StatusResponse(mock(Logger.class), "", Response.HTTP_BAD_REQUEST, Collections.<String, List<String>>emptyMap())); // second response invalid
        when(mockSession5New.canSendNewSessionRequest()).thenReturn(false);
        when(mockSession5New.getBeaconConfiguration()).thenReturn(defaultConfiguration);
        when(mockSession6New.canSendNewSessionRequest()).thenReturn(false);
        when(mockSession6New.getBeaconConfiguration()).thenReturn(defaultConfiguration);

        ArgumentCaptor<BeaconConfiguration> beaconConfigurationArgumentCaptor = ArgumentCaptor.forClass(BeaconConfiguration.class);

        // when
        target.execute(mockContext);

        // verify for no session a new session request has been made
        verify(mockClient, times(0)).sendNewSessionRequest();

        // verify bot sessions are updated
        verify(mockSession5New, times(1)).updateBeaconConfiguration(beaconConfigurationArgumentCaptor.capture());
        assertThat(beaconConfigurationArgumentCaptor.getAllValues().get(0).getMultiplicity(), is(equalTo(0)));

        // verify for beacon 6 only the number of tries was decreased
        verify(mockSession6New, times(1)).updateBeaconConfiguration(beaconConfigurationArgumentCaptor.capture());
        assertThat(beaconConfigurationArgumentCaptor.getAllValues().get(1).getMultiplicity(), is(equalTo(0)));
    }

    @Test
    public void newSessionRequestsAreAbortedWhenTooManyRequestsResponseIsReceived() {
        // given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        BeaconConfiguration defaultConfiguration = new BeaconConfiguration(1);

        StatusResponse statusResponse = mock(StatusResponse.class);
        when(statusResponse.getResponseCode()).thenReturn(Response.HTTP_TOO_MANY_REQUESTS);
        when(statusResponse.isErroneousResponse()).thenReturn(true);
        when(statusResponse.getRetryAfterInMilliseconds()).thenReturn(6543L);

        HTTPClient mockClient = mock(HTTPClient.class);
        when(mockContext.getHTTPClient()).thenReturn(mockClient);
        when(mockContext.getAllNewSessions()).thenReturn(Arrays.asList(mockSession5New, mockSession6New));
        when(mockClient.sendNewSessionRequest())
            .thenReturn(statusResponse); // second response invalid
        when(mockSession5New.canSendNewSessionRequest()).thenReturn(true);
        when(mockSession5New.getBeaconConfiguration()).thenReturn(defaultConfiguration);
        when(mockSession6New.canSendNewSessionRequest()).thenReturn(true);
        when(mockSession6New.getBeaconConfiguration()).thenReturn(defaultConfiguration);

        // when
        target.execute(mockContext);

        // verify for first new sessions a new session request has been made
        verify(mockClient, times(1)).sendNewSessionRequest();

        // verify no changes on first
        verify(mockSession5New, times(1)).canSendNewSessionRequest();
        verifyNoMoreInteractions(mockSession5New);

        // verify second new session was not used at all
        verifyZeroInteractions(mockSession6New);

        // verify any other session was not invoked
        verifyZeroInteractions(mockSession1Open, mockSession2Open, mockSession3Finished, mockSession4Finished);

        // ensure also transition to CaptureOffState
        ArgumentCaptor<BeaconSendingCaptureOffState> argumentCaptor = ArgumentCaptor.forClass(BeaconSendingCaptureOffState.class);
        verify(mockContext, times(1)).setNextState(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().size(), is(equalTo(1)));
        assertThat(argumentCaptor.getAllValues().get(0).sleepTimeInMilliseconds, is(equalTo(6543L)));
    }

    @Test
    public void aBeaconSendingCaptureOnStateSendsFinishedSessions() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        StatusResponse statusResponse = mock(StatusResponse.class);
        when(statusResponse.getResponseCode()).thenReturn(Response.HTTP_OK);
        when(statusResponse.isErroneousResponse()).thenReturn(false);

        when(mockSession3Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession4Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession3Finished.isDataSendingAllowed()).thenReturn(true);
        when(mockSession4Finished.isDataSendingAllowed()).thenReturn(true);

        //when calling execute
        target.execute(mockContext);

        verify(mockSession3Finished, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession4Finished, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));

        // also verify that the session are removed
        verify(mockContext, times(1)).removeSession(mockSession3Finished);
        verify(mockContext, times(1)).removeSession(mockSession4Finished);
    }

    @Test
    public void aBeaconSendingCaptureOnStateClearsFinishedSessionsIfSendingIsNotAllowed() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        when(mockSession3Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(mock(StatusResponse.class));
        when(mockSession4Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(mock(StatusResponse.class));
        when(mockSession3Finished.isDataSendingAllowed()).thenReturn(false);
        when(mockSession4Finished.isDataSendingAllowed()).thenReturn(false);

        //when calling execute
        target.execute(mockContext);

        verify(mockSession3Finished, times(0)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession4Finished, times(0)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));

        // also verify that the session are removed
        verify(mockContext, times(1)).removeSession(mockSession3Finished);
        verify(mockContext, times(1)).removeSession(mockSession4Finished);
    }

    @Test
    public void aBeaconSendingCaptureOnStateDoesNotRemoveFinishedSessionIfSendWasUnsuccessful() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        StatusResponse statusResponse = mock(StatusResponse.class);
        when(statusResponse.getResponseCode()).thenReturn(Response.HTTP_BAD_REQUEST);
        when(statusResponse.isErroneousResponse()).thenReturn(true);

        when(mockSession3Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession4Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(mock(StatusResponse.class));
        when(mockSession3Finished.isEmpty()).thenReturn(false);
        when(mockSession3Finished.isDataSendingAllowed()).thenReturn(true);
        when(mockSession4Finished.isDataSendingAllowed()).thenReturn(true);

        //when calling execute
        target.execute(mockContext);

        verify(mockSession3Finished, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession4Finished, times(0)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));

        verify(mockContext, times(1)).getAllFinishedAndConfiguredSessions();
        verify(mockContext, times(0)).removeSession(any(SessionWrapper.class));
    }

    @Test
    public void aBeaconSendingCaptureOnStateContinuesWithNextFinishedSessionIfSendingWasUnsuccessfulButBeaconIsEmtpy() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        StatusResponse erroneousStatusResponse = mock(StatusResponse.class);
        when(erroneousStatusResponse.getResponseCode()).thenReturn(Response.HTTP_BAD_REQUEST);
        when(erroneousStatusResponse.isErroneousResponse()).thenReturn(true);

        StatusResponse statusResponse = mock(StatusResponse.class);
        when(statusResponse.getResponseCode()).thenReturn(Response.HTTP_OK);
        when(statusResponse.isErroneousResponse()).thenReturn(false);

        when(mockSession3Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(erroneousStatusResponse);
        when(mockSession4Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession3Finished.isEmpty()).thenReturn(true);
        when(mockSession3Finished.isDataSendingAllowed()).thenReturn(true);
        when(mockSession4Finished.isDataSendingAllowed()).thenReturn(true);

        //when calling execute
        target.execute(mockContext);

        verify(mockSession3Finished, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession4Finished, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession3Finished, times(1)).clearCapturedData();
        verify(mockSession4Finished, times(1)).clearCapturedData();

        verify(mockContext, times(1)).getAllFinishedAndConfiguredSessions();
        verify(mockContext, times(1)).removeSession(mockSession3Finished);
        verify(mockContext, times(1)).removeSession(mockSession4Finished);
    }

    @Test
    public void sendingFinishedSessionsIsAbortedImmediatelyWhenTooManyRequestsResponseIsReceived() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        StatusResponse statusResponse = mock(StatusResponse.class);
        when(statusResponse.getResponseCode()).thenReturn(Response.HTTP_TOO_MANY_REQUESTS);
        when(statusResponse.isErroneousResponse()).thenReturn(true);
        when(statusResponse.getRetryAfterInMilliseconds()).thenReturn(12345L);

        when(mockSession3Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession4Finished.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession3Finished.isDataSendingAllowed()).thenReturn(true);
        when(mockSession4Finished.isDataSendingAllowed()).thenReturn(true);

        //when calling execute
        target.execute(mockContext);
        verify(mockSession3Finished, times(1)).isDataSendingAllowed();
        verify(mockSession3Finished, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verifyNoMoreInteractions(mockSession3Finished);

        // verify no interaction with second finished session
        verifyZeroInteractions(mockSession4Finished);

        // verify no interactions with open sessions
        verifyZeroInteractions(mockSession1Open, mockSession2Open);

        verify(mockContext, times(1)).getAllFinishedAndConfiguredSessions();
        verify(mockContext, times(0)).removeSession(any(SessionWrapper.class));

        // ensure also transition to CaptureOffState
        ArgumentCaptor<BeaconSendingCaptureOffState> argumentCaptor = ArgumentCaptor.forClass(BeaconSendingCaptureOffState.class);
        verify(mockContext, times(1)).setNextState(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().size(), is(equalTo(1)));
        assertThat(argumentCaptor.getAllValues().get(0).sleepTimeInMilliseconds, is(equalTo(12345L)));
    }

    @Test
    public void aBeaconSendingCaptureOnStateSendsOpenSessionsIfNotExpired() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();
        when(mockSession1Open.isDataSendingAllowed()).thenReturn(true);
        when(mockSession2Open.isDataSendingAllowed()).thenReturn(true);

        //when calling execute
        target.execute(mockContext);

        verify(mockSession1Open, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession2Open, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockContext, times(1)).setLastOpenSessionBeaconSendTime(org.mockito.Matchers.anyLong());
    }

    @Test
    public void aBeaconSendingCaptureOnStateClearsOpenSessionDataIfSendingIsNotAllowed() {
        //given
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();
        when(mockSession1Open.isDataSendingAllowed()).thenReturn(false);
        when(mockSession2Open.isDataSendingAllowed()).thenReturn(false);

        //when calling execute
        target.execute(mockContext);

        verify(mockSession1Open, times(0)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession2Open, times(0)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession1Open, times(1)).clearCapturedData();
        verify(mockSession2Open, times(1)).clearCapturedData();
        verify(mockContext, times(1)).setLastOpenSessionBeaconSendTime(org.mockito.Matchers.anyLong());
    }

    @Test
    public void sendingOpenSessionsIsAbortedImmediatelyWhenTooManyRequestsResponseIsReceived() {
        //given
        StatusResponse statusResponse = mock(StatusResponse.class);
        when(statusResponse.getResponseCode()).thenReturn(Response.HTTP_TOO_MANY_REQUESTS);
        when(statusResponse.isErroneousResponse()).thenReturn(true);
        when(statusResponse.getRetryAfterInMilliseconds()).thenReturn(12345L);

        when(mockSession1Open.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession2Open.sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class))).thenReturn(statusResponse);
        when(mockSession1Open.isDataSendingAllowed()).thenReturn(true);
        when(mockSession2Open.isDataSendingAllowed()).thenReturn(true);

        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        //when calling execute
        target.execute(mockContext);

        verify(mockSession1Open, times(1)).sendBeacon(org.mockito.Matchers.any(HTTPClientProvider.class));
        verify(mockSession1Open, times(1)).isDataSendingAllowed();
        verifyNoMoreInteractions(mockSession1Open);

        // ensure that second session was not invoked at all
        verifyZeroInteractions(mockSession2Open);

        // ensure also transition to CaptureOffState
        ArgumentCaptor<BeaconSendingCaptureOffState> argumentCaptor = ArgumentCaptor.forClass(BeaconSendingCaptureOffState.class);
        verify(mockContext, times(1)).setNextState(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues().size(), is(equalTo(1)));
        assertThat(argumentCaptor.getAllValues().get(0).sleepTimeInMilliseconds, is(equalTo(12345L)));
    }

    @Test
    public void aBeaconSendingCaptureOnStateTransitionsToCaptureOffStateWhenCapturingGotDisabled() {
        //given
        when(mockContext.isCaptureOn()).thenReturn(false);
        BeaconSendingCaptureOnState target = new BeaconSendingCaptureOnState();

        // when calling execute
        target.execute(mockContext);

        // then verify that capturing is set to disabled
        verify(mockContext, times(1)).handleStatusResponse(org.mockito.Matchers.any(StatusResponse.class));
        verify(mockContext, times(1)).isCaptureOn();

        verify(mockContext, times(1)).setNextState(org.mockito.Matchers.any(BeaconSendingCaptureOffState.class));
    }
}
